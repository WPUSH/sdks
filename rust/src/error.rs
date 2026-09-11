use serde_json::Value;
use thiserror::Error;

/// Client-side validation failure before the HTTP request is sent.
#[derive(Debug, Clone, Error, PartialEq, Eq)]
#[error("{message}")]
pub struct ValidationError {
    pub message: String,
}

impl ValidationError {
    pub fn new(message: impl Into<String>) -> Self {
        Self {
            message: message.into(),
        }
    }
}

/// WPUSH API or client/transport error.
#[derive(Debug, Clone, Error)]
pub struct WPushError {
    pub message: String,
    pub code: Option<i64>,
    pub http_status: u16,
    pub data: Option<Value>,
}

impl std::fmt::Display for WPushError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        if let Some(code) = self.code {
            write!(
                f,
                "{} | code={} | http={}",
                self.message, code, self.http_status
            )
        } else {
            write!(f, "{} | http={}", self.message, self.http_status)
        }
    }
}

impl WPushError {
    pub fn new(
        message: impl Into<String>,
        code: Option<i64>,
        http_status: u16,
        data: Option<Value>,
    ) -> Self {
        Self {
            message: message.into(),
            code,
            http_status,
            data,
        }
    }
}

/// Unified error type returned by Client methods.
#[derive(Debug, Error)]
pub enum Error {
    #[error(transparent)]
    Validation(#[from] ValidationError),
    #[error(transparent)]
    Api(#[from] WPushError),
    #[error(transparent)]
    Http(#[from] reqwest::Error),
    #[error(transparent)]
    Json(#[from] serde_json::Error),
}

impl Error {
    /// Downcast to [`ValidationError`] if applicable.
    pub fn as_validation(&self) -> Option<&ValidationError> {
        match self {
            Error::Validation(e) => Some(e),
            _ => None,
        }
    }

    /// Downcast to [`WPushError`] if applicable.
    pub fn as_api(&self) -> Option<&WPushError> {
        match self {
            Error::Api(e) => Some(e),
            _ => None,
        }
    }
}
