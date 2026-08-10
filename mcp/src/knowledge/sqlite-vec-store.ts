import Database from "better-sqlite3";
import * as sqliteVec from "sqlite-vec";
import type { VectorRecord, VectorHit, VectorStore } from "./vector-store.js";

export class SqliteVecStore implements VectorStore {
  private readonly db: Database.Database;
  private readonly dim: number;

  constructor(dim: number) {
    if (!Number.isInteger(dim) || dim <= 0) {
      throw new Error(`SqliteVecStore: dim must be a positive integer (got ${dim})`);
    }
    this.dim = dim;
    this.db = new Database(":memory:");
    sqliteVec.load(this.db);
    this.db.exec(
      `CREATE VIRTUAL TABLE vec_items USING vec0(id TEXT PRIMARY KEY, embedding float[${dim}]);`,
    );
  }

  async upsert(records: VectorRecord[]): Promise<void> {
    const del = this.db.prepare("DELETE FROM vec_items WHERE id = ?");
    const ins = this.db.prepare(
      "INSERT INTO vec_items (id, embedding) VALUES (?, ?)",
    );
    const tx = this.db.transaction((rows: VectorRecord[]) => {
      for (const r of rows) {
        if (r.vector.length !== this.dim) {
          throw new Error(`SqliteVecStore: vector length ${r.vector.length} != dim ${this.dim}`);
        }
        del.run(r.id);
        ins.run(r.id, JSON.stringify(r.vector));
      }
    });
    tx(records);
  }

  async searchByVector(vector: number[], k: number): Promise<VectorHit[]> {
    const rows = this.db
      .prepare(
        "SELECT id, distance FROM vec_items WHERE embedding MATCH ? AND k = ? ORDER BY distance",
      )
      .all(JSON.stringify(vector), k) as Array<{ id: string; distance: number }>;
    return rows.map((r) => ({ id: r.id, distance: r.distance }));
  }

  close(): void {
    this.db.close();
  }
}
