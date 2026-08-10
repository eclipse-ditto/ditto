import type { Chunk, Retriever, RetrievedChunk } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { VectorStore } from "./vector-store.js";

export class VectorRetriever implements Retriever {
  readonly kind = "vector";
  private readonly chunks = new Map<string, Chunk>();

  constructor(
    private readonly embedder: EmbeddingProvider,
    private readonly store: VectorStore,
  ) {}

  async add(chunks: Chunk[]): Promise<void> {
    if (chunks.length === 0) return;
    const vectors = await this.embedder.embed(chunks.map((c) => c.text));
    await this.store.upsert(
      chunks.map((c, i) => ({ id: c.id, vector: vectors[i] })),
    );
    for (const c of chunks) this.chunks.set(c.id, c);
  }

  async search(query: string, k: number): Promise<RetrievedChunk[]> {
    const [vector] = await this.embedder.embed([query]);
    const hits = await this.store.searchByVector(vector, k);
    const out: RetrievedChunk[] = [];
    for (const hit of hits) {
      const c = this.chunks.get(hit.id);
      if (c) out.push({ chunk: c, matchedBy: ["vector"] });
    }
    return out;
  }

  async getChunk(id: string): Promise<Chunk | undefined> {
    return this.chunks.get(id);
  }
}
