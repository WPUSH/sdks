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
