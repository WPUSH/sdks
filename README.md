# WPUSH SDKs

官方 WPUSH 多语言 SDK 单体仓库（monorepo）。

面向 WPUSH 开放 API（`https://api.wpush.cn/api/v1/*`）的官方多语言 SDK。

方法：`send` / `query`，以及 P0.5 中继 API `sendMail` / `sendCode` / `queryRelay`。

## 语言

| 语言 | 包名 | UA |
|---|---|---|
| Python | `wpush` | `wpush-python/0.1.0` |
| TypeScript/Node | `@wpush/sdk` | `wpush-js/0.1.0` |
| Go | `github.com/WPUSH/sdks/go` | `wpush-go/0.1.0` |
| Java | `cn.wpush:wpush-sdk` | `wpush-java/0.1.0` |
| PHP | `wpush/wpush` | `wpush-php/0.1.0` |
| Dart | `wpush` | `wpush-dart/0.1.0` |
| Rust | `wpush-sdk` | `wpush-rust/0.1.0` |

## 规范

跨语言契约与实现规范见 [SPEC.md](./SPEC.md)。

## 快速开始

各语言 README 提供**完整的按方法可复制示例**（`send` / `query` / `sendMail` / `sendCode` / `queryRelay`，以及 option / topic / 错误处理）：

- Python：[python/README.md](./python/README.md)
- TypeScript：[javascript/README.md](./javascript/README.md)
- Go：[go/README.md](./go/README.md)
- Java：[java/README.md](./java/README.md)
- PHP：[php/README.md](./php/README.md)
- Dart：[dart/README.md](./dart/README.md)
- Rust（`wpush-sdk`）：[rust/README.md](./rust/README.md)

```bash
export WPUSH_API_KEY=WPUSH_your_key
```

LICENSE: MIT © 2026 An Hao / WPUSH
