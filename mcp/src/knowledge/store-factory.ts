import type { AppConfig } from "../config/schema.js";
import type { KnowledgeStore } from "./knowledge-store.js";

const reason = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** Open a KnowledgeStore for the configured backend. `opts.path` overrides the
 *  sqlite path (e.g. ":memory:" for the in-memory fallback).
 *
 *  Backends are imported lazily so their native deps load only when selected:
 *  sqlite pulls better-sqlite3 + sqlite-vec, pgvector pulls pg. A missing dep
 *  surfaces as a clear error naming the fix, not a raw MODULE_NOT_FOUND. */
export async function openStore(
  config: AppConfig,
  opts: { path?: string } = {},
): Promise<KnowledgeStore> {
  const kind = config.knowledge.store.kind;
  if (kind === "sqlite") {
    const path = opts.path ?? config.knowledge.store.sqlite.path ?? ":memory:";
    let SqliteKnowledgeStore: typeof import("./sqlite-knowledge-store.js").SqliteKnowledgeStore;
    try {
      ({ SqliteKnowledgeStore } = await import("./sqlite-knowledge-store.js"));
    } catch (e) {
      throw new Error(
        `sqlite store requires better-sqlite3 + sqlite-vec, which failed to load: ${reason(e)}. ` +
          `Install them, or set knowledge.store.kind=pgvector.`,
      );
    }
    return new SqliteKnowledgeStore(path);
  }
  if (kind === "pgvector") {
    const { connectionString, table } = config.knowledge.store.pgvector;
    if (!connectionString) throw new Error("knowledge.store.pgvector.connectionString is required");
    let PgKnowledgeStore: typeof import("./pg-knowledge-store.js").PgKnowledgeStore;
    try {
      ({ PgKnowledgeStore } = await import("./pg-knowledge-store.js"));
    } catch (e) {
      throw new Error(`pgvector store requires the 'pg' package, which failed to load: ${reason(e)}.`);
    }
    return PgKnowledgeStore.connect(connectionString, table);
  }
  throw new Error(`unsupported knowledge.store.kind: ${kind}`);
}
