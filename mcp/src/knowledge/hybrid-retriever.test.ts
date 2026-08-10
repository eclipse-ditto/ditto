import { describe, it, expect } from "vitest";
import { HybridRetriever } from "./hybrid-retriever.js";
import type { Retriever, Chunk, RetrievedChunk } from "./types.js";

const chunk = (id: string): Chunk => ({
  id, source: "s", title: id, text: id, cite: `https://x/${id}`,
});

// Fake retrievers returning fixed ranked lists.
function fixed(kind: string, ids: string[]): Retriever {
  return {
    kind,
    search: async (_q, k): Promise<RetrievedChunk[]> => ids.slice(0, k).map((id) => ({
      chunk: chunk(id),
      matchedBy: [kind],
    })),
  };
}

describe("HybridRetriever", () => {
  it("has kind 'hybrid'", () => {
    const h = new HybridRetriever([fixed("fts", ["a"]), fixed("vector", ["b"])]);
    expect(h.kind).toBe("hybrid");
  });

  it("fuses two ranked lists via RRF and dedupes", async () => {
    // 'b' appears in both lists -> should rank at/near the top after fusion.
    const a = fixed("fts", ["a", "b", "c"]);
    const d = fixed("vector", ["b", "d", "e"]);
    const h = new HybridRetriever([a, d]);
    const hits = await h.search("q", 3);
    const ids = hits.map((rc) => rc.chunk.id);
    expect(ids[0]).toBe("b");
    expect(new Set(ids).size).toBe(ids.length); // no duplicates
    expect(hits).toHaveLength(3);
  });

  it("merges matchedBy from both retrievers for duplicates", async () => {
    // 'b' appears in both lists -> matchedBy should be ["fts","vector"]
    const a = fixed("fts", ["a", "b", "c"]);
    const d = fixed("vector", ["b", "d", "e"]);
    const h = new HybridRetriever([a, d]);
    const hits = await h.search("q", 5);
    const b = hits.find((rc) => rc.chunk.id === "b");
    expect(b).toBeDefined();
    expect(b!.matchedBy).toEqual(["fts", "vector"]);
    // Single-source chunks should have single matchedBy
    const a_hit = hits.find((rc) => rc.chunk.id === "a");
    expect(a_hit!.matchedBy).toEqual(["fts"]);
    const d_hit = hits.find((rc) => rc.chunk.id === "d");
    expect(d_hit!.matchedBy).toEqual(["vector"]);
  });

});
