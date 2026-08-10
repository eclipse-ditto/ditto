import type { Chunk, RetrievedChunk, Retriever } from "./types.js";
import type { KnowledgeStore } from "./knowledge-store.js";

export class KnowledgeService {
  constructor(
    private readonly store: KnowledgeStore,
    private readonly retriever: Retriever,
  ) {}

  search(query: string, k: number): Promise<RetrievedChunk[]> {
    return this.retriever.search(query, k);
  }

  getChunk(id: string): Promise<Chunk | undefined> {
    return this.store.getChunk(id);
  }
}
