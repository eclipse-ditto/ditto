import { describe, it, expect } from "vitest";
import { AppConfigSchema } from "../config/schema.js";
import { createConfigCredential, resolveCredential } from "./credential.js";
import { startFakeOidc } from "./fake-oidc.js";

const cred = (c: object) =>
  AppConfigSchema.parse({ ditto: { enabled: true, credential: c } }).ditto.credential;

describe("credentials", () => {
  it("basic → Basic header", async () => {
    const c = createConfigCredential(cred({ kind: "basic", username: "u", password: "p" }));
    expect(await c.authHeader()).toBe(`Basic ${Buffer.from("u:p").toString("base64")}`);
  });

  it("basic with no username → no header", async () => {
    const c = createConfigCredential(cred({ kind: "basic" }));
    expect(await c.authHeader()).toBeUndefined();
  });

  it("session Authorization overrides the base credential", async () => {
    const base = createConfigCredential(cred({ kind: "basic", username: "u", password: "p" }));
    const c = resolveCredential(base, { headers: { Authorization: "Bearer sess" } });
    expect(await c.authHeader()).toBe("Bearer sess");
  });

  it("resolveCredential returns the base when no session header", async () => {
    const base = createConfigCredential(cred({ kind: "basic", username: "u", password: "p" }));
    const c = resolveCredential(base, { headers: {} });
    expect(await c.authHeader()).toBe(`Basic ${Buffer.from("u:p").toString("base64")}`);
  });

  it("oidc fetches a bearer token and caches it (one token call for two uses)", async () => {
    const oidc = await startFakeOidc({ access_token: "tok123", expires_in: 3600 });
    try {
      const c = createConfigCredential(cred({ kind: "oidc", tokenUrl: oidc.tokenUrl, clientId: "c", clientSecret: "s" }));
      expect(await c.authHeader()).toBe("Bearer tok123");
      expect(await c.authHeader()).toBe("Bearer tok123");
      expect(oidc.calls).toBe(1);
    } finally { await oidc.stop(); }
  });

  it("oidc sends grant_type=client_credentials, Basic auth, and scope (when set)", async () => {
    const oidc = await startFakeOidc({ access_token: "tok", expires_in: 3600 });
    try {
      const c = createConfigCredential(cred({ kind: "oidc", tokenUrl: oidc.tokenUrl, clientId: "myClient", clientSecret: "mySecret", scope: "scope1 scope2" }));
      await c.authHeader();
      const req = oidc.requests[0];
      expect(req.method).toBe("POST");
      expect(req.headers.authorization).toBe(`Basic ${Buffer.from("myClient:mySecret").toString("base64")}`);
      expect(req.headers["content-type"]).toBe("application/x-www-form-urlencoded");
      expect(req.body).toContain("grant_type=client_credentials");
      expect(req.body).toContain("scope=scope1+scope2");
    } finally { await oidc.stop(); }
  });
});
