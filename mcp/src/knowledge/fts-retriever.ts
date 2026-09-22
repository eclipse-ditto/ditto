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
import type { KnowledgeStore } from "./knowledge-store.js";

export class FtsRetriever implements Retriever {
  readonly kind = "fts";
  constructor(private readonly store: KnowledgeStore) {}

  async search(query: string, k: number): Promise<RetrievedChunk[]> {
    const ids = await this.store.ftsSearch(query, k);
    const out: RetrievedChunk[] = [];
    for (const id of ids) {
      const chunk = await this.store.getChunk(id);
      if (chunk) out.push({ chunk, matchedBy: ["fts"] });
    }
    return out;
  }
}
