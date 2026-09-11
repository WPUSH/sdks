use std::collections::HashMap;
use std::env;
use std::time::Duration;

use regex::Regex;
use reqwest::blocking::Client as HttpClient;
use reqwest::header::{HeaderMap, HeaderValue, ACCEPT, CONTENT_TYPE, USER_AGENT};
use serde_json::{json, Map, Value};

use crate::error::{Error, ValidationError, WPushError};
use crate::USER_AGENT as UA;

const DEFAULT_BASE_URL: &str = "https://api.wpush.cn";

fn phone_re() -> &'static Regex {
    static RE: std::sync::OnceLock<Regex> = std::sync::OnceLock::new();
    RE.get_or_init(|| Regex::new(r"^(\+?86)?1\d{10}$").expect("phone regex"))
}

fn code_re() -> &'static Regex {
    static RE: std::sync::OnceLock<Regex> = std::sync::OnceLock::new();
    RE.get_or_init(|| Regex::new(r"^[A-Za-z0-9]{1,6}$").expect("code regex"))
}

/// Channel input: a single string or a list (joined with commas, order preserved).
#[derive(Debug, Clone)]
pub struct Channel(String);

impl Channel {
    pub fn as_str(&self) -> &str {
        &self.0
    }

    pub fn into_string(self) -> String {
        self.0
    }
}

impl From<&str> for Channel {
    fn from(s: &str) -> Self {
        Self(s.to_string())
    }
}

impl From<String> for Channel {
    fn from(s: String) -> Self {
        Self(s)
    }
}

impl From<Vec<&str>> for Channel {
    fn from(v: Vec<&str>) -> Self {
        Self(v.join(","))
    }
}

impl From<Vec<String>> for Channel {
    fn from(v: Vec<String>) -> Self {
        Self(v.join(","))
    }
}

impl<const N: usize> From<[&str; N]> for Channel {
    fn from(v: [&str; N]) -> Self {
        Self(v.join(","))
    }
}

/// WPUSH blocking HTTP client.
#[derive(Debug, Clone)]
pub struct Client {
    api_key: String,
    base_url: String,
    http: HttpClient,
    default_channel: Option<String>,
}

/// Builder for [`Client`].
#[derive(Debug, Default)]
pub struct ClientBuilder {
    api_key: Option<String>,
    base_url: Option<String>,
    timeout: Option<Duration>,
    default_channel: Option<String>,
}

impl ClientBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn api_key(mut self, key: impl Into<String>) -> Self {
        self.api_key = Some(key.into());
        self
    }

    pub fn base_url(mut self, url: impl Into<String>) -> Self {
        self.base_url = Some(url.into());
        self
    }

    pub fn timeout(mut self, timeout: Duration) -> Self {
        self.timeout = Some(timeout);
        self
    }

    pub fn default_channel(mut self, channel: impl Into<String>) -> Self {
        self.default_channel = Some(channel.into());
        self
    }

    pub fn build(self) -> Result<Client, ValidationError> {
        let key = self
            .api_key
            .filter(|k| !k.is_empty())
            .or_else(|| env::var("WPUSH_API_KEY").ok().filter(|k| !k.is_empty()))
            .ok_or_else(|| {
                ValidationError::new("api_key is required (or set WPUSH_API_KEY)")
            })?;

        let base = self
            .base_url
            .unwrap_or_else(|| DEFAULT_BASE_URL.to_string())
            .trim_end_matches('/')
            .to_string();

        let timeout = self.timeout.unwrap_or(Duration::from_secs(30));
        let http = HttpClient::builder()
            .timeout(timeout)
            .user_agent(UA)
            .build()
            .map_err(|e| ValidationError::new(format!("failed to build HTTP client: {e}")))?;

        Ok(Client {
            api_key: key,
            base_url: base,
            http,
            default_channel: self.default_channel.filter(|c| !c.is_empty()),
        })
    }
}

impl Client {
    /// Create a client with an explicit API key (other options use defaults).
    pub fn new(api_key: impl Into<String>) -> Result<Self, ValidationError> {
        ClientBuilder::new().api_key(api_key).build()
    }

    /// Start a builder. API key from `.api_key(...)` or `WPUSH_API_KEY`.
    pub fn builder() -> ClientBuilder {
        ClientBuilder::new()
    }

