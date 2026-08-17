/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

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

describe("buildKnowledgeService — prebuilt sqlite file", () => {
  it("opens a populated index file without rebuilding (fts)", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const corpus = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    writeFileSync(join(corpus, "a.md"), "# Reconnect\n\nNetty leak out of memory crash.");
    const path = join(dir, "index.db");

    // Pre-populate the file via the same buildIndex the CLI uses.
    const { SqliteKnowledgeStore } = await import("./sqlite-knowledge-store.js");
    const { buildIndex } = await import("./build-index.js");
    const { LocalDirSource } = await import("./local-dir-source.js");
    const w = new SqliteKnowledgeStore(path);
    await buildIndex([new LocalDirSource({ dir: corpus })], w);
    await w.close();

    const config = AppConfigSchema.parse({
      knowledge: {
        retriever: "fts",
        publicSource: { enabled: false },
        localDir: { enabled: true, path: "/nonexistent-should-not-be-read" },
        store: { kind: "sqlite", sqlite: { path } },
      },
    });
    // localDir points at a missing dir on purpose: if the server rebuilt, it would
    // find nothing; since it must LOAD the prebuilt file, search still works.
    const svc = await buildKnowledgeService(config);
    expect(svc).toBeDefined();
    const hits = await svc!.search("out of memory", 5);
    expect(hits[0].chunk.text.toLowerCase()).toContain("out of memory");
  });

  it("rebuilds (fallback) when a prebuilt file's retriever metadata mismatches config", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    const { SqliteKnowledgeStore } = await import("./sqlite-knowledge-store.js");
    const { buildIndex } = await import("./build-index.js");
    const w = new SqliteKnowledgeStore(path);
    await buildIndex([{ id: "s", loadChunks: async () => [
      { id: "s#0", source: "s", title: "T", text: "netty oom", cite: "x" }] }], w, undefined,
      { retriever: "fts" });
    await w.close();
    // Server configured for "vector" but the file was built for "fts" -> must not serve it.
    const config = AppConfigSchema.parse({
      knowledge: { retriever: "vector", embedding: { dim: 3 }, publicSource: { enabled: false },
        localDir: { enabled: false }, store: { kind: "sqlite", sqlite: { path } } },
    });
    const fake = { dim: 3, embed: async (t: string[]) => t.map(() => [1, 0, 0]) };
    const svc = await buildKnowledgeService(config, { embeddingProvider: fake });
    // localDir disabled + publicSource disabled -> fallback build has no sources -> undefined.
    // The point: it did NOT serve the mismatched fts file as a vector index.
    expect(svc).toBeUndefined();
  });

  it("falls back to in-memory build when the store path is a corrupt file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    writeFileSync(path, "this is not a sqlite database");
    const corpus = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    writeFileSync(join(corpus, "a.md"), "# A\n\nnetty out of memory");
    const config = AppConfigSchema.parse({
      knowledge: { retriever: "fts", publicSource: { enabled: false },
        localDir: { enabled: true, path: corpus }, store: { kind: "sqlite", sqlite: { path } } },
    });
    const svc = await buildKnowledgeService(config);
    expect(svc).toBeDefined();
    expect((await svc!.search("memory", 5))[0].chunk.text.toLowerCase()).toContain("out of memory");
  });

  it("refuses to serve a prebuilt vector index with mismatched embedding dim", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    // Build a vector index with dim=3
    const { SqliteKnowledgeStore } = await import("./sqlite-knowledge-store.js");
    const { buildIndex } = await import("./build-index.js");
    const fake3 = { dim: 3, embed: async (t: string[]) => t.map(() => [1, 0, 0]) };
    const w = new SqliteKnowledgeStore(path);
    await buildIndex([{ id: "s", loadChunks: async () => [
      { id: "s#0", source: "s", title: "T", text: "netty oom", cite: "x" }] }], w, fake3,
      { retriever: "vector", embeddingModel: "fake/model", embeddingDim: 3 });
    await w.close();
    // Now try to serve it with config expecting dim=4
    const config = AppConfigSchema.parse({
      knowledge: { retriever: "vector", embedding: { model: "fake/model", dim: 4 },
        publicSource: { enabled: false }, localDir: { enabled: false },
        store: { kind: "sqlite", sqlite: { path } } },
    });
    const fake4 = { dim: 4, embed: async (t: string[]) => t.map(() => [1, 0, 0, 0]) };
    const svc = await buildKnowledgeService(config, { embeddingProvider: fake4 });
    // The mismatched file must NOT be served; fallback has no sources -> undefined.
    expect(svc).toBeUndefined();
  });
});
