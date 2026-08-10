import { describe, it, expect } from "vitest";
import { isSudo, isAllowed } from "./tool-policy.js";
import type { DittoOperation } from "./openapi.js";

const op = (o: Partial<DittoOperation>): DittoOperation => ({
  operationId: "x", method: "GET", path: "/x", summary: "", description: "", params: [], hasBody: false, securitySchemes: [], ...o,
});
const policy = (p: object) => ({ allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [], ...p });

describe("tool-policy", () => {
  it("allows GET by default, blocks writes", () => {
    expect(isAllowed(op({ method: "GET" }), policy({}))).toBe(true);
    expect(isAllowed(op({ operationId: "putThing", method: "PUT" }), policy({}))).toBe(false);
  });
  it("allows a write only when allowlisted", () => {
    expect(isAllowed(op({ operationId: "putThing", method: "PUT" }), policy({ writeAllowlist: ["putThing"] }))).toBe(true);
  });
  it("treats sudo ops specially: only via sudoAllowlist", () => {
    const s = op({ operationId: "sudoRetrieveThing", method: "GET" });
    expect(isSudo(s)).toBe(true);
    expect(isAllowed(s, policy({}))).toBe(false); // GET but sudo -> not auto-allowed
    expect(isAllowed(s, policy({ sudoAllowlist: ["sudoRetrieveThing"] }))).toBe(true);
  });

  it("treats /devops paths as devops-privileged (sudo-gated)", () => {
    const d = op({ operationId: "getLogging", method: "GET", path: "/devops/logging" });
    expect(isSudo(d)).toBe(true);
    expect(isAllowed(d, policy({}))).toBe(false); // GET but /devops -> not auto-allowed
    expect(isAllowed(d, policy({ sudoAllowlist: ["getLogging"] }))).toBe(true);
  });

  it("treats ops with DevOpsBasic/DevOpsBearer security as sudo", () => {
    const conn = op({
      operationId: "getConnections",
      method: "GET",
      path: "/api/2/connections",
      securitySchemes: ["DevOpsBasic"],
    });
    expect(isSudo(conn)).toBe(true);
    expect(isAllowed(conn, policy({}))).toBe(false); // GET but devops-secured -> not auto-allowed
    expect(isAllowed(conn, policy({ sudoAllowlist: ["getConnections"] }))).toBe(true);
  });

  it("treats ops with DevOpsBearer (case-insensitive) as sudo", () => {
    const op2 = op({ securitySchemes: ["DevOpsBearer"], path: "/api/2/connections/foo" });
    expect(isSudo(op2)).toBe(true);
  });
});
