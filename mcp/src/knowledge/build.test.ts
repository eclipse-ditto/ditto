import { describe, it, expect, afterEach } from "vitest";
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { buildKnowledgeService } from "./build.js";
import { AppConfigSchema } from "../config/schema.js";
import type { FetchFn } from "./public-source.js";
import type { KnowledgeService } from "./knowledge-service.js";
import type { EmbeddingProvider } from "./embedding.js";

let service: KnowledgeService | undefined;
afterEach(() => {
  service = undefined;
});

const fakeIndex = `
# Ditto Docs
- [Things](thing.md)
`;

const fakeDoc = `
# Things
A thing is a digital twin of a physical device.
`;

const fakeFetch: FetchFn = async (url) => {
  if (url.endsWith("index.md")) return fakeIndex;
  if (url.endsWith("thing.md")) return fakeDoc;
  throw new Error(`unexpected fetch: ${url}`);
};

const failFetch: FetchFn = async () => {
  throw new Error("fetch failed");
};

describe("buildKnowledgeService", () => {
  it("returns undefined when knowledge disabled", async () => {
    const config = AppConfigSchema.parse({ knowledge: { enabled: false } });
    const result = await buildKnowledgeService(config, {
      fetchFn: fakeFetch,
    });
    expect(result).toBeUndefined();
  });

  it("returns undefined when no sources enabled", async () => {
    const config = AppConfigSchema.parse({
      knowledge: { enabled: true, publicSource: { enabled: false } },
    });
    const result = await buildKnowledgeService(config, {
      fetchFn: fakeFetch,
    });
    expect(result).toBeUndefined();
  });

  it("gracefully degrades on init failure (returns undefined, logs)", async () => {
    const config = AppConfigSchema.parse({
      knowledge: {
        enabled: true,
        publicSource: {
          enabled: true,
          url: "http://example.com/index.md",
        },
      },
    });
    const result = await buildKnowledgeService(config, {
      fetchFn: failFetch,
    });
    expect(result).toBeUndefined();
  });

  it("builds and inits a working service with fake fetch", async () => {
    const config = AppConfigSchema.parse({
      knowledge: {
        enabled: true,
        publicSource: {
          enabled: true,
          url: "http://example.com/index.md",
        },
      },
    });
    service = await buildKnowledgeService(config, { fetchFn: fakeFetch });
    expect(service).toBeDefined();
    const hits = await service!.search("digital twin", 5);
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].chunk.text).toContain("digital twin");
  });
});

const fakeEmbedder: EmbeddingProvider = {
  dim: 3,
  embed: async (texts) =>
    texts.map((t) => {
      const s = t.toLowerCase();
      if (s.includes("reconnect") || s.includes("oom") || s.includes("memory")) return [1, 0, 0];
      if (s.includes("policy") || s.includes("access")) return [0, 1, 0];
      return [0, 0, 1];
    }),
};

describe("buildKnowledgeService — vector retriever over a local dir", () => {
  it("returns semantic hits using an injected embedder (no network/model)", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    writeFileSync(join(dir, "oom.md"), "# OOM\n\nNetty leak out of memory crash.");
    writeFileSync(join(dir, "pol.md"), "# Policy\n\npolicy access control.");

    const config = AppConfigSchema.parse({
      knowledge: {
        retriever: "vector",
        embedding: { dim: 3 },
        publicSource: { enabled: false },
        localDir: { enabled: true, path: dir },
      },
    });
    const svc = await buildKnowledgeService(config, { embeddingProvider: fakeEmbedder });
    expect(svc).toBeDefined();
    const hits = await svc!.search("why does it die on reconnect", 1);
    expect(hits[0].chunk.text.toLowerCase()).toContain("out of memory");
  });

  it("hybrid retriever wiring end-to-end (injected embedder, no network/model)", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    writeFileSync(join(dir, "oom.md"), "# OOM\n\nNetty leak out of memory crash.");
    writeFileSync(join(dir, "keyword.md"), "# Keyword\n\nsomething with unique-keyword-token.");

    const config = AppConfigSchema.parse({
      knowledge: {
        retriever: "hybrid",
        embedding: { dim: 3 },
        publicSource: { enabled: false },
        localDir: { enabled: true, path: dir },
      },
    });
    const svc = await buildKnowledgeService(config, { embeddingProvider: fakeEmbedder });
    expect(svc).toBeDefined();
    const hits = await svc!.search("reconnect memory issues", 2);
    expect(hits.some((rc) => rc.chunk.text.toLowerCase().includes("out of memory"))).toBe(true);
  });
});
