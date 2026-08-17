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

import { existsSync, renameSync, rmSync } from "node:fs";
import type { AppConfig } from "../config/schema.js";
import type { KnowledgeStore } from "./knowledge-store.js";
import { openStore } from "./store-factory.js";

export async function withIngestStore(
  config: AppConfig,
  fn: (store: KnowledgeStore) => Promise<void>,
): Promise<void> {
  const kind = config.knowledge.store.kind;
  if (kind === "sqlite") {
    const path = config.knowledge.store.sqlite.path;
    if (!path) throw new Error("knowledge.store.sqlite.path is required for ingest");
    const tmp = `${path}.tmp`;
    if (existsSync(tmp)) rmSync(tmp, { force: true });
    const store = await openStore(config, { path: tmp });
    try {
      await fn(store);
      await store.close();
    } catch (err) {
      await store.close();
      rmSync(tmp, { force: true });
      throw err;
    }
    renameSync(tmp, path);
    return;
  }
  if (kind === "pgvector") {
    const store = await openStore(config);
    try {
      await store.reset();
      await fn(store);
    } finally {
      await store.close();
    }
    return;
  }
  throw new Error(`unsupported store kind for ingest: ${kind}`);
}
