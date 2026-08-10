import type { Chunk, KnowledgeSource, Retriever } from "./types.js";

export class KnowledgeService {
  private initialized = false;
  constructor(
    private readonly sources: KnowledgeSource[],
    private readonly retriever: Retriever,
  ) {}

  async init(signal?: AbortSignal): Promise<void> {
    if (this.initialized) return;
    for (const source of this.sources) {
      const chunks = await source.loadChunks(signal);
      await this.retriever.add(chunks);
    }
    this.initialized = true;
  }

  async search(query: string, k: number): Promise<Chunk[]> {
    return await this.retriever.search(query, k);
  }

  async getChunk(id: string): Promise<Chunk | undefined> {
    return await this.retriever.getChunk(id);
  }
}
