package cn.wpush.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** JDK 11+ HttpClient transport. */
public final class JdkHttpTransport implements HttpTransport {
  private final HttpClient client;
  private final Duration timeout;

  public JdkHttpTransport(Duration timeout) {
    this.timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
  }

  @Override
  public Response post(String url, Map<String, String> headers, String jsonBody) throws IOException {
    HttpRequest.Builder b = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(timeout)
        .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
    if (headers != null) {
      for (Map.Entry<String, String> e : headers.entrySet()) {
        b.header(e.getKey(), e.getValue());
      }
    }
    try {
      HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
      return new Response(resp.statusCode(), resp.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("request interrupted", e);
    }
  }
}
