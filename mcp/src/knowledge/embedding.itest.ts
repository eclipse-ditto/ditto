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
import { LocalEmbeddings } from "./embedding.js";

// Loads the real bge model (network on first run). Skipped unless RUN_EMBED_ITEST is set.
const run = process.env.RUN_EMBED_ITEST ? describe : describe.skip;

run("LocalEmbeddings (real model)", () => {
  it("produces 384-dim vectors and ranks paraphrase above unrelated", async () => {
    const emb = new LocalEmbeddings();
    const [a, b, c] = await emb.embed([
      "why does it die on reconnect",
      "the Netty leak causes an out of memory crash on reconnect",
      "policies define access control for things",
    ]);
    expect(a).toHaveLength(384);
    const cos = (x: number[], y: number[]) => x.reduce((s, xi, i) => s + xi * y[i], 0);
    expect(cos(a, b)).toBeGreaterThan(cos(a, c));
  }, 120000);
});
