/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.interpreter.util;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.util.debug.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Abstract class for launching a Java process.
 */
public abstract class ProcessLauncher implements ExecuteResultHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessLauncher.class);

  /** Process launching state */
  public enum State {
    NEW,
    LAUNCHED,
    RUNNING,

    /** Process terminated on a non-zero exit value */
    TERMINATED,

    /** Process expected successfully completed */
    COMPLETED
  }

  /** Command line to run */
  private final CommandLine commandLine;

  /** Environment properties */
  private final Map<String, String> envs;

  /** Apache exec watchdog */
  private ExecuteWatchdog watchdog;

  /** Process output stream (log) */
  private final ProcessLogOutputStream processOutput;

  /** Error message gathered if process launching has failed */
  protected String errorMessage = null;

  /** Current state of the process launch */
  protected volatile State state = State.NEW;

  /** <code>true</code> if process launching ended in timeout */
  private boolean launchTimeout = false;

  /** Bash/Linux status for time out */
  public static final int ERROR_STATUS_TIMEOUT = 124;

  /** Bash/Linux status for "command not found" */
  public static final int ERROR_STATUS_COMMAND_NOT_FOUND = 127;

  /** Apache Exec magic status, set to distinguish the case where nothing could be run. Mostly because directory or executable could not be found */
  public static final int APACHE_EXEC_MAGIC_STATUS = -559038737;

  /**
   * Construct a process launcher for a command line and an environment
   * @param commandLine Command line
   * @param envs Environnement
   */
  protected ProcessLauncher(CommandLine commandLine,
                         Map<String, String> envs) {
    this.commandLine = commandLine;
    this.envs = envs;
    this.processOutput = new ProcessLogOutputStream();
  }

  /**
   * Construct a process launcher for a command line, an environment and a given log output stream
   * @param commandLine Command line
   * @param envs Environnement
   */
  protected ProcessLauncher(CommandLine commandLine,
                         Map<String, String> envs,
                         ProcessLogOutputStream processLogOutput) {
    this.commandLine = commandLine;
    this.envs = envs;
    this.processOutput = processLogOutput;
  }

  /**
   * In some cases, we need to redirect process output to paragraph's InterpreterOutput.
   * e.g. In %r.shiny for shiny app
   * @param redirectedContext Interpreter context
   */
  public void setRedirectedContext(InterpreterContext redirectedContext) {
    if (redirectedContext != null) {
      LOGGER.info("Start to redirect process output to interpreter output");
    } else {
      LOGGER.info("Stop to redirect process output to interpreter output");
    }
    this.processOutput.redirectedContext = redirectedContext;
  }

  /**
   * Launch the process
   */
  public void launch() {
    DefaultExecutor executor = new DefaultExecutor();
    executor.setStreamHandler(new PumpStreamHandler(processOutput));
    this.watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT);
    executor.setWatchdog(watchdog);

    // Execute command line asynchronously
    try {
      executor.execute(commandLine, envs, this);
      transition(State.LAUNCHED);
      LOGGER.info("Process is launched: {}", commandLine);
    } catch (IOException e) {
      this.processOutput.stopCatchLaunchOutput();
      LOGGER.error("Fail to launch process: {}", commandLine, e);
      transition(State.TERMINATED);
      errorMessage = e.getMessage();

      try {
        PostMortem.newError()
          .originalExitCode(APACHE_EXEC_MAGIC_STATUS) // According to the Apache exec code below, it has most chances to be a missing directory (Linux/bash Error code = 127)
          .interpretedExitCode(ERROR_STATUS_COMMAND_NOT_FOUND)
          .exceptionClassName(e)
          .outputStreamErrorMessage(this.processOutput.getProcessExecutionOutput())
          .errorMessage(errorMessage)
          .addDiagnostic(getClass(),
             // Get the working directory Apache exec was looking into, as we know it at this step
             String.format("Attempting to start command '%s' in '%s' directory, that wasn't found.",
                commandLine.toString(), executor.getWorkingDirectory().getAbsolutePath()));
      }
      catch(RuntimeException ex) {
        PostMortem.currentError().markThisDiagnosticStepFailed(getClass(), ex);
      }
    }
  }

  /**
   * Wait for that process to be ready
   * @param timeout Timeout amount
   */
  public abstract void waitForReady(int timeout);

  /**
   * Transition current processing launching to another state
   * @param state Next state
   */
  public void transition(State state) {
    this.state = state;
    LOGGER.info("Process state is transitioned to {}", state);
  }

  /**
   * React on a timeout
   */
  public void onTimeout() {
    LOGGER.warn("Process launch is time out.");
    launchTimeout = true;

    // Ensure the watchdog is stopped
    stop();

    // Complete error information
    try {
      PostMortem.editError().originalExitCode(APACHE_EXEC_MAGIC_STATUS)
        .interpretedExitCode(ERROR_STATUS_TIMEOUT)
        .addDiagnostic(getClass(),
           String.format("Command '%s' gone on timeout.", commandLine.toString()));
    }
    catch(RuntimeException ex) {
      PostMortem.currentError().markThisDiagnosticStepFailed(getClass(), ex);
    }
  }

  /**
   * Change process launcher state to <code>RUNNING</code>
   */
  public void onProcessRunning() {
    transition(State.RUNNING);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onProcessComplete(int exitValue) {
    LOGGER.warn("Process is exited with exit value {}", exitValue);

    if (exitValue == 0) {
      transition(State.COMPLETED);
    } else {
      transition(State.TERMINATED);
    }

    // Complete error information
    PostMortem.newError().originalExitCode(exitValue)
      .outputStreamErrorMessage(this.processOutput.getProcessExecutionOutput())
      .interpretedExitCode(exitValue);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void onProcessFailed(ExecuteException e) {
    LOGGER.warn("Process with cmd {} is failed due to", commandLine, e);
    LOGGER.warn("Process ErrorMessage: \n{}", getErrorMessage());
    errorMessage = ExceptionUtils.getStackTrace(e);

    transition(State.TERMINATED);

    // Complete error information
    int exitValue = e.getExitValue();
    PostMortem.newError().originalExitCode(exitValue)
      .exceptionClassName(e)
      .errorMessage(e.getMessage())
      .outputStreamErrorMessage(this.processOutput.getProcessExecutionOutput())
      .stackTraceContent(ExceptionUtils.getStackTrace(e));

    if (exitValue == APACHE_EXEC_MAGIC_STATUS) {
      // It means that asynchronous process hadn't the chance to get any exit from the process itself
      // This process has a chance to not have been found at all. Not being started nor startable.
      PostMortem.editError().interpretedExitCode(ERROR_STATUS_COMMAND_NOT_FOUND);
    }
  }

  /**
   * Get the error message, from the execution output if possible
   * @return Error message
   */
  public String getErrorMessage() {
    if (!StringUtils.isBlank(processOutput.getProcessExecutionOutput())) {
      return processOutput.getProcessExecutionOutput();
    } else {
      return this.errorMessage;
    }
  }

  /**
   * Get the process launch output log
   * @return Process launch output
   */
  public String getProcessLaunchOutput() {
    return this.processOutput.getProcessExecutionOutput();
  }

  /**
   * Detects if the process launch has ended in timeout
   * @return <code>true</code> if it's the case
   */
  public boolean isLaunchTimeout() {
    return launchTimeout;
  }

  /**
   * Detects if the process is still alive
   * @return <code>true</code> if it isn't <code>COMPLETED</code> or <code>TERMINATED</code> yet
   */
  public boolean isAlive() {
    return state != State.TERMINATED && state != State.COMPLETED;
  }

  /**
   * Detects if the process is still running
   * @return <code>true</code> if it has the <code>RUNNING</code> status
   */
  public boolean isRunning() {
    return this.state == State.RUNNING;
  }

  /**
   * Ensure cleaning of process launching
   */
  public void stop() {
    if (watchdog != null && isRunning()) {
      watchdog.destroyProcess();
      watchdog = null;
    }
  }

  /**
   * Ends the catching of output log
   */
  public void stopCatchLaunchOutput() {
    processOutput.stopCatchLaunchOutput();
  }

  /**
   * Catch the output stream (log) content.
   * It starts immediately.
   */
  public static class ProcessLogOutputStream extends LogOutputStream {
    /** <code>true</code> if we have to catch output */
    private boolean catchLaunchOutput = true;

    /** Contains what is gathered, separated by newlines */
    private final StringBuilder launchOutput = new StringBuilder();

    /** if not <code>null</code> stream content will also be redirected here */
    private InterpreterContext redirectedContext;

    /**
     * Ends the launch output catching
     */
    public void stopCatchLaunchOutput() {
      this.catchLaunchOutput = false;
    }

    /**
     * Returns the content of the output stream (log)
     * @return content of the output stream (log) as a String
     */
    public String getProcessExecutionOutput() {
      return launchOutput.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void processLine(String s, int i) {
      // print Interpreter launch command for diagnose purpose
      if (s.startsWith("[INFO]")) {
        LOGGER.info(s);
      } else {
        LOGGER.debug("Process Output: {}", s);
      }

      // Gather output if asked for
      if (catchLaunchOutput) {
        launchOutput.append(s);
        launchOutput.append("\n");
      }

      // And send it to the redirected context, if asked for
      if (redirectedContext != null) {
        try {
          redirectedContext.out.write(s + "\n");
        } catch (IOException e) {
          LOGGER.error("unable to write to redirectedContext", e);
        }
      }
    }
  }
}
