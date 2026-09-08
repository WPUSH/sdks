package cn.wpush.sdk;

/** Client-side validation failure before the request is sent. */
public class ValidationException extends WPushException {
  public ValidationException(String message) {
    super(message, null, null, null);
  }
}
