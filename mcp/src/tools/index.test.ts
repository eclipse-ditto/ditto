import { describe, it, expect, afterEach } from "vitest";
import { registerTools } from "./index.js";
import { AppConfigSchema } from "../config/schema.js";
import { KnowledgeService } from "../knowledge/knowledge-service.js";
import { SqliteKnowledgeStore } from "../knowledge/sqlite-knowledge-store.js";
import { FtsRetriever } from "../knowledge/fts-retriever.js";
import type { Chunk } from "../knowledge/types.js";

const chunk = (id: string, text: string): Chunk => ({
  id,
  source: "test",
  title: "T",
  text,
  cite: `https://x/${id}`,
});

let store: SqliteKnowledgeStore | undefined;
afterEach(() => store?.close());

describe("registerTools wiring", () => {
  it("registers ping by default", async () => {
    const reg = await registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: false } }),
    );
    expect(reg.get("ping")).toBeDefined();
  });

  it("omits action tools when ditto disabled (default)", async () => {
    const reg = await registerTools(AppConfigSchema.parse({}));
    expect(reg.list().some((t) => t.name === "getThingById")).toBe(false);
  });

  it("omits knowledge tools when knowledge disabled", async () => {
    const reg = await registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: false } }),
    );
    expect(reg.get("search")).toBeUndefined();
    expect(reg.get("get_chunk")).toBeUndefined();
  });

  it("omits knowledge tools when service not provided", async () => {
    const reg = await registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: true } }),
    );
    expect(reg.get("search")).toBeUndefined();
    expect(reg.get("get_chunk")).toBeUndefined();
  });

  it("registers knowledge tools when enabled and service provided", async () => {
    store = new SqliteKnowledgeStore(":memory:");
    await store.addChunks([chunk("a", "test content")]);
    const retriever = new FtsRetriever(store);
    const service = new KnowledgeService(store, retriever);
    const reg = await registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: true } }),
      service,
    );
    expect(reg.get("ping")).toBeDefined();
    expect(reg.get("search")).toBeDefined();
    expect(reg.get("get_chunk")).toBeDefined();
  });
});
