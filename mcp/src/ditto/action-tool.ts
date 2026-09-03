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

import { z, type ZodRawShape } from "zod";
import type { ToolDef, ToolResult, RequestCtx } from "../core/types.js";
import type { DittoOperation } from "./openapi.js";
import type { DittoClient } from "./client.js";
import type { DittoCredential } from "./credential.js";
import { resolveCredential } from "./credential.js";
import { isSudo } from "./tool-policy.js";

// Map an operationId to a legal MCP tool name (MCP names must match [A-Za-z0-9_-]).
// Replace illegal chars, collapse runs of "_", and trim the edges. For specs that
// declare no operationId, the id is a synthesized "<METHOD>_<path>" fallback, so
// collapsing keeps names clean: the /api/2/things GET tool becomes "GET_api_2_things"
// rather than "GET__api_2_things".
function sanitizeName(id: string): string {
  return id
    .replace(/[^A-Za-z0-9_]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .slice(0, 64);
}

function inputSchema(op: DittoOperation): ZodRawShape {
  const shape: ZodRawShape = {};
  for (const p of op.params) {
    const base = p.type === "number" ? z.number() : p.type === "boolean" ? z.boolean() : z.string();
    shape[p.name] = (p.required ? base : base.optional()).describe(`${p.in} parameter ${p.name}`);
  }
  if (op.bodySchema?.props.length) {
    const bodyShape: ZodRawShape = {};
    for (const bp of op.bodySchema.props) {
      const base = bp.type === "number" ? z.number()
        : bp.type === "boolean" ? z.boolean()
        : bp.type === "object" ? z.record(z.any())
        : bp.type === "array" ? z.array(z.any())
        : bp.type === "unknown" ? z.any()
        : z.string();
      const desc = `body.${bp.name}` + (bp.required ? " (required)" : "");
      bodyShape[bp.name] = base.optional().describe(desc);
    }
    shape.body = z.object(bodyShape).passthrough().optional().describe("JSON request body");
  } else if (op.hasBody) {
    shape.body = z.any().optional().describe("JSON request body");
  }
  return shape;
}

function text(t: string): ToolResult {
  return { content: [{ type: "text", text: t }] };
}

export interface ToolCredentials {
  standard: DittoCredential;
  devops?: DittoCredential;
}

export function operationToTool(op: DittoOperation, client: DittoClient, creds: ToolCredentials): ToolDef {
  const sudo = isSudo(op);
  return {
    name: sanitizeName(op.operationId),
    description:
      `${op.method} ${op.path} — ${op.description || op.summary}` +
      (sudo ? " [sudo — requires a devops credential]" : ""),
    inputSchema: inputSchema(op),
    handler: async (args: unknown, ctx: RequestCtx): Promise<ToolResult> => {
      // Non-sudo ops always have `standard`; sudo ops require the `devops` slot.
      const base = sudo ? creds.devops : creds.standard;
      if (!base) {
        return text(`Refused: "${op.operationId}" is a sudo operation and requires ditto.devopsCredential.`);
      }
      const credential = resolveCredential(base, { headers: ctx.headers });
      const res = await client.execute(op, (args ?? {}) as Record<string, unknown>, credential, ctx.signal);
      return text(`HTTP ${res.status}\n${res.body}`);
    },
  };
}
