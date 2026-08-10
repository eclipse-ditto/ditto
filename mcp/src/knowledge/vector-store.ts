export interface VectorRecord {
  id: string;
  vector: number[];
}

export interface VectorHit {
  id: string;
  distance: number;
}

export interface VectorStore {
  upsert(records: VectorRecord[]): Promise<void>;
  searchByVector(vector: number[], k: number): Promise<VectorHit[]>;
  close(): void;
}
