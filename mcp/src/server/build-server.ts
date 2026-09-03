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

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { ToolRegistry } from "../registry/tool-registry.js";
import type { AppConfig } from "../config/schema.js";
import { buildCtx } from "./request-ctx.js";

export function buildServer(
  registry: ToolRegistry,
  config: AppConfig,
): McpServer {
  const server = new McpServer({ name: config.server.name, version: "0.1.0" });

  // Initialize tool handlers even when registry is empty
  // by registering and immediately disabling a placeholder.
  // This ensures tools/list is always available (SDK 1.29.0 lazy-initializes handlers).
  if (registry.list().length === 0) {
    const placeholder = server.registerTool(
      "_init",
      { description: "Placeholder to initialize tool handlers" },
      async () => ({ content: [] }),
    );
    placeholder.disable();
  }

  for (const def of registry.list()) {
    server.registerTool(
      def.name,
      { description: def.description, inputSchema: def.inputSchema },
      async (args: unknown, extra: unknown) =>
        def.handler(args, buildCtx(config, extra as Parameters<typeof buildCtx>[1])),
    );
  }
  return server;
}
