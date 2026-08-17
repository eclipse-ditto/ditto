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

import { describe, it, expect, afterEach } from "vitest";
import { VectorRetriever } from "./vector-retriever.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { Chunk } from "./types.js";

const fake: EmbeddingProvider = {
  dim: 3,
  embed: async (texts) =>
    texts.map((t) => {
      const s = t.toLowerCase();
      if (s.includes("reconnect") || s.includes("oom") || s.includes("memory")) return [1, 0, 0];
      if (s.includes("policy") || s.includes("access")) return [0, 1, 0];
      return [0, 0, 1];
    }),
};
const chunk = (id: string, text: string): Chunk => ({
  id, source: "s", title: "T", text, cite: `https://x/${id}`,
});

let store: SqliteKnowledgeStore;
afterEach(async () => await store?.close());

describe("VectorRetriever", () => {
  async function retriever(chunks: Chunk[]) {
    store = new SqliteKnowledgeStore();
    await store.ensureVectorTable(fake.dim);
    await store.addChunks(chunks);
    const vectors = await fake.embed(chunks.map((c) => c.text));
    await store.upsertVectors(chunks.map((c, i) => ({ id: c.id, vector: vectors[i] })));
    return new VectorRetriever(store, fake);
  }

  it("returns semantic hits tagged matchedBy=['vector']", async () => {
    const r = await retriever([
      chunk("oom", "Netty leak out of memory crash"),
      chunk("pol", "policy access control"),
    ]);
    const hits = await r.search("why does it die on reconnect", 1);
    expect(hits[0].chunk.id).toBe("oom");
    expect(hits[0].matchedBy).toEqual(["vector"]);
  });
});
