package org.apache.zeppelin.interpreter.remote;

/**
 * Exception thrown when the Apache Pool face a trouble
 */
public class ApachePoolException extends RuntimeException {
   /**
    * {@inheritDoc}
    */
   public ApachePoolException(String message) {
      super(message);
   }

   /**
    * {@inheritDoc}
    */
   public ApachePoolException(String message, Throwable cause) {
      super(message, cause);
   }
}
