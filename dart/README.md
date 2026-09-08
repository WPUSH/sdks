# wpush (Dart)

官方 WPUSH Dart SDK。依赖 [`http`](https://pub.dev/packages/http)。

## 安装 / Install

```yaml
dependencies:
  wpush: ^0.1.0
```

```bash
dart pub get
export WPUSH_API_KEY=WPUSH_your_key
```

```dart
import 'package:wpush/wpush.dart';

final client = Client(); // 读取 WPUSH_API_KEY
// 或显式传 key:
// final client = Client(apiKey: 'WPUSH_your_key');
```

## send — 基础微信推送

```dart
final msgId = await client.send(title: '告警标题', content: '正文内容', channel: 'wechat');
print(msgId);
```

## send — 多渠道 + option + 幂等键

`option` 为渠道内实例编码（如飞书 `ops`），不可与 `topicCode` 同用。

```dart
final msgId = await client.send(
  title: '运维告警',
  content: 'CPU 过高',
  channel: ['feishu', 'dingtalk'],
  option: 'ops',
  idempotencyKey: 'demo-idem-001',
);
print(msgId);
```

## send — topicCode（不带 option）

```dart
final msgId = await client.send(
  title: '主题广播',
  content: '全员通知',
  topicCode: 'mytopic',
);
print(msgId);
```

## query

```dart
final info = await client.query(msgId);
print(info); // id / title / status / channel ...
```

## sendMail

```dart
final relayId = await client.sendMail(
  to: 'user@example.com',
  title: '邮件标题',
  content: '邮件正文',
);
print(relayId);
```

## sendCode

```dart
final relayId = await client.sendCode(phone: '13800138000', code: '123456');
print(relayId);
```

## queryRelay

```dart
final relay = await client.queryRelay(relayId);
print(relay);
```

## 错误处理

```dart
try {
  await client.send(title: ''); // 本地预检失败
} on ValidationException catch (e) {
  print('validation: ${e.message}');
} on WPushException catch (e) {
  print('api: ${e.message} ${e.code} ${e.httpStatus}');
}
```

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-dart/0.1.0`
