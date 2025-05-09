package org.apache.zeppelin.interpreter.remote;

/**
 * Exception thrown if a remote function call has failed
 */
public class RemoteFunctionCallFailedException extends RuntimeException {
   /**
    * {@inheritDoc}
    */
   public RemoteFunctionCallFailedException(String message) {
      super(message);
   }

   /**
    * {@inheritDoc}
    */
   public RemoteFunctionCallFailedException(String message, Throwable cause) {
      super(message, cause);
   }
}
