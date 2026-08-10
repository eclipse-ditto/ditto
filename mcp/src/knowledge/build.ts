import type { AppConfig } from "../config/schema.js";
import type { KnowledgeSource, Retriever } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import { KnowledgeService } from "./knowledge-service.js";
import { FtsRetriever } from "./fts-retriever.js";
import { VectorRetriever } from "./vector-retriever.js";
import { HybridRetriever } from "./hybrid-retriever.js";
import { PublicSource, type FetchFn } from "./public-source.js";
import { LocalDirSource } from "./local-dir-source.js";

export interface KnowledgeDeps {
  fetchFn?: FetchFn;
  embeddingProvider?: EmbeddingProvider;
}

export async function buildKnowledgeService(
  config: AppConfig,
  deps: KnowledgeDeps = {},
): Promise<KnowledgeService | undefined> {
  if (!config.knowledge.enabled) return undefined;

  const sources: KnowledgeSource[] = [];
  if (config.knowledge.publicSource.enabled) {
    sources.push(
      new PublicSource({
        url: config.knowledge.publicSource.url,
        maxDocs: config.knowledge.publicSource.maxDocs,
        fetchFn: deps.fetchFn,
      }),
    );
  }
  if (config.knowledge.localDir.enabled && config.knowledge.localDir.path) {
    sources.push(
      new LocalDirSource({
        dir: config.knowledge.localDir.path,
        id: config.knowledge.localDir.id,
      }),
    );
  }
  if (sources.length === 0) return undefined;

  const retriever = await buildRetriever(config, deps);

  const service = new KnowledgeService(sources, retriever);
  try {
    await service.init();
  } catch (err) {
    process.stderr.write(
      `[ditto-mcp] knowledge init failed, disabling knowledge tools: ${String(err)}\n`,
    );
    return undefined;
  }
  return service;
}

async function buildRetriever(config: AppConfig, deps: KnowledgeDeps): Promise<Retriever> {
  const kind = config.knowledge.retriever;
  if (kind === "fts") return new FtsRetriever();

  const [{ LocalEmbeddings }, { SqliteVecStore }] = await Promise.all([
    import("./embedding.js"),
    import("./sqlite-vec-store.js"),
  ]);
  const emb = config.knowledge.embedding;
  const embedder: EmbeddingProvider =
    deps.embeddingProvider ??
    new LocalEmbeddings({
      model: emb.model,
      dim: emb.dim,
      modelPath: emb.modelPath,
      allowRemoteModels: emb.allowRemoteModels,
      cacheDir: emb.cacheDir,
      batchSize: emb.batchSize,
    });
  const store = new SqliteVecStore(embedder.dim);
  const vector = new VectorRetriever(embedder, store);

  if (kind === "vector") return vector;
  return new HybridRetriever([new FtsRetriever(), vector]);
}
