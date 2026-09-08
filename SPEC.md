# WPUSH Official SDK Spec（v0.1 定稿）

> 状态：**已锁定**（2026-09-08）。实现与评审以此为准。
>
> 范围：仅封装开放 API `https://api.wpush.cn/api/v1/*`。不封装 `/v2`、MCP、OAuth。

## 已锁定决策（Decisions Locked）

| 项 | 决定 |
|---|---|
| 仓库 | monorepo `WPUSH/sdks` |
| P0 语言 | Python、TypeScript/Node、Go |
| P1 语言 | Java、PHP、Dart |
| LICENSE | MIT（Copyright An Hao / WPUSH, 2026） |
| option 文案 | 明确多实例（webhook/dingtalk/feishu/wechat_work）+ qqbot 群编码 |
| API 基址 | `https://api.wpush.cn` |
| 鉴权 | Header `X-API-Key` + Body `apikey` 双写 |
| 成功判定 | 信封 `code` 必须为 number 且 `=== 0`；bool 一律失败 |

---

## 0. 设计原则

1. **薄封装**：鉴权 + 请求构造 + 信封解析 + 类型化错误；重试可配置，默认关闭。
2. **跨语言同构**：同一概念一致；命名按语言习惯（见 §8 Naming）。
3. **安全默认**：不日志完整 apikey；支持环境变量 `WPUSH_API_KEY`；HTTPS only。
4. **契约诚实**：HTTP 200 ≠ 成功；必须以信封 `code === 0`（number）判定；JSON `code: false`/`true` 一律失败。
5. **幂等优先**：文档与示例默认可带 `X-Idempotency-Key`。
6. **零意外依赖**：Python MVP 优先 stdlib；JS 依赖 Node 18+ fetch；Go 仅标准库。


## 1. 仓库布局（Repo Layout）

```
sdks/
  SPEC.md
  LICENSE
  README.md
  .gitignore
  python/          # package: wpush
  javascript/      # package: @wpush/sdk
  go/              # module: github.com/WPUSH/sdks/go
  java/            # Maven: cn.wpush:wpush-sdk
  php/             # Composer: wpush/wpush
  dart/            # package: wpush
```

### 1.1 包名 / Module

| 语言 | 包名 | User-Agent | 状态 |
|---|---|---|---|
| Python | `wpush` | `wpush-python/{ver}` | 已实现 |
| TypeScript | `@wpush/sdk` | `wpush-js/{ver}` | 已实现 |
| Go | `github.com/WPUSH/sdks/go`（package `wpush`） | `wpush-go/{ver}` | 已实现 |
| Java | `cn.wpush:wpush-sdk`（package `cn.wpush.sdk`） | `wpush-java/{ver}` | 已实现 |
| PHP | `wpush/wpush`（namespace `WPush\`） | `wpush-php/{ver}` | 已实现 |
| Dart | `wpush` | `wpush-dart/{ver}` | 已实现（P1） |

MVP 版本：`0.1.0`。

---

## 2. 鉴权与公共请求头（Auth）

所有 POST JSON 请求必须包含：

| Header | 必填 | 说明 |
|---|---|---|
| `Content-Type` | 是 | `application/json` |
| `Accept` | 建议 | `application/json` |
| `User-Agent` | 是 | `wpush-{lang}/{ver}` |
| `X-API-Key` | 是 | API Key |
| `X-Idempotency-Key` | 否 | 幂等键，建议 UUID |

Body 中**同时**携带 `apikey` 字段（与 Header 同值）。原因：兼容历史表单/文档示例与网关双读。

环境变量：`WPUSH_API_KEY`。构造 Client 时若未显式传 key，则读取该变量；皆空则抛 Validation 错误。


## 3. 信封规则（Envelope）

响应 JSON 形如：

```json
{ "code": 0, "message": "success", "data": ... }
```

### 3.1 成功

- `code` 的 JSON 类型必须是 **number**，且值等于 `0`。
- 禁止把 bool 当作成功：`code: false` / `code: true` → 失败。
- 语言注意：
  - Python：`type(code) is int and code == 0`（bool 是 int 子类）。
  - JS/TS：`typeof code === "number" && code === 0`。
  - Go：`json.Unmarshal` 到 `any` 后，number 为 `float64`；bool 为 `bool`，不可当成功。
  - Java：`Number` 且值为 0；`Boolean` 一律失败。
  - PHP：`is_int`/`is_float` 且 `=== 0`；`is_bool` 一律失败。
  - Dart：`code is num && code == 0`；`bool` 一律失败。

### 3.2 失败

- 非成功 code → 抛 `WPushError`，附带 `code`（若可解析为 int）、`message`、`http_status`、`data`。
- HTTP 非 2xx 但仍有 JSON 信封时，优先按信封 code 解释；否则以 HTTP 状态构造错误。
- 非法 JSON → `WPushError`（invalid JSON response）。

### 3.3 data 约定

- `send` / `sendCode` / `sendMail`：`data` 为消息 id；若服务端返回 number，客户端 **stringify** 后返回 string。
- `query` / `queryRelay`：`data` 为对象，原样解码返回。

---

## 4. ClientOptions

跨语言同构字段：

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| apiKey | string | env `WPUSH_API_KEY` | 必填（显式或环境） |
| baseUrl | string | `https://api.wpush.cn` | 可测性注入 |
| timeout | duration | 30s | 请求超时 |
| defaultChannel | string\|string[] | 空 | send 未指定 channel 时使用 |

