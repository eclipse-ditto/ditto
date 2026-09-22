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

import { readdirSync, readFileSync } from "node:fs";
import { join, basename, extname } from "node:path";
import type { Chunk, KnowledgeSource } from "./types.js";
import { chunkMarkdown } from "./chunker.js";

export interface LocalDirSourceOptions {
  dirs: string[];
  id?: string;
  chunkOptions?: { maxChars?: number; overlap?: number };
}

const MD_EXT = new Set([".md", ".markdown"]);

export class LocalDirSource implements KnowledgeSource {
  readonly id: string;
  private readonly opts: LocalDirSourceOptions;

  constructor(opts: LocalDirSourceOptions) {
    this.opts = opts;
    this.id = opts.id ?? "local";
  }

  async loadChunks(): Promise<Chunk[]> {
    const files: string[] = [];
    for (const dir of this.opts.dirs) {
      try {
        files.push(...walk(dir));
      } catch (err) {
        // Skip an unreadable dir but keep loading the rest.
        process.stderr.write(
          `[ditto-mcp] local-dir-source: cannot read ${dir}: ${String(err)}\n`,
        );
      }
    }
    const chunks: Chunk[] = [];
    for (const file of files) {
      const md = readFileSync(file, "utf8");
      chunks.push(
        ...chunkMarkdown(md, {
          source: this.id,
          title: basename(file),
          cite: file,
          maxChars: this.opts.chunkOptions?.maxChars,
          overlap: this.opts.chunkOptions?.overlap,
        }),
      );
    }
    return chunks.map((c, i) => ({ ...c, id: `${this.id}#${i}` }));
  }
}

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (MD_EXT.has(extname(entry.name).toLowerCase())) out.push(full);
  }
  return out.sort(); // deterministic order
}
