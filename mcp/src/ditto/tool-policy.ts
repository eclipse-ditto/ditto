import type { AppConfig } from "../config/schema.js";
import type { DittoOperation } from "./openapi.js";

export function isSudo(op: DittoOperation): boolean {
  const p = op.path.toLowerCase();
  const hasDevopsSecurity = op.securitySchemes.some((s) => s.toLowerCase().includes("devops"));
  return (
    hasDevopsSecurity ||
    op.operationId.toLowerCase().startsWith("sudo") ||
    p.includes("/sudo") ||
    p.startsWith("/devops") ||
    p.includes("/connections") // connectivity is secret-bearing → always devops
  );
}

export function isAllowed(op: DittoOperation, policy: AppConfig["ditto"]["policy"]): boolean {
  if (isSudo(op)) return policy.sudoAllowlist.includes(op.operationId);
  return policy.allowMethods.includes(op.method) || policy.writeAllowlist.includes(op.operationId);
}
