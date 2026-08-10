export interface Chunk {
  id: string;
  source: string;
  title: string;
  text: string;
  cite: string;
}

/** A corpus provider: yields chunks. Does not search. */
export interface KnowledgeSource {
  id: string;
  loadChunks(signal?: AbortSignal): Promise<Chunk[]>;
}

/** Indexes chunks and searches them. */
export interface Retriever {
  add(chunks: Chunk[]): Promise<void>;
  search(query: string, k: number): Promise<Chunk[]>;
  getChunk(id: string): Promise<Chunk | undefined>;
}
