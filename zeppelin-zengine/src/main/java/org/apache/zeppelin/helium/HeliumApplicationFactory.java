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
package org.apache.zeppelin.helium;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import jakarta.inject.Inject;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.ManagedInterpreterGroup;
import org.apache.zeppelin.interpreter.remote.RemoteAngularObjectRegistry;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterProcess;
import org.apache.zeppelin.interpreter.thrift.RemoteApplicationResult;
import org.apache.zeppelin.notebook.ApplicationState;
import org.apache.zeppelin.notebook.Note;
import org.apache.zeppelin.notebook.NoteEventListener;
import org.apache.zeppelin.notebook.Notebook;
import org.apache.zeppelin.notebook.Paragraph;
import org.apache.zeppelin.scheduler.ExecutorFactory;
import org.apache.zeppelin.scheduler.Job;
import org.apache.zeppelin.user.AuthenticationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HeliumApplicationFactory
 */
public class HeliumApplicationFactory implements ApplicationEventListener, NoteEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(HeliumApplicationFactory.class);
  private final ExecutorService executor;
  private Notebook notebook;
  private ApplicationEventListener applicationEventListener;

  @Inject
  public HeliumApplicationFactory(
      Notebook notebook, ApplicationEventListener applicationEventListener) {
    this.executor =
        ExecutorFactory.singleton().createOrGet(HeliumApplicationFactory.class.getName(), 10);
    this.notebook = notebook;
    this.applicationEventListener = applicationEventListener;

    // TODO(jl): Hmmmmmmmm...
    this.notebook.addNotebookEventListener(this);
  }

  private boolean isRemote(InterpreterGroup group) {
    return group.getAngularObjectRegistry() instanceof RemoteAngularObjectRegistry;
  }


  /**
   * Load pkg and run task
   */
  public String loadAndRun(HeliumPackage pkg, Paragraph paragraph) {
    ApplicationState appState = paragraph.createOrGetApplicationState(pkg);
    onLoad(paragraph.getNote().getId(), paragraph.getId(), appState.getId(),
        appState.getHeliumPackage());
    executor.submit(new LoadApplication(appState, pkg, paragraph));
    return appState.getId();
  }

  /**
   * Load application and run in the remote process
   */
  private class LoadApplication implements Runnable {
    private final HeliumPackage pkg;
    private final Paragraph paragraph;
    private final ApplicationState appState;

    public LoadApplication(ApplicationState appState, HeliumPackage pkg, Paragraph paragraph) {
      this.appState = appState;
      this.pkg = pkg;
      this.paragraph = paragraph;
    }

    @Override
    public void run() {
      try {
        // get interpreter process
        Interpreter intp = paragraph.getBindedInterpreter();
        ManagedInterpreterGroup intpGroup = (ManagedInterpreterGroup) intp.getInterpreterGroup();
        RemoteInterpreterProcess intpProcess = intpGroup.getRemoteInterpreterProcess();
        if (intpProcess == null) {
          throw new ApplicationException("Target interpreter process is not running");
        }

        // load application
        load(intpProcess, appState);

        // run application
        RunApplication runTask = new RunApplication(paragraph, appState.getId());
        runTask.run();
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);

        if (appState != null) {
          appStatusChange(paragraph, appState.getId(), ApplicationState.Status.ERROR);
          appState.setOutput(e.getMessage());
        }
      }
    }

    private void load(RemoteInterpreterProcess intpProcess, ApplicationState appState)
        throws Exception {

      synchronized (appState) {
        if (appState.getStatus() == ApplicationState.Status.LOADED) {
          // already loaded
          return;
        }

        appStatusChange(paragraph, appState.getId(), ApplicationState.Status.LOADING);
        final String pkgInfo = pkg.toJson();
        final String appId = appState.getId();

        RemoteApplicationResult ret = intpProcess.callRemoteFunction(client ->
                client.loadApplication(
                        appId,
                        pkgInfo,
                        paragraph.getNote().getId(),
                        paragraph.getId()));
        if (ret.isSuccess()) {
          appStatusChange(paragraph, appState.getId(), ApplicationState.Status.LOADED);
        } else {
          throw new ApplicationException(ret.getMsg());
        }
      }
    }
  }

  /**
   * Get ApplicationState
   * @param paragraph
   * @param appId
   * @return
   */
  public ApplicationState get(Paragraph paragraph, String appId) {
    return paragraph.getApplicationState(appId);
  }

  /**
   * Unload application
   * It does not remove ApplicationState
   *
   * @param paragraph
   * @param appId
   */
  public void unload(Paragraph paragraph, String appId) {
    executor.execute(new UnloadApplication(paragraph, appId));
  }

  /**
   * Unload application task
   */
  private class UnloadApplication implements Runnable {
    private final Paragraph paragraph;
    private final String appId;

    public UnloadApplication(Paragraph paragraph, String appId) {
      this.paragraph = paragraph;
      this.appId = appId;
    }

    @Override
    public void run() {
      ApplicationState appState = null;
      try {
        appState = paragraph.getApplicationState(appId);

        if (appState == null) {
          LOGGER.warn("Can not find {} to unload from {}", appId, paragraph.getId());
          return;
        }
        if (appState.getStatus() == ApplicationState.Status.UNLOADED) {
          // not loaded
          return;
        }
        unload(appState);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        if (appState != null) {
          appStatusChange(paragraph, appId, ApplicationState.Status.ERROR);
          appState.setOutput(e.getMessage());
        }
      }
    }

    private void unload(final ApplicationState appsToUnload) throws ApplicationException {
      synchronized (appsToUnload) {
        if (appsToUnload.getStatus() != ApplicationState.Status.LOADED) {
          throw new ApplicationException(
              "Can't unload application status " + appsToUnload.getStatus());
        }
        appStatusChange(paragraph, appsToUnload.getId(), ApplicationState.Status.UNLOADING);
        Interpreter intp = null;
        try {
          intp = paragraph.getBindedInterpreter();
        } catch (InterpreterException e) {
          throw new ApplicationException("No interpreter found", e);
        }

        RemoteInterpreterProcess intpProcess =
            ((ManagedInterpreterGroup) intp.getInterpreterGroup()).getRemoteInterpreterProcess();
        if (intpProcess == null) {
          throw new ApplicationException("Target interpreter process is not running");
        }

        RemoteApplicationResult ret = intpProcess.callRemoteFunction(client ->
                client.unloadApplication(appsToUnload.getId()));
        if (ret.isSuccess()) {
          appStatusChange(paragraph, appsToUnload.getId(), ApplicationState.Status.UNLOADED);
        } else {
          throw new ApplicationException(ret.getMsg());
        }
      }
    }
  }

  /**
   * Run application
   * It does not remove ApplicationState
   *
   * @param paragraph
   * @param appId
   */
  public void run(Paragraph paragraph, String appId) {
    executor.execute(new RunApplication(paragraph, appId));
  }

  /**
   * Run application task
   */
  private class RunApplication implements Runnable {
    private final Paragraph paragraph;
    private final String appId;

    public RunApplication(Paragraph paragraph, String appId) {
      this.paragraph = paragraph;
      this.appId = appId;
    }

    @Override
    public void run() {
      ApplicationState appState = null;
      try {
        appState = paragraph.getApplicationState(appId);

        if (appState == null) {
          LOGGER.warn("Can not find {} to unload from {}", appId, paragraph.getId());
          return;
        }

        run(appState);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        if (appState != null) {
          appStatusChange(paragraph, appId, ApplicationState.Status.UNLOADED);
          appState.setOutput(e.getMessage());
        }
      }
    }

    private void run(final ApplicationState app) throws ApplicationException {
      synchronized (app) {
        if (app.getStatus() != ApplicationState.Status.LOADED) {
          throw new ApplicationException(
              "Can't run application status " + app.getStatus());
        }

        Interpreter intp = null;
        try {
          intp = paragraph.getBindedInterpreter();
        } catch (InterpreterException e) {
          throw new ApplicationException("No interpreter found", e);
        }

        RemoteInterpreterProcess intpProcess =
            ((ManagedInterpreterGroup) intp.getInterpreterGroup()).getRemoteInterpreterProcess();
        if (intpProcess == null) {
          throw new ApplicationException("Target interpreter process is not running");
        }
        RemoteApplicationResult ret = intpProcess.callRemoteFunction(client ->
                client.runApplication(app.getId()));
        if (ret.isSuccess()) {
          // success
        } else {
          throw new ApplicationException(ret.getMsg());
        }
      }
    }
  }

  @Override
  public void onOutputAppend(
      String noteId, String paragraphId, int index, String appId, String output) {
    ApplicationState appToUpdate = getAppState(noteId, paragraphId, appId);

    if (appToUpdate != null) {
      appToUpdate.appendOutput(output);
    } else {
      LOGGER.error("Can't find app {}", appId);
    }

    if (applicationEventListener != null) {
      applicationEventListener.onOutputAppend(noteId, paragraphId, index, appId, output);
    }
  }

  @Override
  public void onOutputUpdated(
      String noteId, String paragraphId, int index, String appId,
      InterpreterResult.Type type, String output) {
    ApplicationState appToUpdate = getAppState(noteId, paragraphId, appId);

    if (appToUpdate != null) {
      appToUpdate.setOutput(output);
    } else {
      LOGGER.error("Can't find app {}", appId);
    }

    if (applicationEventListener != null) {
      applicationEventListener.onOutputUpdated(noteId, paragraphId, index, appId, type, output);
    }
  }

  @Override
  public void onLoad(String noteId, String paragraphId, String appId, HeliumPackage pkg) {
    if (applicationEventListener != null) {
      applicationEventListener.onLoad(noteId, paragraphId, appId, pkg);
    }
  }

  @Override
  public void onStatusChange(String noteId, String paragraphId, String appId, String status) {
    ApplicationState appToUpdate = getAppState(noteId, paragraphId, appId);
    if (appToUpdate != null) {
      appToUpdate.setStatus(ApplicationState.Status.valueOf(status));
    }

    if (applicationEventListener != null) {
      applicationEventListener.onStatusChange(noteId, paragraphId, appId, status);
    }
  }

  private void appStatusChange(Paragraph paragraph,
                               String appId,
                               ApplicationState.Status status) {
    ApplicationState app = paragraph.getApplicationState(appId);
    app.setStatus(status);
    onStatusChange(paragraph.getNote().getId(), paragraph.getId(), appId, status.toString());
  }

  private ApplicationState getAppState(String noteId, String paragraphId, String appId) {
    if (notebook == null) {
      return null;
    }
    try {
      return notebook.processNote(noteId,
        note -> {
          if (note == null) {
            LOGGER.warn("Note {} not found", noteId);
            return null;
          }
          Paragraph paragraph = note.getParagraph(paragraphId);
          if (paragraph == null) {
            LOGGER.error("Can't get paragraph {}", paragraphId);
            return null;
          }
          return paragraph.getApplicationState(appId);
        });
    } catch (IOException e) {
      LOGGER.error("Can't get note {}", noteId);
      return null;
    }
  }

  @Override
  public void onNoteRemove(Note note, AuthenticationInfo subject) {
    // do nothing
  }

  @Override
  public void onNoteCreate(Note note, AuthenticationInfo subject) {
    // do nothing
  }

  @Override
  public void onNoteUpdate(Note note, AuthenticationInfo subject) {
    // do nothing
  }

  @Override
  public void onParagraphRemove(Paragraph paragraph) {
    List<ApplicationState> appStates = paragraph.getAllApplicationStates();
    for (ApplicationState app : appStates) {
      UnloadApplication unloadJob = new UnloadApplication(paragraph, app.getId());
      unloadJob.run();
    }
  }

  @Override
  public void onParagraphCreate(Paragraph p) {
    // do nothing
  }

  @Override
  public void onParagraphUpdate(Paragraph p) {
    // do nothing
  }

  @Override
  public void onParagraphStatusChange(Paragraph p, Job.Status status) {
    if (status == Job.Status.FINISHED) {
      // refresh application
      List<ApplicationState> appStates = p.getAllApplicationStates();

      for (ApplicationState app : appStates) {
        loadAndRun(app.getHeliumPackage(), p);
      }
    }
  }
}
