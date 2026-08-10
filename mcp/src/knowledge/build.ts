import type { AppConfig } from "../config/schema.js";
import type { KnowledgeSource } from "./types.js";
import { KnowledgeService } from "./knowledge-service.js";
import { FtsRetriever } from "./fts-retriever.js";
import { PublicSource, type FetchFn } from "./public-source.js";

export interface KnowledgeDeps {
  fetchFn?: FetchFn;
}

/** Build + init the knowledge service once, or return undefined if disabled/unavailable.
 *  Never throws: on init failure it logs a warning and returns undefined (graceful degradation). */
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
  if (sources.length === 0) return undefined;
  const service = new KnowledgeService(sources, new FtsRetriever());
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
