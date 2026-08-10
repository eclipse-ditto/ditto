import { describe, it, expect } from "vitest";
import { mkdtempSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { AppConfigSchema } from "../config/schema.js";
import { withIngestStore } from "./ingest-store.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";

describe("withIngestStore (sqlite)", () => {
  it("builds into a temp file then renames atomically", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    const config = AppConfigSchema.parse({ knowledge: { store: { kind: "sqlite", sqlite: { path } } } });
    await withIngestStore(config, async (store) => {
      await store.addChunks([{ id: "a", source: "s", title: "T", text: "netty", cite: "x" }]);
      await store.setMeta({ schemaVersion: 1, retriever: "fts", complete: true });
    });
    expect(existsSync(path)).toBe(true);
    expect(existsSync(`${path}.tmp`)).toBe(false);
    const s = new SqliteKnowledgeStore(path);
    expect(await s.isPopulated()).toBe(true);
    await s.close();
  });

  it("removes tmp file on error, does not rename, and propagates error", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    const config = AppConfigSchema.parse({ knowledge: { store: { kind: "sqlite", sqlite: { path } } } });
    await expect(
      withIngestStore(config, async () => {
        throw new Error("boom");
      }),
    ).rejects.toThrow("boom");
    expect(existsSync(path)).toBe(false);
    expect(existsSync(`${path}.tmp`)).toBe(false);
  });
});
