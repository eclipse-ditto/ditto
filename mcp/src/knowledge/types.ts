export interface Chunk {
  id: string;
  source: string;
  title: string;
  text: string;
  cite: string;
}

export interface RetrievedChunk {
  chunk: Chunk;
  matchedBy: string[]; // leaf retriever kinds, e.g. ["fts"], ["vector"], ["fts","vector"]
}

/** A corpus provider: yields chunks. Does not search. */
export interface KnowledgeSource {
  id: string;
  loadChunks(signal?: AbortSignal): Promise<Chunk[]>;
}

/** Indexes chunks and searches them. */
export interface Retriever {
  readonly kind: string;
  search(query: string, k: number): Promise<RetrievedChunk[]>;
}
