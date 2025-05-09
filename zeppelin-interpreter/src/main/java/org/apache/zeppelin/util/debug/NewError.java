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
 * Accessible members of an error description when a new error interpretation is created
 */
public interface NewError extends CurrentError, Serializable {
   /**
    * Set the exact exit code returned (if of numeric kind)
    * @param originalExitCode Exit code or <code>null</code> if still running or not launched.
    * @return This new error
    */
   NewError originalExitCode(Integer originalExitCode);

   /**
    * Set the interpreted result code returned.
    * @param interpretedExitCode Exit code or <code>null</code> if still running or not launched.
    * @return This new error
    */
   NewError interpretedExitCode(Integer interpretedExitCode);

   /**
    * Set the error code, if component returns an error trough this way
    * @param errorCode Error code
    * @return This new error
    */
   NewError errorCode(String errorCode);

   /**
    * Set an SQL status code
    * @param sqlState SQL State
    * @return This new error
    */
   NewError sqlState(String sqlState);

   /**
    * Set an error message
    * @param errorMessage Error message
    * @return This new error
    */
   NewError errorMessage(String errorMessage);

   /**
    * Set the stack trace content
    * @param stackTraceContent Stack trace content
    * @return This new error
    */
   NewError stackTraceContent(String stackTraceContent);

   /**
    * Set the error message the output stream gathers
    * @param outputStreamMessage Output stream error message
    * @return This new error
    */
   NewError outputStreamErrorMessage(String outputStreamMessage);

   /**
    * Set the name of the exception class that has been thrown, if one was
    * @param exceptionClassName Name of the exception class
    * @return This new error
    */
   NewError exceptionClassName(String exceptionClassName);

   /**
    * Set the name of the exception class that has been thrown, if one was
    * @param exceptionClass The class of the exception that has been thrown (only its name will be taken)
    * @return This new error
    */
   NewError exceptionClassName(Class<? extends Throwable> exceptionClass);

   /**
    * Set the name of the exception class that has been thrown, if one was.
    * Take also the name of its direct cause exception
    * @param exception The exception that has been thrown (its name only will be taken)
    * @return This new error
    */
   NewError exceptionClassName(Throwable exception);

   /**
    * Refresh entry with current time
    * @return This new error
    */
   NewError now();

   /**
    * {@inheritDoc}
    */
   String toString();
}
