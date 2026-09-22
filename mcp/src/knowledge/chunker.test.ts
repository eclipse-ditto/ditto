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
import { chunkMarkdown } from "./chunker.js";

const meta = { source: "doc", title: "Doc", cite: "https://x/doc" };

describe("chunkMarkdown", () => {
  it("returns one chunk for short input, with stable id and metadata", () => {
    const chunks = chunkMarkdown("# Title\n\nShort body.", meta);
    expect(chunks).toHaveLength(1);
    expect(chunks[0].id).toBe("doc#0");
    expect(chunks[0].source).toBe("doc");
    expect(chunks[0].title).toBe("Doc");
    expect(chunks[0].cite).toBe("https://x/doc");
    expect(chunks[0].text).toContain("Short body.");
  });

  it("splits long input into multiple size-bounded chunks with sequential ids", () => {
    const long = "para. ".repeat(600); // ~3600 chars
    const chunks = chunkMarkdown(long, { ...meta, maxChars: 1000, overlap: 100 });
    expect(chunks.length).toBeGreaterThan(1);
    chunks.forEach((c, i) => expect(c.id).toBe(`doc#${i}`));
    chunks.forEach((c) => expect(c.text.length).toBeLessThanOrEqual(1000));
  });

  it("is deterministic (same input -> identical chunks)", () => {
    const a = chunkMarkdown("# H\n\n" + "word ".repeat(500), meta);
    const b = chunkMarkdown("# H\n\n" + "word ".repeat(500), meta);
    expect(a.length).toBeGreaterThan(1);
    expect(a).toEqual(b);
  });

  it("returns no chunks for empty/whitespace input", () => {
    expect(chunkMarkdown("   \n\n ", meta)).toEqual([]);
  });

  it("throws when overlap >= maxChars", () => {
    expect(() => chunkMarkdown("x".repeat(50), { ...meta, maxChars: 100, overlap: 100 })).toThrow(/overlap/);
  });
});
