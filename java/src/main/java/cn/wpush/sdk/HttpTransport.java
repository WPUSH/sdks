package cn.wpush.sdk;

import java.io.IOException;
import java.util.Map;

/** Injectable HTTP transport for testability. */
public interface HttpTransport {
  /** POST JSON and return status + body text. */
  Response post(String url, Map<String, String> headers, String jsonBody) throws IOException;

  final class Response {
    public final int status;
    public final String body;

    public Response(int status, String body) {
      this.status = status;
      this.body = body;
    }
  }
}
