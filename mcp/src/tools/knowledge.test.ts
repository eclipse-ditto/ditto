import { describe, it, expect, afterEach } from "vitest";
import { KnowledgeService } from "../knowledge/knowledge-service.js";
import { FtsRetriever } from "../knowledge/fts-retriever.js";
import { makeKnowledgeTools } from "./knowledge.js";
import { AppConfigSchema } from "../config/schema.js";
import type { KnowledgeSource } from "../knowledge/types.js";

const src: KnowledgeSource = {
  id: "s",
  loadChunks: async () => [
    { id: "a", source: "s", title: "Things", text: "a thing is a digital twin", cite: "https://x/a" },
  ],
};

const ctx = { config: AppConfigSchema.parse({}) };
let r: FtsRetriever;
afterEach(() => r?.close());

async function tools() {
  r = new FtsRetriever();
  const svc = new KnowledgeService([src], r);
  await svc.init();
  return Object.fromEntries(makeKnowledgeTools(svc).map((t) => [t.name, t]));
}

describe("knowledge tools", () => {
  it("search returns matching chunk text with citation", async () => {
    const t = await tools();
    const res = await t.search.handler({ query: "digital twin" }, ctx);
    const text = res.content.map((p) => p.text).join("\n");
    expect(text).toContain("digital twin");
    expect(text).toContain("https://x/a");
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
