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

/**
 * Accessible members of an error description when an error interpretation is completed
 */
public interface CurrentError extends Serializable {
   /**
    * Get the exact exit code returned (if of numeric kind)
    * @return exit code or <code>null</code> if still running or not launched.
    */
   Integer originalExitCode();

   /**
    * Get the interpreted result code returned.
    * @return exit code or <code>null</code> if still running or not launched.
    */
   Integer interpretedExitCode();

   /**
    * Get the error code if component returns an error trough this way
    * @return Error code
    */
   String errorCode();

   /**
    * Get an SQL status code, if any.
    * @return SQL State
    */
   String sqlState();

   /**
    * Get an error message
    * @return Error message
    */
   String errorMessage();

   /**
    * Get an error message cause, coming from the {@link Exception#getCause()}
    * @return Error cause message of the main error message
    */
   String errorCauseMessage();

   /**
    * Get the stack trace content
    * @return Stack trace content
    */
   String stackTraceContent();

   /**
    * Get the error message the output stream can gather
    * @return Output stream error message
    */
   String outputStreamErrorMessage();

   /**
    * Get the name of the exception class that has been thrown if one was
    * @return Name of the exception class
    */
   String exceptionClassName();

   /**
    * Get the name of the exception cause class related to the exception that has been thrown if this exception has one
    * @return Name of the exception cause class
    */
   String exceptionCauseClassName();

   /**
    * Return time elapsed in milliseconds since 01-01-1970
    * @return time in milliseconds
    */
   long milliseconds();

   /**
    * Return time elapsed in nanoseconds
    * @return time in milliseconds
    */
   long nanoSeconds();

   /**
    * Add a diagnostic to the current error
    * @param message Message
    * @return This current error being completed
    */
   CurrentError addDiagnostic(String message);

   /**
    * Add a diagnostic to the current error
    * @param me the class that is adding to this diagnostic
    * @param message Message
    * @return This current error being completed
    */
   CurrentError addDiagnostic(Class<?> me, String message);

   /**
    * Tells if the current error information has some diagnostic with it
    * @return <code>true</code> if it has one or more
    */
   boolean hasDiagnostics();

   /**
    * Mark the current attempt to add a diagnostic failed.
    * @param me the class that was willing to add a diagnostic
    * @param e  Exception received
    * @return This current error being completed
    */
   CurrentError markThisDiagnosticStepFailed(Class<?> me, Exception e);

   /**
    * Return the diagnostics any component did when it examined this error
    * @return Diagnostics
    */
   String diagnostics();

   /**
    * Detects if this error information is empty (carries no information at all)
    * @return <code>true</code> if each of its members are empty or <code>null</code>
    */
   boolean isEmpty();
}
