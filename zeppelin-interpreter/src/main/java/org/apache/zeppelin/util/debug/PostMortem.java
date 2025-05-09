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

import java.util.*;

/**
 * Helper to follow any task (sync or async) and gather all the troubles it wishes to mention
 */
public class PostMortem {
   /** Singleton instance */
   private static PostMortem singleton;

   /** List of error interpretations */
   private final List<ErrorInterpretation> errorInterpretations =  new ArrayList<>();

   /**
    * Singleton constructor
    */
   private PostMortem() {
   }

   /**
    * Get Instance
    * @return PostMortem Instance.
    */
   public static PostMortem getInstance() {
      if (singleton == null) {
         singleton = new PostMortem();
      }

      return singleton;
   }

   /**
    * Tells if no error interpretations are currently present
    * @return <code>true</code> if the list is empty.
    */
   public static synchronized boolean isEmpty() {
      return getInstance().errorInterpretations.isEmpty();
   }

   /**
    * Get the current error description being edited.
    * @return Current error.
    */
   public static synchronized CurrentError currentError() {
      return getInstance().getCurrentError();
   }

   /**
    * Create a new error description and return it.
    * @return new error.
    */
   public static synchronized NewError newError() {
      return getInstance().createNewError();
   }

   /**
    * Return the current error, allowing a complete edition of it.
    * @return current error, but returned with a new error interface.
    */
   public static synchronized NewError editError() {
      return getInstance().getCurrentError();
   }

   /**
    * Get the current error interpretation. One is created if none exists.
    * @return Error interpretation.
    */
   private synchronized ErrorInterpretation getCurrentError() {
      if (this.errorInterpretations.isEmpty()) {
         newError();
      }

      return this.errorInterpretations.get(this.errorInterpretations.size()-1);
   }

   /**
    * Get the very first error interpretation of this list. One is created if none exists.
    * @return Error interpretation.
    */
   private synchronized ErrorInterpretation firstError() {
      if (this.errorInterpretations.isEmpty()) {
         newError();
      }

      return this.errorInterpretations.get(0);
   }

   /**
    * Create a new error interpretation and place it in the circular array.
    */
   private synchronized NewError createNewError() {
      ErrorInterpretation error = new ErrorInterpretation();
      this.errorInterpretations.add(error);
      return error;
   }
}
