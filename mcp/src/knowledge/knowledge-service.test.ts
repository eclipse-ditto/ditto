import { describe, it, expect, afterEach } from "vitest";
import { KnowledgeService } from "./knowledge-service.js";
import { FtsRetriever } from "./fts-retriever.js";
import type { KnowledgeSource, Chunk } from "./types.js";

const source = (id: string, chunks: Chunk[]): KnowledgeSource => ({
  id,
  loadChunks: async () => chunks,
});

const chunk = (id: string, text: string): Chunk => ({
  id, source: "s", title: "T", text, cite: `https://x/${id}`,
});

let r: FtsRetriever;
afterEach(() => r?.close());

describe("KnowledgeService", () => {
  it("indexes all sources on init and searches across them", async () => {
    r = new FtsRetriever();
    const svc = new KnowledgeService(
      [source("s1", [chunk("a", "reconnect memory crash")]),
       source("s2", [chunk("b", "policy access control")])],
      r,
    );
    await svc.init();
    expect((await svc.search("memory", 5)).map((c) => c.id)).toContain("a");
    expect((await svc.search("policy", 5)).map((c) => c.id)).toContain("b");
    expect((await svc.getChunk("a"))?.text).toBe("reconnect memory crash");
  });

  it("init is idempotent (does not double-index)", async () => {
    r = new FtsRetriever();
    const svc = new KnowledgeService([source("s1", [chunk("a", "alpha")])], r);
    await svc.init();
    await svc.init();
    expect(await svc.search("alpha", 10)).toHaveLength(1);
  });
});
