# @wpush/sdk

Official WPUSH TypeScript/Node SDK.

```bash
npm install @wpush/sdk
export WPUSH_API_KEY=your_key
```

```ts
import { Client } from "@wpush/sdk";
const c = new Client();
const id = await c.send({ title: "t", content: "c", channel: "wechat" });
// also: sendMail / sendCode / queryRelay
```

UA: `wpush-js/0.1.0`. See root SPEC.md.
