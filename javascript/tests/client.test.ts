import { describe, it, expect, vi } from "vitest";
import { Client, ValidationError, WPushError, USER_AGENT } from "../src/index.js";

function mockFetch(handler: (url: string, init?: RequestInit) => { status?: number; body: unknown }) {
  return vi.fn(async (url: string | URL, init?: RequestInit) => {
    const r = handler(String(url), init);
    return new Response(JSON.stringify(r.body), {
      status: r.status ?? 200,
      headers: { "Content-Type": "application/json" },
    });
  });
}

describe("Client", () => {
  it("send success joins channel and sets headers", async () => {
    const fetchImpl = mockFetch((url, init) => {
      expect(url.endsWith("/api/v1/send")).toBe(true);
      const h = init?.headers as Record<string, string>;
      expect(h["User-Agent"]).toBe(USER_AGENT);
      expect(h["X-API-Key"]).toBe("k");
      const body = JSON.parse(String(init?.body));
      expect(body.apikey).toBe("k");
      expect(body.channel).toBe("wechat,dingtalk");
      return { body: { code: 0, message: "success", data: "12345" } };
    });
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    const id = await c.send({ title: "t", content: "c", channel: ["wechat", "dingtalk"] });
    expect(id).toBe("12345");
  });

  it("stringifies numeric data", async () => {
    const fetchImpl = mockFetch(() => ({ body: { code: 0, message: "ok", data: 999 } }));
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    expect(await c.send({ title: "t" })).toBe("999");
  });

  it("rejects bool code", async () => {
    const fetchImpl = mockFetch(() => ({ body: { code: false, message: "nope", data: null } }));
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    await expect(c.send({ title: "t" })).rejects.toBeInstanceOf(WPushError);
  });
});

describe("validation", () => {
  it("option and topicCode conflict", async () => {
    const c = new Client({ apiKey: "k", fetch: vi.fn() as unknown as typeof fetch });
    await expect(c.send({ title: "t", option: "ops", topicCode: "x" })).rejects.toBeInstanceOf(ValidationError);
  });

  it("missing api key", () => {
    const prev = process.env.WPUSH_API_KEY;
    delete process.env.WPUSH_API_KEY;
    expect(() => new Client()).toThrow(ValidationError);
    if (prev !== undefined) process.env.WPUSH_API_KEY = prev;
  });
});

describe("query", () => {
  it("query success", async () => {
    const fetchImpl = mockFetch((url, init) => {
      expect(url.endsWith("/api/v1/query")).toBe(true);
      const body = JSON.parse(String(init?.body));
      expect(body.id).toBe("123");
      return { body: { code: 0, message: "success", data: { id: "123", status: 1 } } };
    });
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    const out = (await c.query("123")) as { id: string; status: number };
    expect(out.id).toBe("123");
    expect(out.status).toBe(1);
  });

  it("api 401", async () => {
    const fetchImpl = mockFetch(() => ({ body: { code: 401, message: "bad", data: null } }));
    const c = new Client({ apiKey: "bad", fetch: fetchImpl as unknown as typeof fetch });
    try {
      await c.send({ title: "t" });
      expect.fail("should throw");
    } catch (e) {
      expect(e).toBeInstanceOf(WPushError);
      expect((e as WPushError).code).toBe(401);
    }
  });
});

describe("relay", () => {
  it("sendMail success", async () => {
    const fetchImpl = mockFetch((url, init) => {
      expect(url.endsWith("/api/v1/send_mail")).toBe(true);
      const body = JSON.parse(String(init?.body));
      expect(body.to).toBe("a@b.com");
      expect(body.title).toBe("hi");
      expect(body.content).toBe("body");
      expect(body.apikey).toBe("k");
      return { body: { code: 0, message: "success", data: "mail-1" } };
    });
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    expect(await c.sendMail({ to: "a@b.com", title: "hi", content: "body" })).toBe("mail-1");
  });

  it("sendCode success", async () => {
    const fetchImpl = mockFetch((url, init) => {
      expect(url.endsWith("/api/v1/send_code")).toBe(true);
      const body = JSON.parse(String(init?.body));
      expect(body.phone).toBe("13800138000");
      expect(body.code).toBe("123456");
      return { body: { code: 0, message: "success", data: "code-1" } };
    });
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    expect(await c.sendCode({ phone: "13800138000", code: "123456" })).toBe("code-1");
  });

  it("sendCode bad phone", async () => {
    const c = new Client({ apiKey: "k", fetch: vi.fn() as unknown as typeof fetch });
    await expect(c.sendCode({ phone: "123", code: "123456" })).rejects.toBeInstanceOf(ValidationError);
  });

  it("queryRelay success", async () => {
    const fetchImpl = mockFetch((url, init) => {
      expect(url.endsWith("/api/v1/query_relay")).toBe(true);
      const body = JSON.parse(String(init?.body));
      expect(body.id).toBe("r1");
      return { body: { code: 0, message: "success", data: { id: "r1", status: 1 } } };
    });
    const c = new Client({ apiKey: "k", fetch: fetchImpl as unknown as typeof fetch });
    const out = (await c.queryRelay("r1")) as { id: string; status: number };
    expect(out.id).toBe("r1");
    expect(out.status).toBe(1);
  });
});
