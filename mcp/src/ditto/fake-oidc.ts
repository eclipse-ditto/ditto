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

export interface FakeOidcRequest {
  method: string;
  headers: Record<string, string | string[] | undefined>;
  body: string;
}

export async function startFakeOidc(
  token: { access_token: string; expires_in: number },
): Promise<{ tokenUrl: string; stop: () => Promise<void>; calls: number; requests: FakeOidcRequest[] }> {
  let calls = 0;
  const requests: FakeOidcRequest[] = [];
  const server: Server = createServer((req, res) => {
    calls++;
    let body = "";
    req.on("data", (chunk) => { body += chunk; });
    req.on("end", () => {
      requests.push({ method: req.method ?? "", headers: req.headers as Record<string, string | string[] | undefined>, body });
      res.setHeader("content-type", "application/json");
      res.end(JSON.stringify(token));
    });
  });
  await new Promise<void>((r) => server.listen(0, "127.0.0.1", r));
  const addr = server.address();
  const port = typeof addr === "object" && addr ? addr.port : 0;
  return {
    tokenUrl: `http://127.0.0.1:${port}/token`,
    stop: () => new Promise<void>((r) => server.close(() => r())),
    get calls() { return calls; },
    requests,
  };
}
