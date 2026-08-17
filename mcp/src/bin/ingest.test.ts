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

import { describe, it, expect } from "vitest";
import { mkdtempSync, writeFileSync, existsSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";
import { SqliteKnowledgeStore } from "../knowledge/sqlite-knowledge-store.js";

const here = dirname(fileURLToPath(import.meta.url));
const entry = resolve(here, "ingest.ts");

describe("ingest CLI (spawn e2e)", () => {
  it("builds a persisted fts index from a local dir corpus", async () => {
    const corpus = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    writeFileSync(join(corpus, "a.md"), "# Reconnect\n\nNetty leak out of memory crash.");
    const idxDir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(idxDir, "index.db");
    const cfgPath = join(idxDir, "config.json");
    writeFileSync(cfgPath, JSON.stringify({
      knowledge: {
        retriever: "fts",
        publicSource: { enabled: false },
        localDir: { enabled: true, path: corpus },
        store: { kind: "sqlite", sqlite: { path } },
      },
    }));

    execFileSync(process.execPath, ["--import", "tsx", entry], {
      env: { ...process.env, DITTO_MCP_CONFIG: cfgPath },
      stdio: "pipe",
    });

    expect(existsSync(path)).toBe(true);
    const store = new SqliteKnowledgeStore(path);
    expect(await store.isPopulated()).toBe(true);
    expect((await store.ftsSearch("memory", 5)).length).toBeGreaterThan(0);
    await store.close();
  }, 30000);

  it("re-ingest is a clean rebuild (no orphan chunks from a larger prior run)", async () => {
    const corpus = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
    const idxDir = mkdtempSync(join(tmpdir(), "ditto-idx-"));
    const path = join(idxDir, "index.db");
    const cfgPath = join(idxDir, "config.json");
    const cfg = (files: number) => {
      // (re)write corpus with `files` docs
      for (let i = 0; i < files; i++) writeFileSync(join(corpus, `d${i}.md`), `# D${i}\n\ndoc ${i} netty`);
      writeFileSync(cfgPath, JSON.stringify({
        knowledge: { retriever: "fts", publicSource: { enabled: false },
          localDir: { enabled: true, path: corpus }, store: { kind: "sqlite", sqlite: { path } } },
      }));
    };
    const run = () => execFileSync(process.execPath, ["--import", "tsx", entry],
      { env: { ...process.env, DITTO_MCP_CONFIG: cfgPath }, stdio: "pipe" });

    cfg(5); run();
    // shrink corpus: remove all, write 1 doc
    for (let i = 0; i < 5; i++) rmSync(join(corpus, `d${i}.md`), { force: true });
    cfg(1); run();

    const store = new SqliteKnowledgeStore(path);
    // Only the single remaining doc's chunk id ("local#0") should exist.
    expect(await store.getChunk("local#0")).toBeDefined();
    expect(await store.getChunk("local#4")).toBeUndefined(); // orphan from the 5-doc run is gone
    await store.close();
  }, 30000);
});
