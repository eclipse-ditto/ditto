#!/usr/bin/env node
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

import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadConfig } from "../config/load.js";
import { registerTools } from "../tools/index.js";
import { buildServer } from "../server/build-server.js";
import { buildKnowledgeService } from "../knowledge/build.js";

async function main(): Promise<void> {
  const config = loadConfig(process.env.DITTO_MCP_CONFIG);
  const knowledge = await buildKnowledgeService(config);
  const registry = await registerTools(config, knowledge);
  const server = buildServer(registry, config);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // Never write to stdout except MCP protocol frames; logs go to stderr.
  process.stderr.write(`[ditto-mcp] stdio server ready: ${config.server.name}\n`);
}

main().catch((err) => {
  process.stderr.write(`[ditto-mcp] fatal: ${String(err)}\n`);
  process.exit(1);
});
