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

import type { RetrievedChunk, Retriever } from "./types.js";
import type { EmbeddingProvider } from "./embedding.js";
import type { KnowledgeStore } from "./knowledge-store.js";

export class VectorRetriever implements Retriever {
  readonly kind = "vector";
  constructor(
    private readonly store: KnowledgeStore,
    private readonly embedder: EmbeddingProvider,
  ) {}

  async search(query: string, k: number): Promise<RetrievedChunk[]> {
    const [vector] = await this.embedder.embed([query]);
    const hits = await this.store.vectorSearch(vector, k);
    const out: RetrievedChunk[] = [];
    for (const hit of hits) {
      const chunk = await this.store.getChunk(hit.id);
      if (chunk) out.push({ chunk, matchedBy: ["vector"] });
    }
    return out;
  }
}
