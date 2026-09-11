//! Official WPUSH Rust SDK (`wpush`).
//!
//! Thin client for `https://api.wpush.cn/api/v1/*`.
//! User-Agent: `wpush-rust/0.1.0`.

mod client;
mod error;

pub use client::{
    Channel, Client, ClientBuilder, QueryParams, SendCodeParams, SendMailParams, SendParams,
};
pub use error::{Error, ValidationError, WPushError};

/// Crate version (matches User-Agent).
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// HTTP User-Agent sent on every request.
pub const USER_AGENT: &str = concat!("wpush-rust/", env!("CARGO_PKG_VERSION"));
