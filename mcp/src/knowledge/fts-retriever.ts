import type { RetrievedChunk, Retriever } from "./types.js";
import type { KnowledgeStore } from "./knowledge-store.js";

export class FtsRetriever implements Retriever {
  readonly kind = "fts";
  constructor(private readonly store: KnowledgeStore) {}

  async search(query: string, k: number): Promise<RetrievedChunk[]> {
    const ids = await this.store.ftsSearch(query, k);
    const out: RetrievedChunk[] = [];
    for (const id of ids) {
      const chunk = await this.store.getChunk(id);
      if (chunk) out.push({ chunk, matchedBy: ["fts"] });
    }
    return out;
  }
}
