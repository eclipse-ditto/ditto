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

import Database from "better-sqlite3";
import * as sqliteVec from "sqlite-vec";
import type { Chunk } from "./types.js";
import type { KnowledgeStore, IndexMeta } from "./knowledge-store.js";

export class SqliteKnowledgeStore implements KnowledgeStore {
  private readonly db: Database.Database;
  private vecReady = false;

  constructor(path = ":memory:") {
    this.db = new Database(path);
    try {
      sqliteVec.load(this.db);
      this.db.exec(`
        CREATE TABLE IF NOT EXISTS chunks (
          id TEXT PRIMARY KEY, source TEXT, title TEXT, text TEXT, cite TEXT
        );
        CREATE VIRTUAL TABLE IF NOT EXISTS chunks_fts USING fts5(id UNINDEXED, title, text);
        CREATE TABLE IF NOT EXISTS index_meta (id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL);
      `);
      // Detect a pre-existing vec table (reopened prebuilt file).
      const row = this.db
        .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='vec_items'")
        .get();
      this.vecReady = row !== undefined;
    } catch (e) {
      this.db.close();
      throw e;
    }
  }

  async addChunks(chunks: Chunk[]): Promise<void> {
    const insC = this.db.prepare(
      "INSERT OR REPLACE INTO chunks (id, source, title, text, cite) VALUES (?, ?, ?, ?, ?)",
    );
    const delF = this.db.prepare("DELETE FROM chunks_fts WHERE id = ?");
    const insF = this.db.prepare(
      "INSERT INTO chunks_fts (id, title, text) VALUES (?, ?, ?)",
    );
    const tx = this.db.transaction((rows: Chunk[]) => {
      for (const c of rows) {
        insC.run(c.id, c.source, c.title, c.text, c.cite);
        delF.run(c.id);
        insF.run(c.id, c.title, c.text);
      }
    });
    tx(chunks);
  }

  async getChunk(id: string): Promise<Chunk | undefined> {
    const r = this.db
      .prepare("SELECT id, source, title, text, cite FROM chunks WHERE id = ?")
      .get(id) as Chunk | undefined;
    return r;
  }

  async ftsSearch(query: string, k: number): Promise<string[]> {
    const match = toMatchQuery(query);
    if (match === "") return [];
    const rows = this.db
      .prepare("SELECT id FROM chunks_fts WHERE chunks_fts MATCH ? ORDER BY rank, id LIMIT ?")
      .all(match, k) as Array<{ id: string }>;
    return rows.map((r) => r.id);
  }

  async ensureVectorTable(dim: number): Promise<void> {
    if (this.vecReady) return;
    if (!Number.isInteger(dim) || dim <= 0) {
      throw new Error(`vector dim must be a positive integer (got ${dim})`);
    }
    this.db.exec(
      `CREATE VIRTUAL TABLE IF NOT EXISTS vec_items USING vec0(id TEXT PRIMARY KEY, embedding float[${dim}]);`,
    );
    this.vecReady = true;
  }

  async upsertVectors(items: { id: string; vector: number[] }[]): Promise<void> {
    if (!this.vecReady) throw new Error("call ensureVectorTable(dim) before upsertVectors");
    const del = this.db.prepare("DELETE FROM vec_items WHERE id = ?");
    const ins = this.db.prepare("INSERT INTO vec_items (id, embedding) VALUES (?, ?)");
    const tx = this.db.transaction((rows: { id: string; vector: number[] }[]) => {
      for (const r of rows) {
        del.run(r.id);
        ins.run(r.id, JSON.stringify(r.vector));
      }
    });
    tx(items);
  }

  async vectorSearch(vector: number[], k: number): Promise<{ id: string; distance: number }[]> {
    if (!this.vecReady) return [];
    const rows = this.db
      .prepare("SELECT id, distance FROM vec_items WHERE embedding MATCH ? AND k = ? ORDER BY distance")
      .all(JSON.stringify(vector), k) as Array<{ id: string; distance: number }>;
    return rows.map((r) => ({ id: r.id, distance: r.distance }));
  }

  async isPopulated(): Promise<boolean> {
    const row = this.db.prepare("SELECT COUNT(*) AS n FROM chunks").get() as { n: number };
    return row.n > 0;
  }

  async hasVectors(): Promise<boolean> {
    if (!this.vecReady) return false;
    const row = this.db.prepare("SELECT COUNT(*) AS n FROM vec_items").get() as { n: number };
    return row.n > 0;
  }

  async setMeta(meta: IndexMeta): Promise<void> {
    this.db.prepare("INSERT OR REPLACE INTO index_meta (id, json) VALUES (1, ?)")
      .run(JSON.stringify(meta));
  }

  async getMeta(): Promise<IndexMeta | undefined> {
    const row = this.db.prepare("SELECT json FROM index_meta WHERE id = 1").get() as { json: string } | undefined;
    if (!row) return undefined;
    try {
      return JSON.parse(row.json) as IndexMeta;
    } catch {
      return undefined;
    }
  }

  async reset(): Promise<void> {
    this.db.exec("DELETE FROM chunks; DELETE FROM chunks_fts; DELETE FROM index_meta;");
    if (this.vecReady) this.db.exec("DELETE FROM vec_items;");
  }

  async close(): Promise<void> {
    this.db.close();
  }
}

/** Arbitrary user text -> safe FTS5 MATCH (word tokens, quoted, OR-joined). */
function toMatchQuery(query: string): string {
  const tokens = query.match(/[\p{L}\p{N}]+/gu);
  if (!tokens || tokens.length === 0) return "";
  return tokens.map((t) => `"${t}"`).join(" OR ");
}
