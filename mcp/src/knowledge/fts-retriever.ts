import Database from "better-sqlite3";
import type { Chunk, Retriever } from "./types.js";

export class FtsRetriever implements Retriever {
  private readonly db: Database.Database;
  private readonly chunks = new Map<string, Chunk>();

  constructor() {
    this.db = new Database(":memory:");
    this.db.exec(
      "CREATE VIRTUAL TABLE chunks USING fts5(id UNINDEXED, title, text);",
    );
  }

  async add(chunks: Chunk[]): Promise<void> {
    const insert = this.db.prepare(
      "INSERT INTO chunks (id, title, text) VALUES (?, ?, ?)",
    );
    const tx = this.db.transaction((rows: Chunk[]) => {
      for (const c of rows) {
        insert.run(c.id, c.title, c.text);
        this.chunks.set(c.id, c);
      }
    });
    tx(chunks);
  }

  async search(query: string, k: number): Promise<Chunk[]> {
    const match = toMatchQuery(query);
    if (match === "") return [];
    const rows = this.db
      .prepare(
        "SELECT id FROM chunks WHERE chunks MATCH ? ORDER BY rank, id LIMIT ?",
      )
      .all(match, k) as Array<{ id: string }>;
    const out: Chunk[] = [];
    for (const row of rows) {
      const c = this.chunks.get(row.id);
      if (c) out.push(c);
    }
    return out;
  }

  async getChunk(id: string): Promise<Chunk | undefined> {
    return this.chunks.get(id);
  }

  close(): void {
    this.db.close();
  }
}

/**
 * Turn arbitrary user text into a safe FTS5 MATCH expression: extract
 * word tokens, quote each as a phrase, join with OR for recall. Returns
 * "" when there are no usable tokens (caller returns no results).
 */
function toMatchQuery(query: string): string {
  const tokens = query.match(/[\p{L}\p{N}]+/gu);
  if (!tokens || tokens.length === 0) return "";
  return tokens.map((t) => `"${t}"`).join(" OR ");
}
