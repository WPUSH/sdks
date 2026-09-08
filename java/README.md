# wpush-sdk (Java)

官方 WPUSH Java SDK。JDK 11+ `HttpClient`，零第三方运行时依赖。

## 安装 / Install

Maven:

```xml
<dependency>
  <groupId>cn.wpush</groupId>
  <artifactId>wpush-sdk</artifactId>
  <version>0.1.0</version>
</dependency>
```

```bash
export WPUSH_API_KEY=WPUSH_your_key
```

```java
import cn.wpush.sdk.Client;
import cn.wpush.sdk.ValidationException;
import cn.wpush.sdk.WPushException;

Client client = Client.builder().build(); // 读取 WPUSH_API_KEY
// 或显式传 key:
// Client client = Client.builder().apiKey("WPUSH_your_key").build();
```

## send — 基础微信推送

```java
String msgId = client.send("告警标题", "正文内容", "wechat");
System.out.println(msgId);
```

## send — 多渠道 + option + 幂等键

`option` 为渠道内实例编码（如飞书 `ops`），不可与 `topicCode` 同用。

```java
import java.util.Arrays;

String msgId = client.send(
    "运维告警",
    "CPU 过高",
    Arrays.asList("feishu", "dingtalk"),
    "ops",
    null,
    null,
    "demo-idem-001"
);
System.out.println(msgId);
```

## send — topicCode（不带 option）

```java
String msgId = client.send(
    "主题广播",
    "全员通知",
    null,
    null,
    "mytopic",
    null,
    null
);
System.out.println(msgId);
```

## query

```java
Map<String, Object> info = client.query(msgId);
System.out.println(info); // id / title / status / channel ...
```

## sendMail

```java
String relayId = client.sendMail(
    "user@example.com",
    "邮件标题",
    "邮件正文"
);
System.out.println(relayId);
```

## sendCode

```java
String relayId = client.sendCode("13800138000", "123456");
System.out.println(relayId);
```

## queryRelay

```java
Map<String, Object> relay = client.queryRelay(relayId);
System.out.println(relay);
```

## 错误处理

```java
try {
  client.send(""); // 本地预检失败
} catch (ValidationException e) {
  System.out.println("validation: " + e.getMessage());
} catch (WPushException e) {
  System.out.println("api: " + e.getMessage() + " " + e.getCode() + " " + e.getHttpStatus());
}
```

## 规范与 UA

跨语言契约见 [SPEC.md](../SPEC.md)。

User-Agent：`wpush-java/0.1.0`
