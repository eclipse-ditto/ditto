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

import { describe, it, expect, beforeAll, afterAll } from "vitest";
import { Client } from "pg";
import { startPgVector } from "./pg-testcontainer.js";

let pg: Awaited<ReturnType<typeof startPgVector>>;
beforeAll(async () => { pg = await startPgVector(); }, 120000);
afterAll(async () => { await pg?.stop(); });

describe("pgvector container", () => {
  it("has the vector extension available", async () => {
    const client = new Client({ connectionString: pg.connectionString });
    await client.connect();
    await client.query("CREATE EXTENSION IF NOT EXISTS vector");
    const r = await client.query("SELECT '[1,2,3]'::vector AS v");
    expect(r.rows[0].v).toBeDefined();
    await client.end();
  });
});
