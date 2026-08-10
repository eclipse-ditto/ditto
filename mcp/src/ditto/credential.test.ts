import { describe, it, expect, afterEach } from "vitest";
import { AppConfigSchema } from "../config/schema.js";
import { createConfigCredential, resolveCredential } from "./credential.js";
import { startFakeOidc } from "./fake-oidc.js";

const cfg = (ditto: object) => AppConfigSchema.parse({ ditto: { enabled: true, ...ditto } });

describe("credentials", () => {
  it("config basic → async Basic header", async () => {
    const c = createConfigCredential(cfg({ credential: { kind: "basic", username: "u", password: "p" } }));
    expect(await c.authHeader()).toBe(`Basic ${Buffer.from("u:p").toString("base64")}`);
    expect(c.isDevops).toBe(false);
  });

  it("devops flag marks isDevops regardless of kind", async () => {
    expect(createConfigCredential(cfg({ credential: { kind: "devops", username: "d", password: "s" } })).isDevops).toBe(true);
    expect(createConfigCredential(cfg({ credential: { kind: "oidc", tokenUrl: "x", clientId: "c", clientSecret: "s", devops: true } })).isDevops).toBe(true);
  });

  it("session Authorization overrides config, inherits config isDevops", async () => {
    const cc = createConfigCredential(cfg({ credential: { kind: "devops", username: "d", password: "s" } }));
    const c = resolveCredential(cc, { headers: { Authorization: "Bearer sess" } });
    expect(await c.authHeader()).toBe("Bearer sess");
    expect(c.isDevops).toBe(true);
  });

  it("oidc fetches a bearer token and caches it (one token call for two uses)", async () => {
    const oidc = await startFakeOidc({ access_token: "tok123", expires_in: 3600 });
    try {
      const c = createConfigCredential(cfg({ credential: { kind: "oidc", tokenUrl: oidc.tokenUrl, clientId: "c", clientSecret: "s" } }));
      expect(await c.authHeader()).toBe("Bearer tok123");
      expect(await c.authHeader()).toBe("Bearer tok123");
      expect(oidc.calls).toBe(1); // cached
    } finally { await oidc.stop(); }
  });

  it("oidc sends grant_type=client_credentials, Basic auth, and scope (when set)", async () => {
    const oidc = await startFakeOidc({ access_token: "tok", expires_in: 3600 });
    try {
      const c = createConfigCredential(cfg({ credential: { kind: "oidc", tokenUrl: oidc.tokenUrl, clientId: "myClient", clientSecret: "mySecret", scope: "scope1 scope2" } }));
      await c.authHeader();
      expect(oidc.requests).toHaveLength(1);
      const req = oidc.requests[0];
      expect(req.method).toBe("POST");
      expect(req.headers.authorization).toBe(`Basic ${Buffer.from("myClient:mySecret").toString("base64")}`);
      expect(req.headers["content-type"]).toBe("application/x-www-form-urlencoded");
      expect(req.body).toContain("grant_type=client_credentials");
      expect(req.body).toContain("scope=scope1+scope2");
    } finally { await oidc.stop(); }
  });
});
