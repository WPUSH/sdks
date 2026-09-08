import { ValidationError, WPushError } from "./errors.js";

export const USER_AGENT = "wpush-js/0.1.0";
const DEFAULT_BASE_URL = "https://api.wpush.cn";

export type ChannelInput = string | string[];

export interface ClientOptions {
  apiKey?: string;
  baseUrl?: string;
  timeoutMs?: number;
  defaultChannel?: ChannelInput;
  fetch?: typeof fetch;
}

export interface SendParams {
  title: string;
  content?: string;
  channel?: ChannelInput;
  option?: string;
  topicCode?: string;
  url?: string;
  idempotencyKey?: string;
}

function joinChannel(ch?: ChannelInput): string | undefined {
  if (ch === undefined) return undefined;
  return Array.isArray(ch) ? ch.join(",") : ch;
}

function isSuccessCode(code: unknown): boolean {
  return typeof code === "number" && code === 0;
}

export class Client {
  readonly apiKey: string;
  readonly baseUrl: string;
  readonly timeoutMs: number;
  readonly defaultChannel?: ChannelInput;
  private readonly fetchImpl: typeof fetch;

  constructor(opts: ClientOptions = {}) {
    const key = opts.apiKey ?? process.env.WPUSH_API_KEY;
    if (!key) throw new ValidationError("api_key is required (or set WPUSH_API_KEY)");
    this.apiKey = key;
    this.baseUrl = (opts.baseUrl ?? DEFAULT_BASE_URL).replace(/\/$/, "");
    this.timeoutMs = opts.timeoutMs ?? 30_000;
    this.defaultChannel = opts.defaultChannel;
    this.fetchImpl = opts.fetch ?? fetch;
  }

  private async post(path: string, payload: Record<string, unknown>, idempotencyKey?: string): Promise<unknown> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
      Accept: "application/json",
      "User-Agent": USER_AGENT,
      "X-API-Key": this.apiKey,
    };
    if (idempotencyKey) headers["X-Idempotency-Key"] = idempotencyKey;
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), this.timeoutMs);
    let res: Response;
    try {
      res = await this.fetchImpl(this.baseUrl + path, {
        method: "POST",
        headers,
        body: JSON.stringify(payload),
        signal: ctrl.signal,
      });
    } finally {
      clearTimeout(t);
    }
    const text = await res.text();
    let body: any;
    try {
      body = text ? JSON.parse(text) : {};
    } catch {
      throw new WPushError("invalid JSON response", { httpStatus: res.status, data: text });
    }
    const code = body?.code;
    if (!isSuccessCode(code)) {
      const errCode = typeof code === "number" ? code : undefined;
      throw new WPushError(String(body?.message ?? "request failed"), {
        code: errCode,
        httpStatus: res.status,
        data: body?.data,
      });
    }
    return body.data;
  }

  async send(params: SendParams): Promise<string> {
    if (!params.title) throw new ValidationError("title is required");
    if (params.option && params.topicCode) {
      throw new ValidationError("option and topic_code cannot be used together");
    }
    const channel = joinChannel(params.channel ?? this.defaultChannel);
    const payload: Record<string, unknown> = { apikey: this.apiKey, title: params.title };
    if (params.content !== undefined) payload.content = params.content;
    if (channel !== undefined) payload.channel = channel;
    if (params.option) payload.option = params.option;
    if (params.topicCode) payload.topic_code = params.topicCode;
    if (params.url) payload.url = params.url;
    const data = await this.post("/api/v1/send", payload, params.idempotencyKey);
    return data === null || data === undefined ? "" : String(data);
  }

  async query(messageId: string, idempotencyKey?: string): Promise<unknown> {
    if (!messageId) throw new ValidationError("message_id is required");
    return this.post("/api/v1/query", { apikey: this.apiKey, id: String(messageId) }, idempotencyKey);
  }
}
