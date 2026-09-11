# wpush (Rust)

官方 WPUSH Rust SDK。Crate：`wpush-sdk`

阻塞式 HTTP 客户端（reqwest），API 表面与 Go SDK 对齐。

## 安装 / 环境变量

```toml
[dependencies]
wpush = "0.1.0"
```

```bash
export WPUSH_API_KEY=WPUSH_your_key
```

```rust
use wpush_sdk::Client;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    // 读取 WPUSH_API_KEY
    let client = Client::builder().build()?;
    // 或显式传 key:
    // Client::new("WPUSH_your_key")?;
    // Client::builder().api_key("WPUSH_your_key").timeout(std::time::Duration::from_secs(30)).build()?;
    let _ = client;
    Ok(())
}
```

## Send — 基础微信推送

```rust
use wpush_sdk::{Client, SendParams};

let client = Client::builder().build()?;
let msg_id = client.send(
    SendParams::new("告警标题")
        .content("正文内容")
        .channel("wechat"),
)?;
println!("{msg_id}");
```

## Send — 多渠道 + option + idempotency_key

`option` 为渠道内实例编码（如飞书 `ops`），不可与 `topic_code` 同用。

```rust
use wpush_sdk::SendParams;

let msg_id = client.send(
    SendParams::new("运维告警")
        .content("CPU 过高")
        .channel(["feishu", "dingtalk"])
        .option("ops")
        .idempotency_key("demo-idem-001"),
)?;
println!("{msg_id}");
```

## Send — topic_code（不带 option）

```rust
use wpush_sdk::SendParams;

let msg_id = client.send(
    SendParams::new("主题广播")
        .content("全员通知")
        .topic_code("mytopic"),
)?;
println!("{msg_id}");
```

## Query

```rust
let info = client.query(&msg_id)?;
println!("{info:?}"); // id / title / status / channel ...
```

## SendMail

```rust
use wpush_sdk::SendMailParams;

let relay_id = client.send_mail(
    SendMailParams::new("user@example.com", "邮件标题").content("邮件正文"),
)?;
println!("{relay_id}");
```

## SendCode

```rust
use wpush_sdk::SendCodeParams;

let relay_id = client.send_code(SendCodeParams::new("13800138000", "123456"))?;
println!("{relay_id}");
```

## QueryRelay

```rust
let relay = client.query_relay(&relay_id)?;
println!("{relay:?}");
```

## 错误处理

Rust 使用 `wpush::Error`，变体包括 `Validation`（`ValidationError`）与 `Api`（`WPushError`）。

```rust
use wpush_sdk::{Client, Error, SendParams};

let client = Client::builder().build()?;
match client.send(SendParams::new("").channel("wechat")) {
    Err(Error::Validation(e)) => eprintln!("validation: {}", e.message),
    Err(Error::Api(e)) => eprintln!("api: {} {:?} {}", e.message, e.code, e.http_status),
    Err(e) => eprintln!("other: {e}"),
    Ok(id) => println!("{id}"),
}
```

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-rust/0.1.0`
