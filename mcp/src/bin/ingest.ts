#!/usr/bin/env node
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

import { loadConfig } from "../config/load.js";
import { buildIndex, metaFor } from "../knowledge/build-index.js";
import { makeSources, makeEmbedder } from "../knowledge/factories.js";
import { withIngestStore } from "../knowledge/ingest-store.js";
import type { EmbeddingProvider } from "../knowledge/embedding.js";

async function main(): Promise<void> {
  const config = loadConfig(process.env.DITTO_MCP_CONFIG);
  if (!config.knowledge.enabled) throw new Error("knowledge is disabled in config");

  const sources = makeSources(config);
  if (sources.length === 0) throw new Error("no knowledge sources enabled");

  let embedder: EmbeddingProvider | undefined;
  if (config.knowledge.retriever !== "fts") {
    embedder = await makeEmbedder(config);
  }

  await withIngestStore(config, (store) =>
    buildIndex(sources, store, embedder, metaFor(config, embedder)),
  );
  const dest = config.knowledge.store.kind === "sqlite" ? config.knowledge.store.sqlite.path : config.knowledge.store.kind;
  process.stderr.write(`[ditto-mcp ingest] done: ${dest}\n`);
}

main().catch((err) => {
  process.stderr.write(`[ditto-mcp ingest] fatal: ${err instanceof Error ? err.stack : String(err)}\n`);
  process.exit(1);
});