    /// POST `/api/v1/send` — returns message id string.
    pub fn send(&self, params: SendParams) -> Result<String, Error> {
        if params.title.is_empty() {
            return Err(ValidationError::new("title is required").into());
        }
        let option = params.option.as_deref().filter(|s| !s.is_empty());
        let topic = params.topic_code.as_deref().filter(|s| !s.is_empty());
        if option.is_some() && topic.is_some() {
            return Err(
                ValidationError::new("option and topic_code cannot be used together").into(),
            );
        }

        let mut channel = params
            .channel
            .map(|c| c.into_string())
            .filter(|s| !s.is_empty());
        if channel.is_none() {
            channel = self.default_channel.clone();
        }

        let mut payload = Map::new();
        payload.insert("apikey".into(), Value::String(self.api_key.clone()));
        payload.insert("title".into(), Value::String(params.title));
        if let Some(content) = params.content.filter(|s| !s.is_empty()) {
            payload.insert("content".into(), Value::String(content));
        }
        if let Some(ch) = channel {
            payload.insert("channel".into(), Value::String(ch));
        }
        if let Some(opt) = option {
            payload.insert("option".into(), Value::String(opt.to_string()));
        }
        if let Some(tc) = topic {
            payload.insert("topic_code".into(), Value::String(tc.to_string()));
        }
        if let Some(url) = params.url.filter(|s| !s.is_empty()) {
            payload.insert("url".into(), Value::String(url));
        }

        let data = self.post("/api/v1/send", Value::Object(payload), params.idempotency_key)?;
        Ok(stringify_id(&data))
    }

    /// POST `/api/v1/query` — returns decoded data object.
    pub fn query(&self, message_id: impl AsRef<str>) -> Result<HashMap<String, Value>, Error> {
        self.query_with(QueryParams {
            id: message_id.as_ref().to_string(),
            idempotency_key: None,
        })
    }

    /// POST `/api/v1/query` with optional idempotency key.
    pub fn query_with(&self, params: QueryParams) -> Result<HashMap<String, Value>, Error> {
        if params.id.is_empty() {
            return Err(ValidationError::new("message_id is required").into());
        }
        let payload = json!({
            "apikey": self.api_key,
            "id": params.id,
        });
        let data = self.post("/api/v1/query", payload, params.idempotency_key)?;
        decode_object(data)
    }

    /// POST `/api/v1/send_mail` — returns relay id string.
    pub fn send_mail(&self, params: SendMailParams) -> Result<String, Error> {
        if params.to.is_empty() {
            return Err(ValidationError::new("to is required").into());
        }
        if params.title.is_empty() {
            return Err(ValidationError::new("title is required").into());
        }
        let mut payload = Map::new();
        payload.insert("apikey".into(), Value::String(self.api_key.clone()));
        payload.insert("to".into(), Value::String(params.to));
        payload.insert("title".into(), Value::String(params.title));
        if let Some(content) = params.content.filter(|s| !s.is_empty()) {
            payload.insert("content".into(), Value::String(content));
        }
        let data = self.post(
            "/api/v1/send_mail",
            Value::Object(payload),
            params.idempotency_key,
        )?;
        Ok(stringify_id(&data))
    }

    /// POST `/api/v1/send_code` — returns relay id string.
    pub fn send_code(&self, params: SendCodeParams) -> Result<String, Error> {
        if params.phone.is_empty() || !phone_re().is_match(&params.phone) {
            return Err(ValidationError::new("invalid phone").into());
        }
        if params.code.is_empty() || !code_re().is_match(&params.code) {
            return Err(ValidationError::new("invalid code").into());
        }
        let payload = json!({
            "apikey": self.api_key,
            "phone": params.phone,
            "code": params.code,
        });
        let data = self.post("/api/v1/send_code", payload, params.idempotency_key)?;
        Ok(stringify_id(&data))
    }

    /// POST `/api/v1/query_relay` — returns decoded data object.
    pub fn query_relay(&self, id: impl AsRef<str>) -> Result<HashMap<String, Value>, Error> {
        self.query_relay_with(QueryParams {
            id: id.as_ref().to_string(),
            idempotency_key: None,
        })
    }

    /// POST `/api/v1/query_relay` with optional idempotency key.
    pub fn query_relay_with(
        &self,
        params: QueryParams,
    ) -> Result<HashMap<String, Value>, Error> {
        if params.id.is_empty() {
            return Err(ValidationError::new("id is required").into());
        }
        let payload = json!({
            "apikey": self.api_key,
            "id": params.id,
        });
        let data = self.post("/api/v1/query_relay", payload, params.idempotency_key)?;
        decode_object(data)
    }

    fn post(
        &self,
        path: &str,
        payload: Value,
        idempotency_key: Option<String>,
    ) -> Result<Value, Error> {
        let url = format!("{}{}", self.base_url, path);
        let mut headers = HeaderMap::new();
        headers.insert(CONTENT_TYPE, HeaderValue::from_static("application/json"));
        headers.insert(ACCEPT, HeaderValue::from_static("application/json"));
        headers.insert(USER_AGENT, HeaderValue::from_static(UA));
        headers.insert(
            "X-API-Key",
            HeaderValue::from_str(&self.api_key).map_err(|e| {
                WPushError::new(format!("invalid API key header: {e}"), None, 0, None)
            })?,
        );
        if let Some(key) = idempotency_key.filter(|k| !k.is_empty()) {
            if let Ok(v) = HeaderValue::from_str(&key) {
                headers.insert("X-Idempotency-Key", v);
            }
        }

        let resp = self
            .http
            .post(&url)
            .headers(headers)
            .json(&payload)
            .send()
            .map_err(|e| WPushError::new(e.to_string(), None, 0, None))?;

        let status = resp.status().as_u16();
        let raw = resp
            .text()
            .map_err(|e| WPushError::new(e.to_string(), None, status, None))?;

        let env: Value = match serde_json::from_str(&raw) {
            Ok(v) => v,
            Err(_) => {
                return Err(WPushError::new(
                    "invalid JSON response",
                    None,
                    status,
                    Some(Value::String(raw)),
                )
                .into());
            }
        };

        let code = env.get("code").cloned().unwrap_or(Value::Null);
        if !is_success_code(&code) {
            let message = env
                .get("message")
                .and_then(|m| m.as_str())
                .unwrap_or("")
                .to_string();
            let data = env.get("data").cloned();
            return Err(WPushError::new(message, parse_code(&code), status, data).into());
        }

        Ok(env.get("data").cloned().unwrap_or(Value::Null))
    }
}

