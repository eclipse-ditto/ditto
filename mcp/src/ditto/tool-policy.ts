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

// Allowlist entries may be either the OpenAPI operationId (e.g. "putThing") or a
// "METHOD path" key (e.g. "PUT /api/2/things/{thingId}"). The latter is the stable,
// user-friendly form when the spec omits operationIds (Ditto's does), in which case
// operationId is a synthesized "METHOD_path" fallback.
function matchesAllowlist(op: DittoOperation, list: string[]): boolean {
  return list.includes(op.operationId) || list.includes(`${op.method} ${op.path}`);
}

export function isAllowed(op: DittoOperation, policy: AppConfig["ditto"]["policy"]): boolean {
  if (isSudo(op)) return matchesAllowlist(op, policy.sudoAllowlist);
  return policy.allowMethods.includes(op.method) || matchesAllowlist(op, policy.writeAllowlist);
}
