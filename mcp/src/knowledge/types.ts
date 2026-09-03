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
  // "anchor" = matched by the retriever; "context" = a positional neighbor pulled
  // in by expansion. Absent means anchor (pre-expansion results).
  role?: "anchor" | "context";
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
