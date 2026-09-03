/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { CredentialConfig } from "../config/schema.js";

export interface DittoCredential {
  authHeader(signal?: AbortSignal): Promise<string | undefined>;
}

function first(h: string | string[] | undefined): string | undefined {
  return Array.isArray(h) ? h[0] : h;
}

class StaticCredential implements DittoCredential {
  constructor(private readonly header: string | undefined) {}
  async authHeader(): Promise<string | undefined> {
    return this.header;
  }
}

interface OidcOptions {
  tokenUrl: string;
  clientId: string;
  clientSecret: string;
  scope?: string;
}

export class OidcClientCredential implements DittoCredential {
  private token?: string;
  private expiresAt = 0;
  private inflight?: Promise<string>;

  constructor(private readonly opts: OidcOptions, private readonly fetchFn: typeof fetch = fetch) {}

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

/** Build a credential from a single credential-config object (standard or devops slot). */
export function createConfigCredential(c: CredentialConfig, fetchFn: typeof fetch = fetch): DittoCredential {
  if (c.kind === "oidc") {
    if (!c.tokenUrl || !c.clientId || !c.clientSecret) {
      process.stderr.write("[ditto-mcp] oidc credential missing tokenUrl/clientId/clientSecret; using no credential\n");
      return new StaticCredential(undefined);
    }
    return new OidcClientCredential(
      { tokenUrl: c.tokenUrl, clientId: c.clientId, clientSecret: c.clientSecret, scope: c.scope },
      fetchFn,
    );
  }
  if (c.username !== undefined) {
    return new StaticCredential(`Basic ${Buffer.from(`${c.username}:${c.password ?? ""}`).toString("base64")}`);
  }
  return new StaticCredential(undefined);
}

/** A per-session `Authorization` header, when present, replaces the base credential. */
export function resolveCredential(
  base: DittoCredential,
  ctx: { headers?: Record<string, string | string[] | undefined> },
): DittoCredential {
  const headers = ctx.headers ?? {};
  const key = Object.keys(headers).find((k) => k.toLowerCase() === "authorization");
  const sessionAuth = first(key ? headers[key] : undefined);
  if (sessionAuth) return new StaticCredential(sessionAuth);
  return base;
}
