import type { AppConfig } from "../config/schema.js";
import type { KnowledgeSource } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import { PublicSource, type FetchFn } from "./public-source.js";
import { LocalDirSource } from "./local-dir-source.js";

export interface FactoryDeps {
  fetchFn?: FetchFn;
  embeddingProvider?: EmbeddingProvider;
}

export function makeSources(config: AppConfig, deps?: FactoryDeps): KnowledgeSource[] {
  const sources: KnowledgeSource[] = [];
  const chunkOptions = config.knowledge.chunk;
  if (config.knowledge.publicSource.enabled) {
    sources.push(
      new PublicSource({
        url: config.knowledge.publicSource.url,
        maxDocs: config.knowledge.publicSource.maxDocs,
        fetchFn: deps?.fetchFn,
        chunkOptions,
      }),
    );
  }
  if (config.knowledge.localDir.enabled && config.knowledge.localDir.path) {
    sources.push(
      new LocalDirSource({
        dir: config.knowledge.localDir.path,
        id: config.knowledge.localDir.id,
        chunkOptions,
      }),
    );
  }
  return sources;
}

export async function makeEmbedder(config: AppConfig, deps?: FactoryDeps): Promise<EmbeddingProvider> {
  if (deps?.embeddingProvider) return deps.embeddingProvider;
  // Lazy: local embeddings pull @huggingface/transformers (onnxruntime-node).
  // Only needed for retriever=vector|hybrid without an injected provider.
  let LocalEmbeddings: typeof import("./embedding.js").LocalEmbeddings;
  try {
    ({ LocalEmbeddings } = await import("./embedding.js"));
  } catch (err) {
    const reason = err instanceof Error ? err.message : String(err);
    throw new Error(
      `local embeddings require @huggingface/transformers, which failed to load: ${reason}. ` +
        `Install it, inject deps.embeddingProvider, or use retriever=fts (no embeddings).`,
    );
  }
  const e = config.knowledge.embedding;
  return new LocalEmbeddings({
    model: e.model, dim: e.dim, modelPath: e.modelPath,
    allowRemoteModels: e.allowRemoteModels, cacheDir: e.cacheDir, batchSize: e.batchSize,
  });
}
