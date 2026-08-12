import { describe, it, expect, afterEach } from "vitest";
import { KnowledgeService } from "./knowledge-service.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import { FtsRetriever } from "./fts-retriever.js";
import { buildIndex } from "./build-index.js";
import type { KnowledgeSource, Chunk, RetrievedChunk, Retriever } from "./types.js";

const src = (id: string, chunks: Chunk[]): KnowledgeSource => ({ id, loadChunks: async () => chunks });
const chunk = (id: string, text: string): Chunk => ({ id, source: "s", title: "T", text, cite: `https://x/${id}` });

// A chunk positioned in a document: id `pub#<n>`, grouped by `cite`.
const c = (n: number, cite: string, text = `text ${n}`): Chunk => ({
  id: `pub#${n}`,
  source: "pub",
  title: `T${n}`,
  text,
  cite,
});

const stubRetriever = (anchors: RetrievedChunk[]): Retriever => ({
  kind: "stub",
  search: async () => anchors,
});

let store: SqliteKnowledgeStore;
afterEach(async () => await store?.close());

describe("KnowledgeService", () => {
  it("searches via the retriever and gets chunks via the store", async () => {
    store = new SqliteKnowledgeStore();
    await buildIndex([src("s1", [chunk("a", "reconnect memory crash")])], store);
    const svc = new KnowledgeService(store, new FtsRetriever(store));
    const hits = await svc.search("memory", 5);
    expect(hits[0].chunk.id).toBe("a");
    expect((await svc.getChunk("a"))?.text).toBe("reconnect memory crash");
  });
});

describe("KnowledgeService neighbor expansion", () => {
  it("context=0 returns only anchors", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([c(0, "A"), c(1, "A"), c(2, "A")]);
    const svc = new KnowledgeService(store, stubRetriever([{ chunk: c(1, "A"), matchedBy: ["fts"] }]));
    const hits = await svc.search("q", 5, { context: 0 });
    expect(hits.map((h) => h.chunk.id)).toEqual(["pub#1"]);
  });

  it("expands to same-cite neighbors in ordinal order, tagged by role", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([c(0, "A"), c(1, "A"), c(2, "A")]);
    const svc = new KnowledgeService(store, stubRetriever([{ chunk: c(1, "A"), matchedBy: ["vector"] }]));
    const hits = await svc.search("q", 5, { context: 1 });
    expect(hits.map((h) => h.chunk.id)).toEqual(["pub#0", "pub#1", "pub#2"]);
    expect(hits.map((h) => h.role)).toEqual(["context", "anchor", "context"]);
    expect(hits.find((h) => h.chunk.id === "pub#1")!.matchedBy).toEqual(["vector"]);
  });

  it("does not cross document boundaries (different cite)", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([c(0, "A"), c(1, "A"), c(2, "A"), c(3, "B"), c(4, "B")]);
    // anchor pub#3 = first chunk of doc B; pub#2 is numerically adjacent but belongs to doc A.
    const svc = new KnowledgeService(store, stubRetriever([{ chunk: c(3, "B"), matchedBy: ["fts"] }]));
    const hits = await svc.search("q", 5, { context: 1 });
    expect(hits.map((h) => h.chunk.id)).toEqual(["pub#3", "pub#4"]);
  });

  it("dedups and merges overlapping anchor windows into one contiguous span", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([c(0, "A"), c(1, "A"), c(2, "A"), c(3, "A")]);
    const svc = new KnowledgeService(
      store,
      stubRetriever([
        { chunk: c(1, "A"), matchedBy: ["fts"] },
        { chunk: c(2, "A"), matchedBy: ["vector"] },
      ]),
    );
    const hits = await svc.search("q", 5, { context: 1 });
    expect(hits.map((h) => h.chunk.id)).toEqual(["pub#0", "pub#1", "pub#2", "pub#3"]);
    expect(hits.map((h) => h.role)).toEqual(["context", "anchor", "anchor", "context"]);
    expect(new Set(hits.map((h) => h.chunk.id)).size).toBe(hits.length); // no dupes
  });
});
