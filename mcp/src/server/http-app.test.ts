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
import { request } from "node:http";
import type { Server } from "node:http";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { AppConfigSchema } from "../config/schema.js";
import { createHttpApp } from "./http-app.js";

async function listen(): Promise<{ server: Server; url: string }> {
  // protocol e2e; DNS-rebinding protection exercised separately below.
  // knowledge disabled to keep this transport test hermetic; knowledge covered in knowledge tests.
  const config = AppConfigSchema.parse({
    server: { http: { enableDnsRebindingProtection: false } },
    knowledge: { enabled: false },
  });
  const app = createHttpApp(config);
  return await new Promise((res) => {
    const server = app.listen(0, () => {
      const addr = server.address();
      const port = typeof addr === "object" && addr ? addr.port : 0;
      res({ server, url: `http://127.0.0.1:${port}/mcp` });
    });
  });
}

describe("streamable HTTP app (e2e)", () => {
  it("serves ping over streamable HTTP with a session", async () => {
    const { server, url } = await listen();
    const transport = new StreamableHTTPClientTransport(new URL(url));
    const client = new Client({ name: "http-test", version: "0.0.0" });
    await client.connect(transport);
    const res = await client.callTool({ name: "ping", arguments: {} });
    const content = res.content as Array<{ type: string; text: string }>;
    expect(content[0].text).toBe("pong");
    await client.close();
    await new Promise<void>((r) => server.close(() => r()));
  });

  it("rejects prototype pollution attempts without crashing", async () => {
    const { server, url } = await listen();
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "mcp-session-id": "__proto__",
      },
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: 1,
        method: "tools/call",
        params: { name: "ping", arguments: {} },
      }),
    });
    expect(response.status).toBe(400);
    const body = await response.json();
    expect(body.error.message).toBe("Bad Request: no valid session");
    await new Promise<void>((r) => server.close(() => r()));
  });

  it("rejects DNS rebinding attacks by default", async () => {
    // Default config has DNS-rebinding protection enabled
    // knowledge disabled to keep this transport test hermetic; knowledge covered in knowledge tests.
    const config = AppConfigSchema.parse({ knowledge: { enabled: false } });
    const app = createHttpApp(config);
    const server = await new Promise<Server>((res) => {
      const srv = app.listen(0, () => res(srv));
    });
    const addr = server.address();
    const port = typeof addr === "object" && addr ? addr.port : 0;

    const body = JSON.stringify({
      jsonrpc: "2.0",
      id: 1,
      method: "initialize",
      params: {
        protocolVersion: "2024-11-05",
        capabilities: {},
        clientInfo: { name: "attacker", version: "1.0" },
      },
    });

    const statusCode = await new Promise<number>((resolve) => {
      const req = request(
        {
          hostname: "127.0.0.1",
          port,
          path: "/mcp",
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Content-Length": Buffer.byteLength(body),
            host: "attacker.example",
          },
        },
        (res) => {
          resolve(res.statusCode ?? 0);
        },
      );
      req.write(body);
      req.end();
    });

    expect(statusCode).toBe(403);
    await new Promise<void>((r) => server.close(() => r()));
  });
});
