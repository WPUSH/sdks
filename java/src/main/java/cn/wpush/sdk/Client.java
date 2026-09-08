package cn.wpush.sdk;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** WPUSH open API client. */
public final class Client {
  public static final String USER_AGENT = "wpush-java/0.1.0";
  public static final String DEFAULT_BASE_URL = "https://api.wpush.cn";

  private static final Pattern PHONE_RE = Pattern.compile("^(\\+?86)?1\\d{10}$");
  private static final Pattern CODE_RE = Pattern.compile("^[A-Za-z0-9]{1,6}$");

  private final String apiKey;
  private final String baseUrl;
  private final Object defaultChannel;
  private final HttpTransport transport;

  public Client(String apiKey) {
    this(apiKey, null, Duration.ofSeconds(30), null, null);
  }

  public Client(String apiKey, String baseUrl, Duration timeout, Object defaultChannel, HttpTransport transport) {
    String key = apiKey;
    if (key == null || key.isEmpty()) {
      key = System.getenv("WPUSH_API_KEY");
    }
    if (key == null || key.isEmpty()) {
      throw new ValidationException("api_key is required (or set WPUSH_API_KEY)");
    }
    this.apiKey = key;
    String base = (baseUrl == null || baseUrl.isEmpty()) ? DEFAULT_BASE_URL : baseUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.baseUrl = base;
    this.defaultChannel = defaultChannel;
    this.transport = transport != null ? transport : new JdkHttpTransport(timeout == null ? Duration.ofSeconds(30) : timeout);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getApiKey() {
    return apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public String send(String title, String content, Object channel, String option, String topicCode, String url, String idempotencyKey)
      throws WPushException {
    if (title == null || title.isEmpty()) {
      throw new ValidationException("title is required");
    }
    if (option != null && !option.isEmpty() && topicCode != null && !topicCode.isEmpty()) {
      throw new ValidationException("option and topic_code cannot be used together");
    }
    Object chSrc = channel != null ? channel : defaultChannel;
    String ch = joinChannel(chSrc);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("apikey", apiKey);
    payload.put("title", title);
    if (content != null) payload.put("content", content);
    if (ch != null) payload.put("channel", ch);
    if (option != null && !option.isEmpty()) payload.put("option", option);
    if (topicCode != null && !topicCode.isEmpty()) payload.put("topic_code", topicCode);
    if (url != null && !url.isEmpty()) payload.put("url", url);
    Object data = post("/api/v1/send", payload, idempotencyKey);
    return stringifyId(data);
  }

  public String send(String title) throws WPushException {
    return send(title, null, null, null, null, null, null);
  }

  public String send(String title, String content, Object channel) throws WPushException {
    return send(title, content, channel, null, null, null, null);
  }

  public Map<String, Object> query(String messageId) throws WPushException {
    return query(messageId, null);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> query(String messageId, String idempotencyKey) throws WPushException {
    if (messageId == null || messageId.isEmpty()) {
      throw new ValidationException("message_id is required");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("apikey", apiKey);
    payload.put("id", String.valueOf(messageId));
    Object data = post("/api/v1/query", payload, idempotencyKey);
    if (!(data instanceof Map)) {
      throw new WPushException("invalid query data", null, null, data);
    }
    return (Map<String, Object>) data;
  }

  public String sendMail(String to, String title, String content, String idempotencyKey) throws WPushException {
    if (to == null || to.isEmpty()) throw new ValidationException("to is required");
    if (title == null || title.isEmpty()) throw new ValidationException("title is required");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("apikey", apiKey);
    payload.put("to", to);
    payload.put("title", title);
    if (content != null) payload.put("content", content);
    return stringifyId(post("/api/v1/send_mail", payload, idempotencyKey));
  }

  public String sendMail(String to, String title, String content) throws WPushException {
    return sendMail(to, title, content, null);
  }

  public String sendCode(String phone, String code, String idempotencyKey) throws WPushException {
    if (phone == null || !PHONE_RE.matcher(phone).matches()) {
      throw new ValidationException("invalid phone");
    }
    if (code == null || !CODE_RE.matcher(code).matches()) {
      throw new ValidationException("invalid code");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("apikey", apiKey);
    payload.put("phone", phone);
    payload.put("code", code);
    return stringifyId(post("/api/v1/send_code", payload, idempotencyKey));
  }

  public String sendCode(String phone, String code) throws WPushException {
    return sendCode(phone, code, null);
  }

  public Map<String, Object> queryRelay(String id) throws WPushException {
    return queryRelay(id, null);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> queryRelay(String id, String idempotencyKey) throws WPushException {
    if (id == null || id.isEmpty()) {
      throw new ValidationException("id is required");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("apikey", apiKey);
    payload.put("id", String.valueOf(id));
    Object data = post("/api/v1/query_relay", payload, idempotencyKey);
    if (!(data instanceof Map)) {
      throw new WPushException("invalid query data", null, null, data);
    }
    return (Map<String, Object>) data;
  }

  private Object post(String path, Map<String, Object> payload, String idempotencyKey) throws WPushException {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Accept", "application/json");
    headers.put("User-Agent", USER_AGENT);
    headers.put("X-API-Key", apiKey);
    if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
      headers.put("X-Idempotency-Key", idempotencyKey);
    }
    String body = Json.stringify(payload);
    HttpTransport.Response resp;
    try {
      resp = transport.post(baseUrl + path, headers, body);
    } catch (IOException e) {
      throw new WPushException(e.getMessage() == null ? "transport error" : e.getMessage(), null, null, null);
    }
    Map<String, Object> env;
    try {
      if (resp.body == null || resp.body.isEmpty()) {
        throw new IllegalArgumentException("empty");
      }
      env = Json.parseObject(resp.body);
    } catch (RuntimeException e) {
      throw new WPushException("invalid JSON response", null, resp.status, resp.body);
    }
    return parseEnvelope(env, resp.status);
  }

  private static Object parseEnvelope(Map<String, Object> body, int httpStatus) throws WPushException {
    Object code = body.get("code");
    Object message = body.get("message");
    Object data = body.get("data");
    if (!isSuccessCode(code)) {
      Integer errCode = (code instanceof Number && !(code instanceof Boolean)) ? ((Number) code).intValue() : null;
      // Boolean is not a Number subclass in Java, so above is fine; still reject bool explicitly.
      if (code instanceof Boolean) errCode = null;
      String msg = message == null ? "request failed" : String.valueOf(message);
      if (msg.isEmpty()) msg = "request failed";
      throw new WPushException(msg, errCode, httpStatus, data);
    }
    return data;
  }

  /** Success only if code is a number equal to 0 (never boolean). */
  static boolean isSuccessCode(Object code) {
    if (code instanceof Boolean) return false;
    if (code instanceof Number) {
      return ((Number) code).doubleValue() == 0.0;
    }
    return false;
  }

  static String joinChannel(Object channel) {
    if (channel == null) return null;
    if (channel instanceof String) return (String) channel;
    if (channel instanceof Collection) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Object c : (Collection<?>) channel) {
        if (!first) sb.append(',');
        first = false;
        sb.append(Objects.toString(c, ""));
      }
      return sb.toString();
    }
    if (channel instanceof Object[]) {
      StringBuilder sb = new StringBuilder();
      Object[] arr = (Object[]) channel;
      for (int i = 0; i < arr.length; i++) {
        if (i > 0) sb.append(',');
        sb.append(Objects.toString(arr[i], ""));
      }
      return sb.toString();
    }
    return String.valueOf(channel);
  }

  static String stringifyId(Object data) {
    if (data == null) return "";
    return String.valueOf(data);
  }

  public static final class Builder {
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private Duration timeout = Duration.ofSeconds(30);
    private Object defaultChannel;
    private HttpTransport transport;

    public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
    public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
    public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }
    public Builder defaultChannel(Object defaultChannel) { this.defaultChannel = defaultChannel; return this; }
    public Builder transport(HttpTransport transport) { this.transport = transport; return this; }

    public Client build() {
      return new Client(apiKey, baseUrl, timeout, defaultChannel, transport);
    }
  }
}
