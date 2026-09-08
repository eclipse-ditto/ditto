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

import { pipeline, env, type FeatureExtractionPipeline } from "@huggingface/transformers";

export interface EmbeddingProvider {
  readonly dim: number;
  embed(texts: string[], signal?: AbortSignal): Promise<number[][]>;
}

export interface LocalEmbeddingsOptions {
  model?: string;
  dim?: number;
  modelPath?: string;
  allowRemoteModels?: boolean;
  cacheDir?: string;
  /** Texts embedded per forward pass. Bounds peak memory; default 32. */
  batchSize?: number;
}

const DEFAULT_MODEL = "Xenova/bge-small-en-v1.5";
const DEFAULT_DIM = 384;
const DEFAULT_BATCH_SIZE = 32;

/** Split `items` into consecutive batches of at most `size`. */
export function toBatches<T>(items: T[], size: number): T[][] {
  if (size <= 0) throw new Error(`batch size must be positive (got ${size})`);
  const out: T[][] = [];
  for (let i = 0; i < items.length; i += size) {
    out.push(items.slice(i, i + size));
  }
  return out;
}

/**
 * Local ONNX embeddings via Transformers.js. The model weights are downloaded
 * from the HuggingFace hub on first use and cached to disk; set
 * `allowRemoteModels: false` + `modelPath` for offline/gated deployments.
 */
export class LocalEmbeddings implements EmbeddingProvider {
  readonly dim: number;
  private readonly model: string;
  private readonly batchSize: number;
  private extractorPromise?: Promise<FeatureExtractionPipeline>;

  constructor(opts: LocalEmbeddingsOptions = {}) {
    this.model = opts.model ?? DEFAULT_MODEL;
    this.dim = opts.dim ?? DEFAULT_DIM;
    this.batchSize = opts.batchSize ?? DEFAULT_BATCH_SIZE;
    if (opts.allowRemoteModels !== undefined) env.allowRemoteModels = opts.allowRemoteModels;
    if (opts.modelPath !== undefined) env.localModelPath = opts.modelPath;
    if (opts.cacheDir !== undefined) env.cacheDir = opts.cacheDir;
  }

  private extractor(): Promise<FeatureExtractionPipeline> {
    if (!this.extractorPromise) {
      // Type assertion needed due to complex union type from pipeline generic
      this.extractorPromise = pipeline("feature-extraction", this.model) as unknown as Promise<FeatureExtractionPipeline>;
    }
    return this.extractorPromise;
  }

  async embed(texts: string[]): Promise<number[][]> {
    if (texts.length === 0) return [];
    const extractor = await this.extractor();
    // Embed in bounded batches: a single forward pass over the whole corpus
    // allocates a tensor for every input at once and can exhaust memory.
    const out: number[][] = [];
    for (const batch of toBatches(texts, this.batchSize)) {
      const output = await extractor(batch, { pooling: "mean", normalize: true });
      out.push(...(output.tolist() as number[][]));
    }
    return out;
  }
}
