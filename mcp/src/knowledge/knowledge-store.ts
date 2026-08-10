import type { Chunk } from "./types.js";

export const SCHEMA_VERSION = 1;

export interface IndexMeta {
  schemaVersion: number;
  retriever: string;
  embeddingModel?: string;
  embeddingDim?: number;
  complete: boolean;
}

/** Holds chunks, a keyword (FTS) index, and vectors. Backend-agnostic
 *  (SqliteKnowledgeStore now; PgKnowledgeStore in P2c). */
export interface KnowledgeStore {
  addChunks(chunks: Chunk[]): Promise<void>;
  getChunk(id: string): Promise<Chunk | undefined>;
  ftsSearch(query: string, k: number): Promise<string[]>;
  ensureVectorTable(dim: number): Promise<void>;
  upsertVectors(items: { id: string; vector: number[] }[]): Promise<void>;
  vectorSearch(vector: number[], k: number): Promise<{ id: string; distance: number }[]>;
  isPopulated(): Promise<boolean>;
  hasVectors(): Promise<boolean>;
  setMeta(meta: IndexMeta): Promise<void>;
  getMeta(): Promise<IndexMeta | undefined>;
  reset(): Promise<void>;
  close(): Promise<void>;
}
