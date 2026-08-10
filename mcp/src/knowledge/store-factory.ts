import type { AppConfig } from "../config/schema.js";
import type { KnowledgeStore } from "./knowledge-store.js";
import { SqliteKnowledgeStore } from "./sqlite-knowledge-store.js";

/** Open a KnowledgeStore for the configured backend. `opts.path` overrides the
 *  sqlite path (e.g. ":memory:" for the in-memory fallback). */
export async function openStore(
  config: AppConfig,
  opts: { path?: string } = {},
): Promise<KnowledgeStore> {
  const kind = config.knowledge.store.kind;
  if (kind === "sqlite") {
    const path = opts.path ?? config.knowledge.store.sqlite.path ?? ":memory:";
    return new SqliteKnowledgeStore(path);
  }
  if (kind === "pgvector") {
    const { connectionString, table } = config.knowledge.store.pgvector;
    if (!connectionString) throw new Error("knowledge.store.pgvector.connectionString is required");
    const { PgKnowledgeStore } = await import("./pg-knowledge-store.js");
    return PgKnowledgeStore.connect(connectionString, table);
  }
  throw new Error(`unsupported knowledge.store.kind: ${kind}`);
}
