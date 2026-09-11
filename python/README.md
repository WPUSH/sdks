# wpush (Python)

官方 WPUSH Python SDK。零运行时依赖（stdlib `urllib`）。

## 安装

```bash
pip install -e ".[dev]"
# 或从源码目录: pip install -e .
export WPUSH_API_KEY=WPUSH_your_key
```

```python
from wpush import Client, WPushError, ValidationError

client = Client()  # 读取 WPUSH_API_KEY
# 或显式传 key:
# client = Client(api_key="WPUSH_your_key")
```

## send — 基础微信推送

```python
msg_id = client.send("告警标题", "正文内容", channel="wechat")
print(msg_id)
```

## send — 多渠道 + option + 幂等键

`option` 为渠道内实例编码（如飞书 `ops`），不可与 `topic_code` 同用。

```python
msg_id = client.send(
    "运维告警",
    "CPU 过高",
    channel=["feishu", "dingtalk"],
    option="ops",
    idempotency_key="demo-idem-001",
)
print(msg_id)
```

## send — topic_code（不带 option）

```python
msg_id = client.send(
    "主题广播",
    "全员通知",
    topic_code="mytopic",
)
print(msg_id)
```

## query

```python
info = client.query(msg_id)
print(info)  # id / title / status / channel ...
```

## send_mail

```python
relay_id = client.send_mail(
    "user@example.com",
    "邮件标题",
    "邮件正文",
)
print(relay_id)
```

## send_code

```python
relay_id = client.send_code("13800138000", "123456")
print(relay_id)
```

## query_relay

```python
relay = client.query_relay(relay_id)
print(relay)
```

## 错误处理

```python
try:
    client.send("", channel="wechat")  # 本地预检失败
except ValidationError as e:
    print("validation:", e.message)
except WPushError as e:
    print("api:", e.message, e.code, e.http_status)
```

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-python/0.1.0`

