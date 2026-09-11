use serde_json::{json, Value};
use wpush_sdk::{
    Client, Error, SendCodeParams, SendMailParams, SendParams, USER_AGENT, ValidationError,
    WPushError,
};

fn client_for(server: &mockito::ServerGuard) -> Client {
    Client::builder()
        .api_key("k")
        .base_url(server.url())
        .build()
        .expect("client")
}

#[test]
fn send_success() {
    let mut server = mockito::Server::new();
    let m = server
        .mock("POST", "/api/v1/send")
        .match_header("User-Agent", USER_AGENT)
        .match_header("X-API-Key", "k")
        .match_header("Content-Type", "application/json")
        .with_status(200)
        .with_header("content-type", "application/json")
        .with_body(r#"{"code":0,"message":"success","data":"12345"}"#)
        .create();

    let c = client_for(&server);
    let id = c
        .send(
            SendParams::new("t")
                .content("c")
                .channel(["wechat", "dingtalk"]),
        )
        .expect("send");
    assert_eq!(id, "12345");

    // Verify body via a second mock that inspects request — re-run with expect body
    m.assert();

    let mut server2 = mockito::Server::new();
    let m2 = server2
        .mock("POST", "/api/v1/send")
        .match_body(mockito::Matcher::PartialJsonString(
            r#"{"apikey":"k","title":"t","content":"c","channel":"wechat,dingtalk"}"#.into(),
        ))
        .with_body(r#"{"code":0,"message":"success","data":"12345"}"#)
        .create();
    let c2 = client_for(&server2);
    let id2 = c2
        .send(
            SendParams::new("t")
                .content("c")
                .channel(["wechat", "dingtalk"]),
        )
        .unwrap();
    assert_eq!(id2, "12345");
    m2.assert();
}

#[test]
fn reject_bool_code() {
    let mut server = mockito::Server::new();
    server
        .mock("POST", "/api/v1/send")
        .with_body(r#"{"code":false,"message":"nope","data":null}"#)
        .create();
    let c = client_for(&server);
    let err = c.send(SendParams::new("t")).unwrap_err();
    match err {
        Error::Api(e) => {
            assert!(e.code.is_none());
            assert_eq!(e.message, "nope");
        }
        other => panic!("expected Api error, got {other:?}"),
    }
}

#[test]
fn api_error_401() {
    let mut server = mockito::Server::new();
    server
        .mock("POST", "/api/v1/send")
        .with_body(r#"{"code":401,"message":"bad key","data":null}"#)
        .create();
    let c = Client::builder()
        .api_key("bad")
        .base_url(server.url())
        .build()
        .unwrap();
    let err = c.send(SendParams::new("t")).unwrap_err();
    let e: &WPushError = err.as_api().expect("api");
    assert_eq!(e.code, Some(401));
    assert_eq!(e.message, "bad key");
}

#[test]
fn option_topic_conflict() {
    let c = Client::builder()
        .api_key("k")
        .base_url("http://example.invalid")
        .build()
        .unwrap();
    let err = c
        .send(
            SendParams::new("t")
                .option("ops")
                .topic_code("x"),
        )
        .unwrap_err();
    assert!(matches!(err, Error::Validation(_)));
    assert_eq!(
        err.as_validation().unwrap().message,
        "option and topic_code cannot be used together"
    );
}

#[test]
fn query_success() {
    let mut server = mockito::Server::new();
    let m = server
        .mock("POST", "/api/v1/query")
        .match_body(mockito::Matcher::PartialJsonString(
            r#"{"apikey":"k","id":"123"}"#.into(),
        ))
        .with_body(r#"{"code":0,"message":"success","data":{"id":"123","status":1}}"#)
        .create();
    let c = client_for(&server);
    let out = c.query("123").unwrap();
    assert_eq!(out.get("id").and_then(Value::as_str), Some("123"));
    m.assert();
}

#[test]
fn send_numeric_data() {
    let mut server = mockito::Server::new();
    server
        .mock("POST", "/api/v1/send")
        .with_body(r#"{"code":0,"message":"ok","data":999}"#)
        .create();
    let c = client_for(&server);
    let id = c.send(SendParams::new("t")).unwrap();
    assert_eq!(id, "999");
}

#[test]
fn send_mail_success() {
    let mut server = mockito::Server::new();
    let m = server
        .mock("POST", "/api/v1/send_mail")
        .match_body(mockito::Matcher::PartialJsonString(
            r#"{"apikey":"k","to":"a@b.com","title":"hi","content":"body"}"#.into(),
        ))
        .with_body(r#"{"code":0,"message":"success","data":"mail-1"}"#)
        .create();
    let c = client_for(&server);
    let id = c
        .send_mail(SendMailParams::new("a@b.com", "hi").content("body"))
        .unwrap();
    assert_eq!(id, "mail-1");
    m.assert();
}

#[test]
fn send_code_success() {
    let mut server = mockito::Server::new();
    let m = server
        .mock("POST", "/api/v1/send_code")
        .match_body(mockito::Matcher::PartialJsonString(
            r#"{"apikey":"k","phone":"13800138000","code":"123456"}"#.into(),
        ))
        .with_body(r#"{"code":0,"message":"success","data":"code-1"}"#)
        .create();
    let c = client_for(&server);
    let id = c
        .send_code(SendCodeParams::new("13800138000", "123456"))
        .unwrap();
    assert_eq!(id, "code-1");
    m.assert();
}

#[test]
fn send_code_bad_phone() {
    let c = Client::builder()
        .api_key("k")
        .base_url("http://example.invalid")
        .build()
        .unwrap();
    let err = c
        .send_code(SendCodeParams::new("123", "123456"))
        .unwrap_err();
    assert!(matches!(err, Error::Validation(ValidationError { .. })));
}

#[test]
fn query_relay_success() {
    let mut server = mockito::Server::new();
    let m = server
        .mock("POST", "/api/v1/query_relay")
        .match_body(mockito::Matcher::PartialJsonString(
            r#"{"apikey":"k","id":"r1"}"#.into(),
        ))
        .with_body(r#"{"code":0,"message":"success","data":{"id":"r1","status":1}}"#)
        .create();
    let c = client_for(&server);
    let out = c.query_relay("r1").unwrap();
    assert_eq!(out.get("id").and_then(Value::as_str), Some("r1"));
    m.assert();
}

#[test]
fn title_required() {
    let c = Client::builder()
        .api_key("k")
        .base_url("http://example.invalid")
        .build()
        .unwrap();
    let err = c
        .send(SendParams {
            title: String::new(),
            ..Default::default()
        })
        .unwrap_err();
    assert_eq!(err.as_validation().unwrap().message, "title is required");
}

#[test]
fn missing_api_key() {
    // Ensure env is not set for this process check — only if already unset
    if std::env::var("WPUSH_API_KEY").is_err() {
        let err = Client::builder().build().unwrap_err();
        assert!(err.message.contains("api_key"));
    }
}

#[test]
fn user_agent_constant() {
    assert_eq!(USER_AGENT, "wpush-rust/0.1.0");
    let _ = json!({"ok": true});
}
