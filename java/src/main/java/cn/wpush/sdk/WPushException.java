package cn.wpush.sdk;

/** API or transport error from WPUSH. */
public class WPushException extends RuntimeException {
  private final Integer code;
  private final Integer httpStatus;
  private final Object data;

  public WPushException(String message) {
    this(message, null, null, null);
  }

  public WPushException(String message, Integer code, Integer httpStatus, Object data) {
    super(message);
    this.code = code;
    this.httpStatus = httpStatus;
    this.data = data;
  }

  public Integer getCode() {
    return code;
  }

  public Integer getHttpStatus() {
    return httpStatus;
  }

  public Object getData() {
    return data;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(getMessage() == null ? "" : getMessage());
    if (code != null) {
      sb.append(" | code=").append(code);
    }
    if (httpStatus != null) {
      sb.append(" | http=").append(httpStatus);
    }
    return sb.toString();
  }
}
