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
    const src = new LocalDirSource({ dir });
    const chunks = await src.loadChunks();
    expect(src.id).toBe("local");
    const texts = chunks.map((c) => c.text).join("\n");
    expect(texts).toContain("reconnect storm");
    expect(texts).toContain("Netty leak detection");
    expect(texts).not.toContain("not markdown");
    expect(chunks.every((c) => c.source === "local")).toBe(true);
    chunks.forEach((c, i) => expect(c.id).toBe(`local#${i}`));
  });

  it("returns [] for a missing directory (non-fatal)", async () => {
    const src = new LocalDirSource({ dir: join(dir, "does-not-exist") });
    expect(await src.loadChunks()).toEqual([]);
  });
});
