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

import { describe, it, expect } from "vitest";
import { ToolRegistry } from "./tool-registry.js";
import type { ToolDef } from "../core/types.js";

const makeTool = (name: string): ToolDef => ({
  name,
  description: `tool ${name}`,
  inputSchema: {},
  handler: async () => ({ content: [{ type: "text", text: name }] }),
});

describe("ToolRegistry", () => {
  it("registers and retrieves a tool", () => {
    const r = new ToolRegistry();
    r.register(makeTool("a"));
    expect(r.get("a")?.name).toBe("a");
    expect(r.list().map((t) => t.name)).toEqual(["a"]);
  });

  it("throws on duplicate tool names", () => {
    const r = new ToolRegistry();
    r.register(makeTool("a"));
    expect(() => r.register(makeTool("a"))).toThrow(/duplicate/i);
  });

  it("returns undefined for unknown tools", () => {
    const r = new ToolRegistry();
    expect(r.get("missing")).toBeUndefined();
  });
});
