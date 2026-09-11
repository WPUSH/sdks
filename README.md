# WPUSH SDKs

官方 WPUSH 多语言 SDK 单体仓库（monorepo）。

Official multi-language SDKs for the WPUSH open API (`https://api.wpush.cn/api/v1/*`).

Methods: `send` / `query` plus P0.5 relay APIs `sendMail` / `sendCode` / `queryRelay`.

## Languages / 语言

| Language | Package | UA |
|---|---|---|
| Python | `wpush` | `wpush-python/0.1.0` |
| TypeScript/Node | `@wpush/sdk` | `wpush-js/0.1.0` |
| Go | `github.com/WPUSH/sdks/go` | `wpush-go/0.1.0` |
| Java | `cn.wpush:wpush-sdk` | `wpush-java/0.1.0` |
| PHP | `wpush/wpush` | `wpush-php/0.1.0` |
| Dart | `wpush` | `wpush-dart/0.1.0` |
| Rust | `wpush-sdk` | `wpush-rust/0.1.0` |

## Spec

跨语言契约与实现规范见 [SPEC.md](./SPEC.md)。

## Quick start

Each language README has **full per-method copy-paste examples** (`send` / `query` / `sendMail` / `sendCode` / `queryRelay`, plus option / topic / errors):

- Python: [python/README.md](./python/README.md)
- TypeScript: [javascript/README.md](./javascript/README.md)
- Go: [go/README.md](./go/README.md)
- Java: [java/README.md](./java/README.md)
- PHP: [php/README.md](./php/README.md)
- Dart: [dart/README.md](./dart/README.md)
- Rust (`wpush-sdk`): [rust/README.md](./rust/README.md)

```bash
export WPUSH_API_KEY=WPUSH_your_key
```

LICENSE: MIT © 2026 An Hao / WPUSH
