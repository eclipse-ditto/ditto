import pg from "pg";
import type { Chunk } from "./types.js";
import type { IndexMeta, KnowledgeStore } from "./knowledge-store.js";

export class PgKnowledgeStore implements KnowledgeStore {
  private constructor(
    private readonly pool: pg.Pool,
    private readonly t: string, // sanitized table prefix
    private vecReady: boolean,
  ) {}

  static async connect(connectionString: string, table: string): Promise<PgKnowledgeStore> {
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(table)) {
      throw new Error(`invalid pgvector table prefix: ${table}`);
    }
    if (table.length > 50) {
      throw new Error(`table prefix too long (max 50 chars): ${table}`);
    }
    const pool = new pg.Pool({ connectionString });
    try {
      await pool.query("CREATE EXTENSION IF NOT EXISTS vector");
      await pool.query(
        `CREATE TABLE IF NOT EXISTS ${table}_chunks (
           id TEXT PRIMARY KEY, source TEXT, title TEXT, text TEXT, cite TEXT,
           tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(title,'') || ' ' || coalesce(text,''))) STORED
         )`,
      );
      await pool.query(`CREATE INDEX IF NOT EXISTS ${table}_chunks_tsv ON ${table}_chunks USING GIN (tsv)`);
      await pool.query(`CREATE TABLE IF NOT EXISTS ${table}_meta (id INT PRIMARY KEY CHECK (id = 1), json JSONB NOT NULL)`);
      const vec = await pool.query(
        "SELECT to_regclass($1) AS reg",
        [`${table}_vec`],
      );
      return new PgKnowledgeStore(pool, table, vec.rows[0].reg !== null);
    } catch (e) {
      await pool.end();
      throw e;
    }
  }

  async addChunks(chunks: Chunk[]): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      for (const c of chunks) {
        await client.query(
          `INSERT INTO ${this.t}_chunks (id, source, title, text, cite) VALUES ($1,$2,$3,$4,$5)
           ON CONFLICT (id) DO UPDATE SET source=$2, title=$3, text=$4, cite=$5`,
          [c.id, c.source, c.title, c.text, c.cite],
        );
      }
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK").catch(() => {});
      throw e;
    } finally {
      client.release();
    }
  }

  async getChunk(id: string): Promise<Chunk | undefined> {
    const r = await this.pool.query(
      `SELECT id, source, title, text, cite FROM ${this.t}_chunks WHERE id = $1`,
      [id],
    );
    return r.rows[0] as Chunk | undefined;
  }

  async ftsSearch(query: string, k: number): Promise<string[]> {
    const tokens = [...query.matchAll(/[\p{L}\p{N}]+/gu)].map((m) => m[0]);
    if (tokens.length === 0) return [];
    const tsquery = tokens.join(" | ");
    const r = await this.pool.query(
      `SELECT id FROM ${this.t}_chunks
       WHERE tsv @@ to_tsquery('english', $1)
       ORDER BY ts_rank(tsv, to_tsquery('english', $1)) DESC, id
       LIMIT $2`,
      [tsquery, k],
    );
    return r.rows.map((row) => row.id as string);
  }

  async ensureVectorTable(dim: number): Promise<void> {
    if (this.vecReady) return;
    if (!Number.isInteger(dim) || dim <= 0) throw new Error(`vector dim must be positive (got ${dim})`);
    await this.pool.query(
      `CREATE TABLE IF NOT EXISTS ${this.t}_vec (id TEXT PRIMARY KEY, embedding vector(${dim}))`,
    );
    await this.pool.query(
      `CREATE INDEX IF NOT EXISTS ${this.t}_vec_idx ON ${this.t}_vec USING hnsw (embedding vector_cosine_ops)`,
    );
    this.vecReady = true;
  }

  async upsertVectors(items: { id: string; vector: number[] }[]): Promise<void> {
    if (!this.vecReady) throw new Error("call ensureVectorTable(dim) before upsertVectors");
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      for (const it of items) {
        await client.query(
          `INSERT INTO ${this.t}_vec (id, embedding) VALUES ($1, $2)
           ON CONFLICT (id) DO UPDATE SET embedding = $2`,
          [it.id, JSON.stringify(it.vector)],
        );
      }
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK").catch(() => {});
      throw e;
    } finally {
      client.release();
    }
  }

  async vectorSearch(vector: number[], k: number): Promise<{ id: string; distance: number }[]> {
    if (!this.vecReady) return [];
    const r = await this.pool.query(
      `SELECT id, embedding <=> $1 AS distance FROM ${this.t}_vec ORDER BY embedding <=> $1 LIMIT $2`,
      [JSON.stringify(vector), k],
    );
    return r.rows.map((row) => ({ id: row.id as string, distance: Number(row.distance) }));
  }

  async isPopulated(): Promise<boolean> {
    const r = await this.pool.query(`SELECT EXISTS (SELECT 1 FROM ${this.t}_chunks) AS e`);
    return r.rows[0].e === true;
  }

  async hasVectors(): Promise<boolean> {
    if (!this.vecReady) return false;
    const r = await this.pool.query(`SELECT EXISTS (SELECT 1 FROM ${this.t}_vec) AS e`);
    return r.rows[0].e === true;
  }

  async setMeta(meta: IndexMeta): Promise<void> {
    await this.pool.query(
      `INSERT INTO ${this.t}_meta (id, json) VALUES (1, $1) ON CONFLICT (id) DO UPDATE SET json = $1`,
      [JSON.stringify(meta)],
    );
  }

  async getMeta(): Promise<IndexMeta | undefined> {
    const r = await this.pool.query(`SELECT json FROM ${this.t}_meta WHERE id = 1`);
    if (r.rows.length === 0) return undefined;
    try {
      const v = r.rows[0].json;
      return (typeof v === "string" ? JSON.parse(v) : v) as IndexMeta;
    } catch {
      return undefined;
    }
  }

  async reset(): Promise<void> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(`TRUNCATE ${this.t}_chunks`);
      await client.query(`DELETE FROM ${this.t}_meta`);
      await client.query(`DROP TABLE IF EXISTS ${this.t}_vec`);
      this.vecReady = false;
      await client.query("COMMIT");
    } catch (e) {
      await client.query("ROLLBACK").catch(() => {});
      throw e;
    } finally {
      client.release();
    }
  }

  async close(): Promise<void> {
    await this.pool.end();
  }
}
