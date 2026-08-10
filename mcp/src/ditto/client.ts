import type { DittoOperation } from "./openapi.js";
import type { DittoCredential } from "./credential.js";

export interface DittoResponse {
  status: number;
  body: string;
}

export interface DittoClient {
  execute(
    op: DittoOperation,
    args: Record<string, unknown>,
    credential: DittoCredential,
    signal?: AbortSignal,
  ): Promise<DittoResponse>;
}

export class HttpDittoClient implements DittoClient {
  constructor(
    private readonly baseUrl: string,
    private readonly fetchFn: typeof fetch = fetch,
  ) {}

  async execute(
    op: DittoOperation,
    args: Record<string, unknown>,
    credential: DittoCredential,
    signal?: AbortSignal,
  ): Promise<DittoResponse> {
    let path = op.path;
    const query = new URLSearchParams();
    for (const p of op.params) {
      const v = args[p.name];
      if (p.in === "path") {
        path = path.replace(`{${p.name}}`, encodeURIComponent(String(v ?? "")).replace(/%3A/g, ":"));
      } else if (v !== undefined) {
        query.set(p.name, String(v));
      }
    }
    const qs = query.toString();
    const url = `${this.baseUrl}${path}${qs ? `?${qs}` : ""}`;

    const headers: Record<string, string> = {};
    const auth = await credential.authHeader(signal);
    if (auth) headers["authorization"] = auth;
    let body: string | undefined;
    if (op.hasBody && args.body !== undefined) {
      headers["content-type"] = "application/json";
      body = JSON.stringify(args.body);
    }

    const res = await this.fetchFn(url, { method: op.method, headers, body, signal });
    return { status: res.status, body: await res.text() };
  }
}
