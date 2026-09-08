export class WPushError extends Error {
  readonly code?: number;
  readonly httpStatus?: number;
  readonly data?: unknown;
  constructor(message: string, opts: { code?: number; httpStatus?: number; data?: unknown } = {}) {
    super(message);
    this.name = "WPushError";
    this.code = opts.code;
    this.httpStatus = opts.httpStatus;
    this.data = opts.data;
  }
}
export class ValidationError extends WPushError {
  constructor(message: string) {
    super(message);
    this.name = "ValidationError";
  }
}
