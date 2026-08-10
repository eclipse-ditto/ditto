import { describe, it, expect, afterEach } from "vitest";
import { VectorRetriever } from "./vector-retriever.js";
import { SqliteVecStore } from "./sqlite-vec-store.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { Chunk } from "./types.js";

// Deterministic fake: map each text to a fixed 3-d vector by keyword.
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

let store: SqliteVecStore;
afterEach(() => store?.close());

describe("VectorRetriever", () => {
  it("has kind 'vector'", () => {
    store = new SqliteVecStore(3);
    const r = new VectorRetriever(fake, store);
    expect(r.kind).toBe("vector");
  });

  it("matches semantically (query near the OOM chunk, not the policy chunk)", async () => {
    store = new SqliteVecStore(3);
    const r = new VectorRetriever(fake, store);
    await r.add([
      chunk("oom", "Netty leak out of memory crash"),
      chunk("pol", "policy access control"),
    ]);
    const hits = await r.search("why does it die on reconnect", 1);
    expect(hits[0].chunk.id).toBe("oom");
    expect(hits[0].matchedBy).toEqual(["vector"]);
  });

  it("getChunk returns the stored chunk", async () => {
    store = new SqliteVecStore(3);
    const r = new VectorRetriever(fake, store);
    await r.add([chunk("a", "reconnect memory")]);
    expect((await r.getChunk("a"))?.text).toBe("reconnect memory");
    expect(await r.getChunk("missing")).toBeUndefined();
  });
});
