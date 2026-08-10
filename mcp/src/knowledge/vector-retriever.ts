import type { RetrievedChunk, Retriever } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { KnowledgeStore } from "./knowledge-store.js";

export class VectorRetriever implements Retriever {
  readonly kind = "vector";
  constructor(
    private readonly store: KnowledgeStore,
    private readonly embedder: EmbeddingProvider,
  ) {}

  async search(query: string, k: number): Promise<RetrievedChunk[]> {
    const [vector] = await this.embedder.embed([query]);
    const hits = await this.store.vectorSearch(vector, k);
    const out: RetrievedChunk[] = [];
    for (const hit of hits) {
      const chunk = await this.store.getChunk(hit.id);
      if (chunk) out.push({ chunk, matchedBy: ["vector"] });
    }
    return out;
  }
}
