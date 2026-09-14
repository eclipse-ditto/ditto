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
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { InMemoryTransport } from "@modelcontextprotocol/sdk/inMemory.js";
import { AppConfigSchema } from "../config/schema.js";
import { registerTools } from "../tools/index.js";
import { buildServer } from "./build-server.js";

async function connectedClient() {
  // knowledge disabled to keep this transport test hermetic; knowledge covered in knowledge tests.
  const config = AppConfigSchema.parse({ knowledge: { enabled: false } });
  const registry = await registerTools(config);
  const server = buildServer(registry, config);
  const [clientTransport, serverTransport] =
    InMemoryTransport.createLinkedPair();
  await server.connect(serverTransport);
  const client = new Client({ name: "test-client", version: "0.0.0" });
  await client.connect(clientTransport);
  return client;
}

describe("buildServer + ping (in-memory e2e)", () => {
  it("lists the ping tool", async () => {
    const client = await connectedClient();
    const { tools } = await client.listTools();
    expect(tools.map((t) => t.name)).toContain("ping");
    await client.close();
  });

  it("calls ping and gets pong", async () => {
    const client = await connectedClient();
    const res = await client.callTool({ name: "ping", arguments: {} });
    const content = res.content as Array<{ type: string; text: string }>;
    expect(content[0]).toEqual({ type: "text", text: "pong" });
    await client.close();
  });

  it("omits ping when disabled in config", async () => {
    // knowledge disabled to keep this transport test hermetic; knowledge covered in knowledge tests.
    const config = AppConfigSchema.parse({
      tools: { ping: false },
      knowledge: { enabled: false },
    });
    const registry = await registerTools(config);
    const server = buildServer(registry, config);
    const [ct, st] = InMemoryTransport.createLinkedPair();
    await server.connect(st);
    const client = new Client({ name: "t", version: "0" });
    await client.connect(ct);
    const { tools } = await client.listTools();
    expect(tools.map((t) => t.name)).not.toContain("ping");
    await client.close();
  });
});