/// Arguments for [`Client::send`].
#[derive(Debug, Clone, Default)]
pub struct SendParams {
    pub title: String,
    pub content: Option<String>,
    pub channel: Option<Channel>,
    pub option: Option<String>,
    pub topic_code: Option<String>,
    pub url: Option<String>,
    pub idempotency_key: Option<String>,
}

impl SendParams {
    pub fn new(title: impl Into<String>) -> Self {
        Self {
            title: title.into(),
            ..Default::default()
        }
    }

    pub fn content(mut self, content: impl Into<String>) -> Self {
        self.content = Some(content.into());
        self
    }

    pub fn channel(mut self, channel: impl Into<Channel>) -> Self {
        self.channel = Some(channel.into());
        self
    }

    pub fn option(mut self, option: impl Into<String>) -> Self {
        self.option = Some(option.into());
        self
    }

    pub fn topic_code(mut self, topic_code: impl Into<String>) -> Self {
        self.topic_code = Some(topic_code.into());
        self
    }

    pub fn url(mut self, url: impl Into<String>) -> Self {
        self.url = Some(url.into());
        self
    }

    pub fn idempotency_key(mut self, key: impl Into<String>) -> Self {
        self.idempotency_key = Some(key.into());
        self
    }
}

/// Arguments for query / query_relay.
#[derive(Debug, Clone, Default)]
pub struct QueryParams {
    pub id: String,
    pub idempotency_key: Option<String>,
}

/// Arguments for [`Client::send_mail`].
#[derive(Debug, Clone, Default)]
pub struct SendMailParams {
    pub to: String,
    pub title: String,
    pub content: Option<String>,
    pub idempotency_key: Option<String>,
}

impl SendMailParams {
    pub fn new(to: impl Into<String>, title: impl Into<String>) -> Self {
        Self {
            to: to.into(),
            title: title.into(),
            ..Default::default()
        }
    }

    pub fn content(mut self, content: impl Into<String>) -> Self {
        self.content = Some(content.into());
        self
    }

    pub fn idempotency_key(mut self, key: impl Into<String>) -> Self {
        self.idempotency_key = Some(key.into());
        self
    }
}

/// Arguments for [`Client::send_code`].
#[derive(Debug, Clone, Default)]
pub struct SendCodeParams {
    pub phone: String,
    pub code: String,
    pub idempotency_key: Option<String>,
}

impl SendCodeParams {
    pub fn new(phone: impl Into<String>, code: impl Into<String>) -> Self {
        Self {
            phone: phone.into(),
            code: code.into(),
            ..Default::default()
        }
    }

    pub fn idempotency_key(mut self, key: impl Into<String>) -> Self {
        self.idempotency_key = Some(key.into());
        self
    }
}

fn is_success_code(code: &Value) -> bool {
    match code {
        Value::Number(n) => {
            if let Some(i) = n.as_i64() {
                i == 0
            } else if let Some(u) = n.as_u64() {
                u == 0
            } else if let Some(f) = n.as_f64() {
                f == 0.0
            } else {
                false
            }
        }
        // bool (and everything else) is never success
        _ => false,
    }
}

fn parse_code(code: &Value) -> Option<i64> {
    match code {
        Value::Number(n) => n
            .as_i64()
            .or_else(|| n.as_u64().map(|u| u as i64))
            .or_else(|| n.as_f64().map(|f| f as i64)),
        _ => None,
    }
}

fn stringify_id(data: &Value) -> String {
    match data {
        Value::String(s) => s.clone(),
        Value::Number(n) => n.to_string(),
        Value::Bool(b) => b.to_string(),
        Value::Null => String::new(),
        other => {
            let s = other.to_string();
            s.trim_matches('"').to_string()
        }
    }
}

fn decode_object(data: Value) -> Result<HashMap<String, Value>, Error> {
    match data {
        Value::Object(map) => Ok(map.into_iter().collect()),
        other => Err(WPushError::new(
            "invalid query data",
            None,
            0,
            Some(other),
        )
        .into()),
    }
}
