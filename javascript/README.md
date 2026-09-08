# @wpush/sdk

Official WPUSH TypeScript/Node SDK (Node 18+ `fetch`).

## Install / env

```bash
npm install @wpush/sdk
export WPUSH_API_KEY=WPUSH_your_key
```

```ts
import { Client, WPushError, ValidationError } from "@wpush/sdk";

const client = new Client(); // reads WPUSH_API_KEY
// or: new Client({ apiKey: "WPUSH_your_key" })
```

## send — basic wechat

```ts
const msgId = await client.send({
  title: "告警标题",
  content: "正文内容",
  channel: "wechat",
});
console.log(msgId);
```

## send — multi-channel + option + idempotencyKey

`option` is the per-channel instance code (e.g. Feishu `ops`). Mutually exclusive with `topicCode`.

```ts
const msgId = await client.send({
  title: "运维告警",
  content: "CPU 过高",
  channel: ["feishu", "dingtalk"],
  option: "ops",
  idempotencyKey: "demo-idem-001",
});
console.log(msgId);
```

## send — topicCode (no option)

```ts
const msgId = await client.send({
  title: "主题广播",
  content: "全员通知",
  topicCode: "mytopic",
});
console.log(msgId);
```

## query

```ts
const info = await client.query(msgId);
console.log(info); // id / title / status / channel ...
```

## sendMail

```ts
const relayId = await client.sendMail({
  to: "user@example.com",
  title: "邮件标题",
  content: "邮件正文",
});
console.log(relayId);
```

## sendCode

```ts
const relayId = await client.sendCode({
  phone: "13800138000",
  code: "123456",
});
console.log(relayId);
```

## queryRelay

```ts
const relay = await client.queryRelay(relayId);
console.log(relay);
```

## Error handling

```ts
try {
  await client.send({ title: "", channel: "wechat" });
} catch (e) {
  if (e instanceof ValidationError) {
    console.error("validation:", e.message);
  } else if (e instanceof WPushError) {
    console.error("api:", e.message, e.code, e.httpStatus);
  } else {
    throw e;
  }
}
```

## Spec & UA

See [SPEC.md](../SPEC.md).

User-Agent: `wpush-js/0.1.0`
