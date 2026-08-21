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

import { createServer, type Server } from "node:http";

export interface RecordedRequest { method: string; url: string; auth?: string; body?: string }
export interface FakeReply { status: number; body: string }

export async function startFakeDitto(
  handler: (req: RecordedRequest) => FakeReply,
): Promise<{ baseUrl: string; stop: () => Promise<void>; requests: RecordedRequest[] }> {
  const requests: RecordedRequest[] = [];
  const server: Server = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on("data", (c) => chunks.push(c as Buffer));
    req.on("end", () => {
      const rec: RecordedRequest = {
        method: req.method ?? "GET",
        url: req.url ?? "/",
        auth: req.headers["authorization"] as string | undefined,
        body: chunks.length ? Buffer.concat(chunks).toString("utf8") : undefined,
      };
      requests.push(rec);
      const reply = handler(rec);
      res.statusCode = reply.status;
      res.setHeader("content-type", "application/json");
      res.end(reply.body);
    });
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  const addr = server.address();
  const port = typeof addr === "object" && addr ? addr.port : 0;
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    stop: () => new Promise<void>((r) => server.close(() => r())),
    requests,
  };
}
