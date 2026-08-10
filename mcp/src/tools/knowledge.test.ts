import { describe, it, expect, afterEach } from "vitest";
import { KnowledgeService } from "../knowledge/knowledge-service.js";
import { SqliteKnowledgeStore } from "../knowledge/sqlite-knowledge-store.js";
import { FtsRetriever } from "../knowledge/fts-retriever.js";
import { makeKnowledgeTools } from "./knowledge.js";
import { AppConfigSchema } from "../config/schema.js";

const ctx = { config: AppConfigSchema.parse({}) };
let store: SqliteKnowledgeStore | undefined;
afterEach(() => store?.close());

async function tools() {
  store = new SqliteKnowledgeStore(":memory:");
  await store.addChunks([
    { id: "a", source: "s", title: "Things", text: "a thing is a digital twin", cite: "https://x/a" },
  ]);
  const retriever = new FtsRetriever(store);
  const svc = new KnowledgeService(store, retriever);
  return Object.fromEntries(makeKnowledgeTools(svc).map((t) => [t.name, t]));
}

describe("knowledge tools", () => {
  it("search returns matching chunk text with citation and matched provenance", async () => {
    const t = await tools();
    const res = await t.search.handler({ query: "digital twin" }, ctx);
    const text = res.content.map((p) => p.text).join("\n");
    expect(text).toContain("digital twin");
    expect(text).toContain("https://x/a");
    expect(text).toContain("matched: fts");
  });

  it("search reports no results cleanly", async () => {
    const t = await tools();
    const res = await t.search.handler({ query: "zzzznotfound" }, ctx);
    expect(res.content[0].text.toLowerCase()).toContain("no results");
  });

  it("get_chunk returns the chunk by id, or a not-found message", async () => {
    const t = await tools();
    const ok = await t.get_chunk.handler({ id: "a" }, ctx);
    expect(ok.content[0].text).toContain("digital twin");
    const miss = await t.get_chunk.handler({ id: "nope" }, ctx);
    expect(miss.content[0].text.toLowerCase()).toContain("not found");
  });
});
