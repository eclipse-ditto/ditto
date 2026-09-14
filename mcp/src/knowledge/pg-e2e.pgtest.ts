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
import { AppConfigSchema } from "../config/schema.js";
import { withIngestStore } from "./ingest-store.js";
import { buildIndex, metaFor } from "./build-index.js";
import { buildKnowledgeService } from "./build.js";
import type { KnowledgeSource, Chunk } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";

const fake: EmbeddingProvider = {
  dim: 3,
  embed: async (texts) => texts.map((t) =>
    /reconnect|oom|memory/i.test(t) ? [1, 0, 0] : /policy|access/i.test(t) ? [0, 1, 0] : [0, 0, 1]),
};
const chunk = (id: string, text: string): Chunk => ({ id, source: "local", title: "T", text, cite: id });
const src: KnowledgeSource = { id: "local", loadChunks: async () => [
  chunk("local#0", "Netty leak out of memory crash"), chunk("local#1", "policy access control")] };

let pg: Awaited<ReturnType<typeof startPgVector>>;
beforeAll(async () => { pg = await startPgVector(); }, 120000);
afterAll(async () => { await pg?.stop(); });

function cfg() {
  return AppConfigSchema.parse({
    knowledge: {
      retriever: "hybrid", embedding: { dim: 3 },
      publicSource: { enabled: false }, localDir: { enabled: false },
      store: { kind: "pgvector", pgvector: { connectionString: pg.connectionString, table: "e2e" } },
    },
  });
}

describe("pgvector end-to-end", () => {
  it("ingest populates pg; server serves hybrid search from it", async () => {
    await withIngestStore(cfg(), (store) => buildIndex([src], store, fake, metaFor(cfg(), fake)));
    const svc = await buildKnowledgeService(cfg(), { embeddingProvider: fake });
    expect(svc).toBeDefined();
    const hits = await svc!.search("why does it die on reconnect", 2);
    expect(hits[0].chunk.text.toLowerCase()).toContain("out of memory");
    expect(hits[0].matchedBy.length).toBeGreaterThan(0);
  });

  it("empty pg store → buildKnowledgeService returns undefined (no auto-write)", async () => {
    const c = cfg();
    c.knowledge.store.pgvector!.table = `empty_${Math.floor(performance.now())}`;
    const { openStore } = await import("./store-factory.js");
    const store = await openStore(c);
    expect(await store.isPopulated()).toBe(false); // Confirm empty before building service.
    await store.close();
    const svc = await buildKnowledgeService(c, { embeddingProvider: fake });
    expect(svc).toBeUndefined(); // Server does NOT auto-build for pgvector.
    // Confirm the store is STILL empty (proves no auto-write).
    const check = await openStore(c);
    expect(await check.isPopulated()).toBe(false);
    await check.close();
  });
});
