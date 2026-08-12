import { describe, it, expect } from "vitest";
import { makeSources } from "./factories.js";
import { AppConfigSchema } from "../config/schema.js";

const LONG_DOC = [
  "Alpha paragraph about token integration here.",
  "Beta paragraph about policy subjects here now.",
  "Gamma paragraph about activation actions here.",
  "Delta paragraph about JWT permission grants.",
].join("\n\n");

const INDEX = `# Ditto docs\n- [Doc](https://eclipse.dev/ditto/doc.md): a doc\n`;

const DOCS: Record<string, string> = {
  "https://eclipse.dev/ditto/llms.txt": INDEX,
  "https://eclipse.dev/ditto/doc.md": LONG_DOC,
};

const fakeFetch = async (url: string): Promise<string> => {
  if (!(url in DOCS)) throw new Error(`404 ${url}`);
  return DOCS[url];
};

describe("knowledge.chunk config", () => {
  it("defaults to maxChars 1000 / overlap 150", () => {
    const cfg = AppConfigSchema.parse({});
    expect(cfg.knowledge.chunk).toEqual({ maxChars: 1000, overlap: 150 });
  });

  it("rejects overlap >= maxChars", () => {
    expect(() =>
      AppConfigSchema.parse({ knowledge: { chunk: { maxChars: 100, overlap: 100 } } }),
    ).toThrow();
  });

  it("flows configured chunk size through makeSources to PublicSource", async () => {
    const cfg = AppConfigSchema.parse({
      knowledge: { chunk: { maxChars: 60, overlap: 10 } },
    });
    const sources = makeSources(cfg, { fetchFn: fakeFetch });
    const pub = sources.find((s) => s.id === "public");
    expect(pub).toBeDefined();
    const chunks = await pub!.loadChunks();
    // With maxChars=60 the 4 paragraphs cannot pack into one chunk.
    expect(chunks.length).toBeGreaterThan(1);
    expect(Math.max(...chunks.map((c) => c.text.length))).toBeLessThanOrEqual(60);
  });
});
