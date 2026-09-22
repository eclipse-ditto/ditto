/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { KnowledgeSource } from "./types.js";
import type { KnowledgeStore } from "./knowledge-store.js";
import type { EmbeddingProvider } from "./embedding.js";
import { SCHEMA_VERSION } from "./knowledge-store.js";
import type { AppConfig } from "../config/schema.js";

export function metaFor(
  config: AppConfig,
  embedder?: EmbeddingProvider,
): { retriever: string; embeddingModel?: string; embeddingDim?: number } {
  return {
    retriever: config.knowledge.retriever,
    embeddingModel: embedder ? config.knowledge.embedding.model : undefined,
    embeddingDim: embedder ? config.knowledge.embedding.dim : undefined,
  };
}

export async function buildIndex(
  sources: KnowledgeSource[],
  store: KnowledgeStore,
  embedder?: EmbeddingProvider,
  meta?: { retriever: string; embeddingModel?: string; embeddingDim?: number },
  signal?: AbortSignal,
): Promise<void> {
  if (embedder) await store.ensureVectorTable(embedder.dim);
  for (const source of sources) {
    const chunks = await source.loadChunks(signal);
    await store.addChunks(chunks);
    if (embedder && chunks.length > 0) {
      const vectors = await embedder.embed(chunks.map((c) => c.text), signal);
      await store.upsertVectors(chunks.map((c, i) => ({ id: c.id, vector: vectors[i] })));
    }
  }
  await store.setMeta({
    schemaVersion: SCHEMA_VERSION,
    retriever: meta?.retriever ?? "fts",
    embeddingModel: meta?.embeddingModel,
    embeddingDim: meta?.embeddingDim,
    complete: true,
  });
}