语言特有：

- Python：`timeout: float` 秒。
- JS：`timeoutMs: number`；可注入 `fetch`。
- Go：`HTTPClient *http.Client`；`Timeout time.Duration`。
- Java：`Duration timeout`；可注入 `HttpTransport`。
- PHP：`float $timeout`；可注入 `HttpTransport`。
- Dart：`Duration timeout`；可注入 `http.Client`。


## 5. API 方法

Base path：`{baseUrl}/api/v1`。一律 **POST** + JSON。

### 5.1 send — `POST /api/v1/send`

| 字段 | 必填 | 说明 |
|---|---|---|
| apikey | 是 | 与 Header 同值 |
| title | 是 | 标题 |
| content | 否 | 正文（多数渠道支持 Markdown） |
| channel | 否 | 字符串或数组；数组 → **按顺序**逗号拼接 |
| option | 否 | 多实例/群编码；**不可与 topic_code 同用** |
| topic_code | 否 | 主题广播编码 |
| url | 否 | 附加链接 |

返回：`string` 消息 id。

客户端预检：`option` 与 `topic_code` 同时非空 → `ValidationError`（不发请求）。

### 5.2 query — `POST /api/v1/query`

| 字段 | 必填 |
|---|---|
| apikey | 是 |
| id | 是（消息 id） |

返回：`data` 对象，常见字段：`id, title, status, point, created_at, updated_at, channel, type, ip`。

status：`0` 发送中 / `1` 成功 / `2` 失败。

### 5.3 sendMail — `POST /api/v1/send_mail`（P0.5 已实现）

| 字段 | 必填 | 说明 |
|---|---|---|
| apikey | 是 | 与 Header 同值 |
| to | 是 | 收件邮箱 |
| title | 是 | 标题 |
| content | 否 | 正文（有 title 即可） |

可选 Header `X-Idempotency-Key`。返回：relay id string（data 同 send，stringify）。

### 5.4 sendCode — `POST /api/v1/send_code`（P0.5 已实现）

| 字段 | 必填 |
|---|---|
| apikey | 是 |
| phone | 是 |
| code | 是（验证码内容） |

客户端软校验：`phone` 匹配 `^(\+?86)?1\d{10}$`，`code` 匹配 `^[A-Za-z0-9]{1,6}$`，否则 `ValidationError`。
可选 `X-Idempotency-Key`。返回：relay id string。

### 5.5 queryRelay — `POST /api/v1/query_relay`（P0.5 已实现）

| 字段 | 必填 |
|---|---|
| apikey | 是 |
| id | 是（relay id） |

返回：`data` 对象（同 query）。


## 6. option 语义（多实例）

`option` 表示**渠道内实例编码**，不是渠道名本身。

适用：

| 场景 | 含义 |
|---|---|
| webhook / dingtalk / feishu / wechat_work | 多机器人/多 Webhook 实例编码 |
| qqbot | QQ 群编码（group code） |

行为：

1. 不带 `option`：投该渠道的**默认**实例。
2. `option=编码`：投指定实例，例如 `channel=feishu` + `option=ops`。
3. 多渠道同编码：`channel=dingtalk,feishu` + `option=ops` → 分别投两边编码为 `ops` 的实例。
4. 与 `topic_code` **互斥**；客户端应 Validation 拒绝。
5. 编码不存在时服务端通常返回 `422`。

文档示例必须同时给出「默认实例」与「option=ops」两种用法。

---

## 7. channels 枚举

| channel | 免费 | 说明 |
|---|---|---|
| wechat | 是 | 微信公众号模板消息（默认） |
| webhook | 是 | 自定义 Webhook |
| feishu | 是 | 飞书机器人 |
| dingtalk | 是 | 钉钉机器人 |
| wechat_work | 是 | 企业微信机器人 |
| mail | 否 | 邮件（约 0.3 积分） |
| sms | 否 | 短信 |
| app | 是 | 官方 APP 推送 |
| qqbot | 视产品 | QQ 机器人（配合 option 群编码） |
| clawbot | 视产品 | 扩展渠道 |

未知 channel：客户端**不强制拦截**（便于向前兼容），由服务端校验。

数组入参 → 逗号拼接，**保留顺序**，不去重（除非某语言文档另注）。


## 8. 错误码（WPushError codes）

服务端信封 `code`（整数）常见值：

| code | 含义 |
|---|---|
| 0 | 成功 |
| 401 | apikey 错误 |
| 404 | 资源不存在 |
| 422 | 请求参数错误 |
| 429 | 请求频率太快 |
| 500 | 系统错误 |
| 10001 | 主题不存在 |
| 10002 | 积分不足 |
| 10003 | 主题没有订阅用户 |
| 10004 | 消息不存在 |
| 10005 | 预留 / 通道未绑定（以实现为准） |
| 10006 | 预留 / 实例 option 无效（以实现为准） |

