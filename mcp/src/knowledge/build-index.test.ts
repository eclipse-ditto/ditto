import { describe, it, expect, afterEach } from "vitest";
import { buildIndex } from "./build-index.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import type { KnowledgeSource, Chunk } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";

const src = (id: string, chunks: Chunk[]): KnowledgeSource => ({ id, loadChunks: async () => chunks });
const chunk = (id: string, text: string): Chunk => ({ id, source: "s", title: "T", text, cite: `https://x/${id}` });
const fake: EmbeddingProvider = { dim: 3, embed: async (t) => t.map(() => [1, 0, 0]) };

let store: SqliteKnowledgeStore;
afterEach(async () => await store?.close());

describe("buildIndex", () => {
  it("adds chunks (fts) with no embedder", async () => {
    store = new SqliteKnowledgeStore();
    await buildIndex([src("s1", [chunk("a", "reconnect memory")])], store);
    expect(await store.isPopulated()).toBe(true);
    expect((await store.ftsSearch("memory", 5))[0]).toBe("a");
    expect(await store.vectorSearch([1, 0, 0], 5)).toEqual([]); // no vectors
  });

  it("adds chunks + vectors with an embedder", async () => {
    store = new SqliteKnowledgeStore();
    await buildIndex([src("s1", [chunk("a", "x")])], store, fake);
    expect((await store.vectorSearch([1, 0, 0], 1))[0].id).toBe("a");
  });
});
