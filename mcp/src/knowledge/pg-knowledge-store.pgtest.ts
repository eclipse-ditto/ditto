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
import { startPgVector } from "./pg-testcontainer.js";
import { PgKnowledgeStore } from "./pg-knowledge-store.js";
import type { Chunk } from "./types.js";

const chunk = (id: string, text: string): Chunk => ({ id, source: "s", title: "T", text, cite: `https://x/${id}` });

let pg: Awaited<ReturnType<typeof startPgVector>>;
beforeAll(async () => { pg = await startPgVector(); }, 120000);
afterAll(async () => { await pg?.stop(); });

async function fresh() {
  const store = await PgKnowledgeStore.connect(pg.connectionString, `t_${Math.floor(performance.now())}`);
  await store.reset();
  return store;
}

describe("PgKnowledgeStore", () => {
  it("addChunks + getChunk + isPopulated", async () => {
    const s = await fresh();
    expect(await s.isPopulated()).toBe(false);
    await s.addChunks([chunk("a", "netty out of memory")]);
    expect(await s.isPopulated()).toBe(true);
    expect((await s.getChunk("a"))?.text).toBe("netty out of memory");
    await s.close();
  });

  it("ftsSearch ranks keyword matches", async () => {
    const s = await fresh();
    await s.addChunks([chunk("a", "netty leak out of memory crash"), chunk("b", "policy access control")]);
    expect((await s.ftsSearch("memory crash", 5))[0]).toBe("a");
    expect(await s.ftsSearch("zzznope", 5)).toEqual([]);
    await s.close();
  });

  it("vectorSearch returns nearest; hasVectors reflects state", async () => {
    const s = await fresh();
    await s.ensureVectorTable(3);
    expect(await s.hasVectors()).toBe(false);
    await s.addChunks([chunk("x", "a"), chunk("y", "b")]);
    await s.upsertVectors([{ id: "x", vector: [1, 0, 0] }, { id: "y", vector: [0, 1, 0] }]);
    expect(await s.hasVectors()).toBe(true);
    expect((await s.vectorSearch([1, 0, 0], 1))[0].id).toBe("x");
    await s.close();
  });

  it("meta round-trip + reset clears everything", async () => {
    const s = await fresh();
    await s.setMeta({ schemaVersion: 1, retriever: "fts", complete: true });
    expect((await s.getMeta())?.complete).toBe(true);
    await s.addChunks([chunk("a", "x")]);
    await s.reset();
    expect(await s.isPopulated()).toBe(false);
    expect(await s.getMeta()).toBeUndefined();
    await s.close();
  });

  it("reset() drops vec table → re-ingest can change dim", async () => {
    const s = await fresh();
    await s.ensureVectorTable(3);
    await s.addChunks([chunk("x", "a")]);
    await s.upsertVectors([{ id: "x", vector: [1, 0, 0] }]);
    expect(await s.hasVectors()).toBe(true);
    await s.reset();
    expect(await s.isPopulated()).toBe(false);
    // Re-ingest at a different dim succeeds (proves vec table was dropped + recreated).
    await s.ensureVectorTable(4);
    await s.addChunks([chunk("y", "b")]);
    await s.upsertVectors([{ id: "y", vector: [0, 1, 0, 1] }]);
    expect(await s.hasVectors()).toBe(true);
    expect((await s.vectorSearch([0, 1, 0, 1], 1))[0].id).toBe("y");
    await s.close();
  });

  it("ftsSearch uses OR semantics (chunk matches ANY token)", async () => {
    const s = await fresh();
    await s.addChunks([chunk("a", "netty out of memory crash"), chunk("b", "policy access control")]);
    // 2-term query where chunk b matches only "policy" (not "memory") → still returned (OR recall).
    const hits = await s.ftsSearch("memory policy", 5);
    expect(hits).toContain("a"); // matches "memory"
    expect(hits).toContain("b"); // matches "policy"
    await s.close();
  });
});
