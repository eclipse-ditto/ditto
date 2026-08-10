import { describe, it, expect, afterEach } from "vitest";
import { registerTools } from "./index.js";
import { AppConfigSchema } from "../config/schema.js";
import { KnowledgeService } from "../knowledge/knowledge-service.js";
import { FtsRetriever } from "../knowledge/fts-retriever.js";
import type { KnowledgeSource, Chunk } from "../knowledge/types.js";

const chunk = (id: string, text: string): Chunk => ({
  id,
  source: "test",
  title: "T",
  text,
  cite: `https://x/${id}`,
});

const fakeSource = (chunks: Chunk[]): KnowledgeSource => ({
  id: "fake",
  loadChunks: async () => chunks,
});

let retriever: FtsRetriever | undefined;
afterEach(() => retriever?.close());

describe("registerTools wiring", () => {
  it("registers ping by default", () => {
    const reg = registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: false } }),
    );
    expect(reg.get("ping")).toBeDefined();
  });

  it("omits knowledge tools when knowledge disabled", () => {
    const reg = registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: false } }),
    );
    expect(reg.get("search")).toBeUndefined();
    expect(reg.get("get_chunk")).toBeUndefined();
  });

  it("omits knowledge tools when service not provided", () => {
    const reg = registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: true } }),
    );
    expect(reg.get("search")).toBeUndefined();
    expect(reg.get("get_chunk")).toBeUndefined();
  });

  it("registers knowledge tools when enabled and service provided", async () => {
    retriever = new FtsRetriever();
    const service = new KnowledgeService(
      [fakeSource([chunk("a", "test content")])],
      retriever,
    );
    await service.init();
    const reg = registerTools(
      AppConfigSchema.parse({ knowledge: { enabled: true } }),
      service,
    );
    expect(reg.get("ping")).toBeDefined();
    expect(reg.get("search")).toBeDefined();
    expect(reg.get("get_chunk")).toBeDefined();
  });
});
