import { describe, it, expect, afterEach } from "vitest";
import { KnowledgeService } from "./knowledge-service.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import { FtsRetriever } from "./fts-retriever.js";
import { buildIndex } from "./build-index.js";
import type { KnowledgeSource, Chunk } from "./types.js";

const src = (id: string, chunks: Chunk[]): KnowledgeSource => ({ id, loadChunks: async () => chunks });
const chunk = (id: string, text: string): Chunk => ({ id, source: "s", title: "T", text, cite: `https://x/${id}` });

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
