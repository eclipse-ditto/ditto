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

  it("search pulls in same-document neighbors as labeled context", async () => {
    store = new SqliteKnowledgeStore(":memory:");
    await store.addChunks([
      { id: "pub#0", source: "pub", title: "T", text: "alpha prelude", cite: "https://x/doc" },
      { id: "pub#1", source: "pub", title: "T", text: "bravo uniquematchword baz", cite: "https://x/doc" },
      { id: "pub#2", source: "pub", title: "T", text: "charlie epilogue", cite: "https://x/doc" },
    ]);
    const svc = new KnowledgeService(store, new FtsRetriever(store));
    const t = Object.fromEntries(
      makeKnowledgeTools(svc, { limit: 5, context: 1 }).map((tool) => [tool.name, tool]),
    );
    const res = await t.search.handler({ query: "uniquematchword" }, ctx);
    const text = res.content.map((p) => p.text).join("\n");
    // anchor labeled matched, neighbors labeled context
    expect(text).toContain("matched: fts");
    expect(text).toContain("context");
    expect(text).toContain("alpha prelude"); // neighbor pub#0
    expect(text).toContain("charlie epilogue"); // neighbor pub#2
    // context=0 override suppresses neighbors
    const only = await t.search.handler({ query: "uniquematchword", context: 0 }, ctx);
    const onlyText = only.content.map((p) => p.text).join("\n");
    expect(onlyText).not.toContain("alpha prelude");
  });
});
