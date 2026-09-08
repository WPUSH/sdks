package cn.wpush.sdk;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

  static final class CapturingTransport implements HttpTransport {
    String lastUrl;
    Map<String, String> lastHeaders;
    String lastBody;
    Response next;

    CapturingTransport(Response next) {
      this.next = next;
    }

    @Override
    public Response post(String url, Map<String, String> headers, String jsonBody) {
      this.lastUrl = url;
      this.lastHeaders = headers;
      this.lastBody = jsonBody;
      return next;
    }
  }

  private static Client client(HttpTransport t) {
    return Client.builder().apiKey("k").transport(t).build();
  }

  @Test
  void sendSuccessJoinsChannelAndHeaders() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"success\",\"data\":\"12345\"}"));
    Client c = client(t);
    String mid = c.send("t", "c", Arrays.asList("wechat", "dingtalk"), null, null, null, null);
    assertEquals("12345", mid);
    assertEquals(Client.USER_AGENT, t.lastHeaders.get("User-Agent"));
    assertEquals("k", t.lastHeaders.get("X-API-Key"));
    assertTrue(t.lastUrl.endsWith("/api/v1/send"));
    Map<String, Object> body = Json.parseObject(t.lastBody);
    assertEquals("k", body.get("apikey"));
    assertEquals("wechat,dingtalk", body.get("channel"));
    assertEquals("t", body.get("title"));
  }

  @Test
  void sendNumericDataStringified() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"ok\",\"data\":999}"));
    assertEquals("999", client(t).send("t"));
  }

  @Test
  void rejectBoolCode() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":false,\"message\":\"nope\",\"data\":null}"));
    WPushException ex = assertThrows(WPushException.class, () -> client(t).send("t"));
    assertNull(ex.getCode());
  }

  @Test
  void apiError401() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":401,\"message\":\"API Key错误\",\"data\":null}"));
    WPushException ex = assertThrows(WPushException.class, () -> client(t).send("t"));
    assertEquals(401, ex.getCode());
  }

  @Test
  void optionTopicConflict() {
    CapturingTransport t = new CapturingTransport(new HttpTransport.Response(200, "{}"));
    Client c2 = client(t);
    assertThrows(ValidationException.class, () -> c2.send("t", null, null, "ops", "topic1", null, null));
    assertNull(t.lastUrl);
  }

  @Test
  void missingApiKey() {
    String prev = System.getenv("WPUSH_API_KEY");
    // Cannot easily clear env; pass empty and ensure no env dependency by using blank after checking
    // If env is set in CI this may pass; use builder with explicit empty only when env unset.
    if (prev == null || prev.isEmpty()) {
      assertThrows(ValidationException.class, () -> Client.builder().build());
    }
  }

  @Test
  void querySuccess() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"success\",\"data\":{\"id\":\"123\",\"status\":1,\"title\":\"t\"}}"));
    Map<String, Object> out = client(t).query("123");
    assertEquals("123", out.get("id"));
    assertEquals(1, out.get("status"));
    Map<String, Object> body = Json.parseObject(t.lastBody);
    assertEquals("123", body.get("id"));
    assertEquals("k", body.get("apikey"));
  }

  @Test
  void idempotencyHeader() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"ok\",\"data\":\"1\"}"));
    client(t).send("t", null, null, null, null, null, "idem-1");
    assertEquals("idem-1", t.lastHeaders.get("X-Idempotency-Key"));
  }

  @Test
  void sendMailSuccess() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"success\",\"data\":\"mail-1\"}"));
    assertEquals("mail-1", client(t).sendMail("a@b.com", "hi", "body"));
    assertTrue(t.lastUrl.endsWith("/api/v1/send_mail"));
    Map<String, Object> body = Json.parseObject(t.lastBody);
    assertEquals("a@b.com", body.get("to"));
    assertEquals("hi", body.get("title"));
    assertEquals("body", body.get("content"));
  }

  @Test
  void sendCodeSuccess() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"success\",\"data\":\"code-1\"}"));
    assertEquals("code-1", client(t).sendCode("13800138000", "123456"));
    assertTrue(t.lastUrl.endsWith("/api/v1/send_code"));
    Map<String, Object> body = Json.parseObject(t.lastBody);
    assertEquals("13800138000", body.get("phone"));
    assertEquals("123456", body.get("code"));
  }

  @Test
  void sendCodeBadPhone() {
    CapturingTransport t = new CapturingTransport(new HttpTransport.Response(200, "{}"));
    assertThrows(ValidationException.class, () -> client(t).sendCode("123", "123456"));
    assertNull(t.lastUrl);
  }

  @Test
  void queryRelaySuccess() {
    CapturingTransport t = new CapturingTransport(
        new HttpTransport.Response(200, "{\"code\":0,\"message\":\"success\",\"data\":{\"id\":\"r1\",\"status\":1}}"));
    Map<String, Object> out = client(t).queryRelay("r1");
    assertEquals("r1", out.get("id"));
    assertTrue(t.lastUrl.endsWith("/api/v1/query_relay"));
    Map<String, Object> body = Json.parseObject(t.lastBody);
    assertEquals("r1", body.get("id"));
  }

  @Test
  void isSuccessCodeRejectsBool() {
    assertFalse(Client.isSuccessCode(Boolean.FALSE));
    assertFalse(Client.isSuccessCode(Boolean.TRUE));
    assertTrue(Client.isSuccessCode(0));
    assertTrue(Client.isSuccessCode(0.0));
  }
}
