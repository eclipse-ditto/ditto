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

import { describe, it, expect, beforeEach } from "vitest";
import { mkdtempSync, writeFileSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { LocalDirSource } from "./local-dir-source.js";

let dir: string;
beforeEach(() => {
  dir = mkdtempSync(join(tmpdir(), "ditto-corpus-"));
  writeFileSync(join(dir, "runbook.md"), "# Runbook\n\nRestart connectivity to clear the reconnect storm.");
  mkdirSync(join(dir, "sub"));
  writeFileSync(join(dir, "sub", "tuning.markdown"), "# Tuning\n\nDisable Netty leak detection.");
  writeFileSync(join(dir, "ignore.txt"), "not markdown");
});

describe("LocalDirSource", () => {
  it("loads and chunks markdown files recursively, ignoring non-markdown", async () => {
    const src = new LocalDirSource({ dirs: [dir] });
    const chunks = await src.loadChunks();
    expect(src.id).toBe("local");
    const texts = chunks.map((c) => c.text).join("\n");
    expect(texts).toContain("reconnect storm");
    expect(texts).toContain("Netty leak detection");
    expect(texts).not.toContain("not markdown");
    expect(chunks.every((c) => c.source === "local")).toBe(true);
    chunks.forEach((c, i) => expect(c.id).toBe(`local#${i}`));
  });

  it("merges markdown across multiple dirs with unique sequential ids", async () => {
    const dir2 = mkdtempSync(join(tmpdir(), "ditto-corpus2-"));
    writeFileSync(join(dir2, "extra.md"), "# Extra\n\nSecond corpus entry about sharding.");
    const src = new LocalDirSource({ dirs: [dir, dir2] });
    const chunks = await src.loadChunks();
    const texts = chunks.map((c) => c.text).join("\n");
    expect(texts).toContain("reconnect storm");
    expect(texts).toContain("Netty leak detection");
    expect(texts).toContain("sharding");
    // ids stay unique + sequential across both dirs
    chunks.forEach((c, i) => expect(c.id).toBe(`local#${i}`));
    expect(new Set(chunks.map((c) => c.id)).size).toBe(chunks.length);
  });

  it("skips a missing dir but still loads the readable ones (non-fatal)", async () => {
    const src = new LocalDirSource({ dirs: [join(dir, "does-not-exist"), dir] });
    const chunks = await src.loadChunks();
    const texts = chunks.map((c) => c.text).join("\n");
    expect(texts).toContain("reconnect storm");
  });

  it("returns [] when all dirs are missing (non-fatal)", async () => {
    const src = new LocalDirSource({ dirs: [join(dir, "does-not-exist")] });
    expect(await src.loadChunks()).toEqual([]);
  });

  it("returns [] for an empty dirs list", async () => {
    const src = new LocalDirSource({ dirs: [] });
    expect(await src.loadChunks()).toEqual([]);
  });
});
