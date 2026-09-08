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
import { FtsRetriever } from "./fts-retriever.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import type { Chunk } from "./types.js";

const chunk = (id: string, text: string): Chunk => ({
  id, source: "s", title: "T", text, cite: `https://x/${id}`,
});

let store: SqliteKnowledgeStore;
afterEach(async () => await store?.close());

describe("FtsRetriever", () => {
  async function retriever(chunks: Chunk[]) {
    store = new SqliteKnowledgeStore();
    await store.addChunks(chunks);
    return new FtsRetriever(store);
  }

  it("returns keyword hits tagged matchedBy=['fts'], best first", async () => {
    const r = await retriever([
      chunk("a", "Netty leak out of memory crash"),
      chunk("b", "policies access control"),
    ]);
    const hits = await r.search("memory crash", 5);
    expect(hits[0].chunk.id).toBe("a");
    expect(hits[0].matchedBy).toEqual(["fts"]);
  });

  it("honors k and returns [] on no match", async () => {
    const r = await retriever([chunk("a", "alpha"), chunk("b", "alpha")]);
    expect(await r.search("alpha", 1)).toHaveLength(1);
    expect(await r.search("zzznope", 5)).toEqual([]);
  });
});
