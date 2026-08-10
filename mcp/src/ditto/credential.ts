import type { AppConfig } from "../config/schema.js";

export interface DittoCredential {
  readonly isDevops: boolean;
  authHeader(signal?: AbortSignal): Promise<string | undefined>;
}

function first(h: string | string[] | undefined): string | undefined {
  return Array.isArray(h) ? h[0] : h;
}

class StaticCredential implements DittoCredential {
  constructor(readonly isDevops: boolean, private readonly header: string | undefined) {}
  async authHeader(): Promise<string | undefined> {
    return this.header;
  }
}

interface OidcOptions {
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  scope?: string;
  isDevops: boolean;
}

export class OidcClientCredential implements DittoCredential {
  readonly isDevops: boolean;
  private token?: string;
  private expiresAt = 0;
  private inflight?: Promise<string>;

  constructor(private readonly opts: OidcOptions, private readonly fetchFn: typeof fetch = fetch) {
    this.isDevops = opts.isDevops;
  }

  async authHeader(signal?: AbortSignal): Promise<string | undefined> {
    const now = Date.now();
    if (this.token && now < this.expiresAt) return `Bearer ${this.token}`;
    if (!this.inflight) this.inflight = this.fetchToken(signal).finally(() => { this.inflight = undefined; });
    const token = await this.inflight;
    return `Bearer ${token}`;
  }

  private async fetchToken(signal?: AbortSignal): Promise<string> {
    const body = new URLSearchParams({ grant_type: "client_credentials" });
    if (this.opts.scope) body.set("scope", this.opts.scope);
    const basic = Buffer.from(`${this.opts.clientId}:${this.opts.clientSecret}`).toString("base64");
    const timeoutSignal = AbortSignal.timeout(15000);
    const combinedSignal = signal ? AbortSignal.any([signal, timeoutSignal]) : timeoutSignal;
    const res = await this.fetchFn(this.opts.tokenUrl, {
      method: "POST",
      headers: { authorization: `Basic ${basic}`, "content-type": "application/x-www-form-urlencoded" },
      body: body.toString(),
      signal: combinedSignal,
    });
    if (!res.ok) throw new Error(`oidc token endpoint ${this.opts.tokenUrl} -> ${res.status}`);
    const json = (await res.json()) as { access_token?: string; expires_in?: number };
    if (!json.access_token) throw new Error("oidc token response missing access_token");
    this.token = json.access_token;
    this.expiresAt = Date.now() + Math.max(0, (json.expires_in ?? 300) - 30) * 1000;
    return this.token;
  }
}

export function createConfigCredential(config: AppConfig, fetchFn: typeof fetch = fetch): DittoCredential {
  const c = config.ditto.credential;
  const isDevops = c.devops ?? c.kind === "devops";
  if (c.kind === "oidc") {
    if (!c.tokenUrl || !c.clientId || !c.clientSecret) {
      process.stderr.write("[ditto-mcp] oidc credential missing tokenUrl/clientId/clientSecret; using no credential\n");
      return new StaticCredential(isDevops, undefined);
    }
    return new OidcClientCredential(
      { tokenUrl: c.tokenUrl, clientId: c.clientId, clientSecret: c.clientSecret, scope: c.scope, isDevops },
      fetchFn,
    );
  }
  if (c.username !== undefined) {
    const header = `Basic ${Buffer.from(`${c.username}:${c.password ?? ""}`).toString("base64")}`;
    return new StaticCredential(isDevops, header);
  }
  return new StaticCredential(false, undefined);
}

export function resolveCredential(
  configCredential: DittoCredential,
  ctx: { headers?: Record<string, string | string[] | undefined> },
): DittoCredential {
  const headers = ctx.headers ?? {};
  const key = Object.keys(headers).find((k) => k.toLowerCase() === "authorization");
  const sessionAuth = first(key ? headers[key] : undefined);
  if (sessionAuth) return new StaticCredential(configCredential.isDevops, sessionAuth);
  return configCredential;
}
