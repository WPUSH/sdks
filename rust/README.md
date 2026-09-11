# wpush (Rust)

Official WPUSH Rust SDK. Crate: `wpush-sdk`

Blocking HTTP client (reqwest) matching the Go SDK surface.

## Install / env

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
    // reads WPUSH_API_KEY
    let client = Client::builder().build()?;
    // or: Client::new("WPUSH_your_key")?;
    // or: Client::builder().api_key("WPUSH_your_key").timeout(std::time::Duration::from_secs(30)).build()?;
    let _ = client;
    Ok(())
}
```

## Send — basic wechat

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

## Send — multi-channel + option + idempotency_key

`option` is the per-channel instance code (e.g. Feishu `ops`). Mutually exclusive with `topic_code`.

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

## Send — topic_code (no option)

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

## Error handling

Rust uses `wpush::Error` with variants `Validation` (`ValidationError`) and `Api` (`WPushError`).

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

## Spec & UA

See [SPEC.md](../SPEC.md).

User-Agent: `wpush-rust/0.1.0`
