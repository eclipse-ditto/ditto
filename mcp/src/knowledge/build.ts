import { existsSync } from "node:fs";
import type { AppConfig } from "../config/schema.js";
import type { Retriever } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { KnowledgeStore, IndexMeta } from "./knowledge-store.js";
import { SCHEMA_VERSION } from "./knowledge-store.js";
import { openStore } from "./store-factory.js";
import { KnowledgeService } from "./knowledge-service.js";
import { FtsRetriever } from "./fts-retriever.js";
import { VectorRetriever } from "./vector-retriever.js";
import { HybridRetriever } from "./hybrid-retriever.js";
import { buildIndex, metaFor } from "./build-index.js";
import { makeSources, makeEmbedder, type FactoryDeps } from "./factories.js";

export type KnowledgeDeps = FactoryDeps;

function metaMatches(meta: IndexMeta | null | undefined, config: AppConfig): boolean {
  if (!meta || meta.complete !== true || meta.schemaVersion !== SCHEMA_VERSION) return false;
  if (meta.retriever !== config.knowledge.retriever) return false;
  if (config.knowledge.retriever === "fts") return true;
  const e = config.knowledge.embedding;
  return meta.embeddingModel === e.model && meta.embeddingDim === e.dim;
}

export async function buildKnowledgeService(
  config: AppConfig,
  deps: KnowledgeDeps = {},
): Promise<KnowledgeService | undefined> {
  if (!config.knowledge.enabled) return undefined;

  const needsVectors = config.knowledge.retriever !== "fts";
  const embedder: EmbeddingProvider | undefined = needsVectors
    ? await makeEmbedder(config, deps)
    : undefined;

  try {
    // Non-sqlite stores (e.g. pgvector): open, check if populated + meta matches, serve; ELSE warn (no auto-build).
    if (config.knowledge.store.kind !== "sqlite") {
      const store = await openStore(config);
      try {
        if (await store.isPopulated()) {
          const meta = await store.getMeta();
          if (metaMatches(meta, config)) return new KnowledgeService(store, makeRetriever(config, store, embedder));
        }
        // No matching prebuilt index → warn and disable knowledge (do NOT auto-build for pgvector).
        process.stderr.write(
          `[ditto-mcp] no matching prebuilt index in the configured Postgres store; ` +
          `run \`ingest\` to build it — the server does not build pgvector indexes\n`,
        );
        await store.close();
        return undefined;
      } catch (err) {
        await store.close();
        process.stderr.write(`[ditto-mcp] knowledge init failed: ${String(err)}\n`);
        return undefined;
      }
    }

    // Sqlite: prebuilt file path.
    const path = config.knowledge.store.sqlite.path;
    // Prebuilt file: open and serve without rebuilding.
    if (path && existsSync(path)) {
      try {
        const store = await openStore(config, { path });
        if (await store.isPopulated()) {
          const meta = await store.getMeta();
          if (metaMatches(meta, config)) return new KnowledgeService(store, makeRetriever(config, store, embedder));
          process.stderr.write(
            `[ditto-mcp] prebuilt index at ${path} is incomplete or does not match config ` +
              `(retriever/model/dim/version); rebuilding in memory\n`,
          );
          await store.close();
        } else {
          await store.close();
        }
      } catch (err) {
        process.stderr.write(`[ditto-mcp] cannot open prebuilt index at ${path}: ${String(err)}; rebuilding in memory\n`);
      }
    }

    // Fallback: build in-memory (never writes the configured file).
    if (path) {
      process.stderr.write(
        `[ditto-mcp] no prebuilt index at ${path}; building in memory (run \`ingest\` to persist)\n`,
      );
    }
    const sources = makeSources(config, deps);
    if (sources.length === 0) return undefined;
    const store: KnowledgeStore = await openStore(config, { path: ":memory:" });
    try {
      await buildIndex(sources, store, embedder, metaFor(config, embedder));
    } catch (err) {
      await store.close();
      throw err;
    }
    return new KnowledgeService(store, makeRetriever(config, store, embedder));
  } catch (err) {
    process.stderr.write(
      `[ditto-mcp] knowledge init failed, disabling knowledge tools: ${String(err)}\n`,
    );
    return undefined;
  }
}

function makeRetriever(config: AppConfig, store: KnowledgeStore, embedder?: EmbeddingProvider): Retriever {
  const kind = config.knowledge.retriever;
  if (kind === "fts") return new FtsRetriever(store);
  if (!embedder) throw new Error("vector/hybrid retriever requires an embedder");
  const vector = new VectorRetriever(store, embedder);
  if (kind === "vector") return vector;
  return new HybridRetriever([new FtsRetriever(store), vector]);
}