客户端：

- `WPushError`：服务端或传输错误，含 `code`/`message`/`http_status`/`data`。
- `ValidationError`（可继承 WPushError）：本地预检失败，**无** HTTP 往返。

禁止把 HTTP status 与信封 code 混为一谈；对外属性名统一为 `code` = 信封码。

---

## 9. 命名映射（Naming Map）

| 概念 | Python | TypeScript | Go | Java | PHP | Dart |
|---|---|---|---|---|---|---|
| 客户端 | `Client` | `Client` | `Client` | `Client` | `Client` | `Client` |
| 构造 | `Client(api_key=...)` | `new Client({ apiKey })` | `NewClient(Options{...})` | `Client.builder().apiKey(...)` | `new Client($apiKey)` | `Client(apiKey: ...)` |
| 发送 | `send(...)` | `send({...})` | `Send(SendParams{...})` | `send(...)` | `send(...)` | `send(...)` |
| 邮件 | `send_mail(...)` | `sendMail({...})` | `SendMail(...)` | `sendMail(...)` | `sendMail(...)` | `sendMail(...)` |
| 验证码 | `send_code(...)` | `sendCode({...})` | `SendCode(...)` | `sendCode(...)` | `sendCode(...)` | `sendCode(...)` |
| 查询 | `query(id)` | `query(id)` | `Query(id, idem)` | `query(id)` | `query($id)` | `query(id)` |
| 中继查询 | `query_relay(id)` | `queryRelay(id)` | `QueryRelay(id, idem)` | `queryRelay(id)` | `queryRelay($id)` | `queryRelay(id)` |
| 主题 | `topic_code=` | `topicCode` | `TopicCode` | `topicCode` | `$topicCode` | `topicCode` |
| 幂等 | `idempotency_key=` | `idempotencyKey` | `IdempotencyKey` | `idempotencyKey` | `$idempotencyKey` | `idempotencyKey` |
| 错误 | `WPushError` | `WPushError` | `*Error` / `*ValidationError` | `WPushException` / `ValidationException` | `WPushException` / `ValidationException` | `WPushException` / `ValidationException` |
| 版本常量 | `__version__` | package.json | 模块注释 / const | `pom.xml` / `USER_AGENT` | `composer.json` / `USER_AGENT` | `pubspec.yaml` / `kUserAgent` |

JSON wire 字段一律 **snake_case**：`topic_code`、`apikey`。

---

## 10. 测试要求

每语言 MVP 必须：

1. Mock HTTP（Python：`urllib` mock；Go：`httptest`；JS：注入 `fetch` / vitest）。
2. 覆盖：send 成功、channel 数组拼接、numeric data stringify、bool code 拒绝、401、option+topic 冲突、query 成功、UA 与 `X-API-Key`。
3. 不打真实网络；不打印真实 API Key。
4. CI 友好：`pytest` / `vitest run` / `go test` / `mvn test` / `composer test` / `dart test`。

---

## 11. 版本与 SemVer

- 遵循 SemVer 2.0。
- `0.x`：允许破坏性调整，但需在 CHANGELOG 标明。
- UA 中的 `{ver}` 与包版本一致。
- 破坏性：删除方法、改变成功判定、改变 channel 拼接规则 → major（或 0.x minor 注明 breaking）。

---

## 12. Non-goals（明确不做）

- 不封装 MCP、OAuth、控制台私有 API、`/v2`。
- 不做渠道侧签名（钉钉加签等由服务端/控制台配置完成）。
- 不做本地消息队列 / 持久化重试存储。
- 不在 SDK 内嵌入真实密钥或示例密钥。
- 不保证短信/邮件投递至第三方收件箱（仅保证 API 受理语义）。

---

## 13. 实现检查清单（MVP / P0.5）

- [x] Python：`wpush` Client/send/query/errors + pytest
- [x] TypeScript：`@wpush/sdk` Client/send/query/errors + vitest
- [x] Go：`wpush` Client/Send/Query/Error + httptest
- [x] P0.5：`sendMail` / `sendCode` / `queryRelay`（Python、TS、Go）+ 单测
- [x] Java：`cn.wpush:wpush-sdk` Client/send/query/sendMail/sendCode/queryRelay + JUnit 5（已实现）
- [x] PHP：`wpush/wpush` Client + PHPUnit（已实现）
- [x] Dart：`wpush` Client + `dart test` / MockClient（P1，已实现）
- [x] LICENSE MIT
- [x] SPEC / README / .gitignore

---

## 附录 A. 最小 curl 对照

```bash
curl -X POST "https://api.wpush.cn/api/v1/send" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $WPUSH_API_KEY" \
  -H "User-Agent: wpush-curl/0.1.0" \
  -d "{\"apikey\":\"$WPUSH_API_KEY\",\"title\":\"hello\",\"content\":\"world\",\"channel\":\"wechat\"}"
```

成功：响应 JSON 中 `code` 为数字 `0`，`data` 为消息 id。

---

*End of SPEC v0.1 — locked 2026-09-08*
