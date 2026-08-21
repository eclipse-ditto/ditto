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
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";
import type { Chunk } from "./types.js";

const chunk = (id: string, text: string): Chunk => ({
  id, source: "s", title: "T", text, cite: `https://x/${id}`,
});

let store: SqliteKnowledgeStore;
afterEach(async () => await store?.close());

describe("SqliteKnowledgeStore", () => {
  it("stores chunks and returns them by id", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([chunk("a", "hello world")]);
    expect((await store.getChunk("a"))?.text).toBe("hello world");
    expect(await store.getChunk("missing")).toBeUndefined();
  });

  it("keyword-searches via FTS, best match first, honoring k, injection-safe", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([
      chunk("a", "Netty leak out of memory crash on reconnect"),
      chunk("b", "policies define access control"),
    ]);
    expect((await store.ftsSearch("memory crash", 5))[0]).toBe("a");
    expect(await store.ftsSearch("alpha", 1)).toEqual([]);
    expect(await store.ftsSearch('"(bad) AND *', 5)).toEqual([]); // no throw
  });

  it("vector-searches nearest first after ensureVectorTable", async () => {
    store = new SqliteKnowledgeStore();
    await store.ensureVectorTable(3);
    await store.upsertVectors([
      { id: "x", vector: [1, 0, 0] },
      { id: "y", vector: [0, 1, 0] },
    ]);
    const hits = await store.vectorSearch([1, 0, 0], 1);
    expect(hits[0].id).toBe("x");
  });

  it("vectorSearch returns [] when no vector table exists", async () => {
    store = new SqliteKnowledgeStore();
    expect(await store.vectorSearch([1, 0, 0], 5)).toEqual([]);
  });

  it("isPopulated reflects whether chunks exist", async () => {
    store = new SqliteKnowledgeStore();
    expect(await store.isPopulated()).toBe(false);
    await store.addChunks([chunk("a", "x")]);
    expect(await store.isPopulated()).toBe(true);
  });

  it("hasVectors is false until ensureVectorTable and upsertVectors; true after", async () => {
    store = new SqliteKnowledgeStore();
    expect(await store.hasVectors()).toBe(false);
    await store.ensureVectorTable(3);
    expect(await store.hasVectors()).toBe(false);
    await store.upsertVectors([{ id: "a", vector: [1, 0, 0] }]);
    expect(await store.hasVectors()).toBe(true);
  });

  it("addChunks is idempotent (re-adding an id does not duplicate FTS results)", async () => {
    store = new SqliteKnowledgeStore();
    await store.addChunks([chunk("a", "reconnect memory crash")]);
    await store.addChunks([chunk("a", "reconnect memory crash")]);
    expect(await store.ftsSearch("memory", 10)).toEqual(["a"]);
  });

  it("persists to a file: data survives close + reopen", async () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "index.db");
    const w = new SqliteKnowledgeStore(path);
    await w.ensureVectorTable(3);
    await w.addChunks([chunk("a", "reconnect memory")]);
    await w.upsertVectors([{ id: "a", vector: [1, 0, 0] }]);
    await w.close();

    store = new SqliteKnowledgeStore(path);
    expect(await store.isPopulated()).toBe(true);
    expect((await store.getChunk("a"))?.text).toBe("reconnect memory");
    expect((await store.ftsSearch("memory", 5))[0]).toBe("a");
    expect((await store.vectorSearch([1, 0, 0], 1))[0].id).toBe("a");
  });

  it("isPopulated/hasVectors are async and correct", async () => {
    store = new SqliteKnowledgeStore();
    expect(await store.isPopulated()).toBe(false);
    expect(await store.hasVectors()).toBe(false);
  });

  it("stores and returns index metadata", async () => {
    store = new SqliteKnowledgeStore();
    expect(await store.getMeta()).toBeUndefined();
    await store.setMeta({ schemaVersion: 1, retriever: "fts", complete: true });
    expect(await store.getMeta()).toEqual({ schemaVersion: 1, retriever: "fts", complete: true });
  });

  it("closes the DB handle on failed init (corrupt/non-sqlite file)", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(dir, "corrupt.db");
    const { writeFileSync } = require("node:fs");
    writeFileSync(path, "not a db");
    expect(() => new SqliteKnowledgeStore(path)).toThrow();
  });

  it("reset() clears chunks, fts, vectors, and meta", async () => {
    store = new SqliteKnowledgeStore();
    store.ensureVectorTable ? await store.ensureVectorTable(3) : null;
    await store.addChunks([{ id: "a", source: "s", title: "T", text: "netty", cite: "x" }]);
    await store.upsertVectors([{ id: "a", vector: [1, 0, 0] }]);
    await store.setMeta({ schemaVersion: 1, retriever: "fts", complete: true });
    await store.reset();
    expect(await store.isPopulated()).toBe(false);
    expect(await store.getChunk("a")).toBeUndefined();
    expect(await store.getMeta()).toBeUndefined();
  });
});
