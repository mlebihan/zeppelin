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
package org.apache.zeppelin.util.debug;

import java.io.Serializable;
import java.text.MessageFormat;

/**
 * Carries most error information it can in case of failure or timeout
 */
public class ErrorInterpretation implements NewError, Serializable {
   /** Milliseconds elapsed since 01-01-1970 */
   private long milliseconds;

   /* Time elapsed in nanoseconds */
   private long nanoSeconds;

   /** The exit code returned by the underlying process */
   private Integer originalExitCode;

   /** Interpreted exit code */
   private Integer interpretedExitCode;

   /** Error code or state, if one exists. For processes willing to reply with <code>E0021</code> */
   private String errorCode;

   /** SQL State */
   private String sqlState;

   /** Error message */
   private String errorMessage;

   /** Error message cause (from exception.getCause(), if any) */
   private String errorCauseMessage;

   /** Stack trace content */
   private String stackTraceContent;

   /** Error message coming from output stream */
   private String outputStreamMessage;

   /** Exception class name */
   private String exceptionClassName;

   /** Exception cause class name (from exception.getCause(), if any) */
   private String exceptionCauseClassName;

   /** Free diagnostics any member of the call stack who examine this error can complete with its own thoughts with {@link #addDiagnostic(String)} */
   private final StringBuilder diagnostics = new StringBuilder();

   /**
    * Construct an error interpretation.
    */
   public ErrorInterpretation() {
      this.milliseconds = System.currentTimeMillis();
      this.nanoSeconds = System.nanoTime();
   }

   @Override
   public Integer originalExitCode() {
      return this.originalExitCode;
   }

   @Override
   public NewError originalExitCode(Integer originalExitCode) {
      this.originalExitCode = originalExitCode;
      return this;
   }

   @Override
   public Integer interpretedExitCode() {
      if (this.interpretedExitCode == null) {
         return originalExitCode();
      }

      return this.interpretedExitCode;
   }

   @Override
   public NewError interpretedExitCode(Integer interpretedExitCode) {
      this.interpretedExitCode = interpretedExitCode;
      return this;
   }

   @Override
   public String errorCode() {
      return this.errorCode;
   }

   @Override
   public NewError errorCode(String errorCode) {
      this.errorCode = errorCode;
      return this;
   }

   @Override
   public String sqlState() {
      return this.sqlState;
   }

   @Override
   public NewError sqlState(String sqlState) {
      this.sqlState = sqlState;
      return this;
   }

   @Override
   public String errorMessage() {
      return this.errorMessage;
   }

   @Override
   public NewError errorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
   }

   @Override
   public String errorCauseMessage() {
      return this.errorCauseMessage;
   }

   @Override
   public String stackTraceContent() {
      return stackTraceContent;
   }

   @Override
   public NewError stackTraceContent(String stackTraceContent) {
      this.stackTraceContent = stackTraceContent;
      return this;
   }

   @Override
   public String outputStreamErrorMessage() {
      return this.outputStreamMessage;
   }

   @Override
   public NewError outputStreamErrorMessage(String outputStreamMessage) {
      this.outputStreamMessage = outputStreamMessage;
      return this;
   }

   @Override
   public String exceptionClassName() {
      return this.exceptionClassName;
   }

   @Override
   public NewError exceptionClassName(String exceptionClassName) {
      this.exceptionClassName = exceptionClassName;
      return this;
   }

   @Override
   public NewError exceptionClassName(Class<? extends Throwable> exceptionClass) {
      return exceptionClassName(exceptionClass != null ? exceptionClass.getName() : "");
   }

   @Override
   public NewError exceptionClassName(Throwable exception) {
      exceptionClassName(exception.getClass());

      if (exception.getCause() != null) {
         this.errorCauseMessage = exception.getCause().getMessage();
         this.exceptionCauseClassName = exception.getCause().getClass().getName();
      }

      return this;
   }

   @Override
   public String exceptionCauseClassName() {
      return this.exceptionCauseClassName;
   }

   @Override
   public long milliseconds() {
      return this.milliseconds;
   }

   @Override
   public long nanoSeconds() {
      return this.nanoSeconds;
   }

   @Override
   public NewError now() {
      this.milliseconds = System.currentTimeMillis();
      this.nanoSeconds = System.nanoTime();
      return this;
   }

   @Override
   public CurrentError addDiagnostic(String message) {
      if (!this.diagnostics.isEmpty()) {
         this.diagnostics.append("\n");
      }

      this.diagnostics.append(message);
      return this;
   }

   @Override
   public CurrentError addDiagnostic(Class<?> me, String message) {
      if (me != null) {
         this.diagnostics.append(me.getSimpleName());
         this.diagnostics.append(": ");
      }

      return addDiagnostic(message);
   }

   @Override
   public boolean hasDiagnostics() {
      return !this.diagnostics.isEmpty();
   }

   @Override
   public CurrentError markThisDiagnosticStepFailed(Class<?> me, Exception e) {
      String diagnostic;

      if (e == null) {
         diagnostic = "Failed to diagnose. Unknown reason.";
      }
      else {
         diagnostic = String.format("Failed to diagnose. %s", e.getMessage());
      }

      return addDiagnostic(me, diagnostic);
   }

   @Override
   public String diagnostics() {
      return diagnostics.toString();
   }

   @Override
   public boolean isEmpty() {
      return this.originalExitCode == null && this.interpretedExitCode == null && (this.errorCode == null || this.errorCode.isEmpty()) && (this.sqlState == null || this.sqlState.isEmpty())
         && (this.errorMessage == null || this.errorMessage.isEmpty())
         && (this.stackTraceContent == null || this.stackTraceContent.isEmpty())
         && (this.exceptionClassName == null || this.exceptionClassName.isEmpty())
         && this.diagnostics.isEmpty();
   }

   @Override
   public String toString() {
      String format = """
         At {0,number,#} ms, {1,number,#} ns:
         Exit/Error (Original: {2,number,#0}, Interpreted: {3,number,#0}, Code: {4}, SQL: {5}):
         Error message: {6}
         thrown by exception of class: {7}
         Error message (cause): {8}
         caused by exception of class: {9}
         OutputStream error message (log): {10}
         Stack trace: {11}
         Diagnostics: {12}""";

      return new MessageFormat(format).format(new Object[] {
         this.milliseconds, this.nanoSeconds,
         this.originalExitCode, this.interpretedExitCode, this.errorCode, this.sqlState,
         this.errorMessage,
         this.exceptionClassName,
         this.errorCauseMessage,
         this.exceptionCauseClassName,
         this.outputStreamMessage,
         this.stackTraceContent,
         hasDiagnostics() ? diagnostics() : "None"});
   }
}
