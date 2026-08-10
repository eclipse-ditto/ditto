import type { Chunk } from "./types.js";

export interface ChunkOptions {
  source: string;
  title: string;
  cite: string;
  maxChars?: number;
  overlap?: number;
}

/**
 * Split markdown into size-bounded chunks. Paragraphs (blank-line separated)
 * are packed into windows of at most `maxChars`; a paragraph longer than
 * `maxChars` is hard-split with `overlap` characters carried between pieces.
 * Deterministic: same input yields identical chunks with ids `${source}#${n}`.
 */
export function chunkMarkdown(md: string, opts: ChunkOptions): Chunk[] {
  const maxChars = opts.maxChars ?? 1000;
  const overlap = opts.overlap ?? 150;
  if (overlap < 0) throw new Error(`overlap must be >= 0 (got ${overlap})`);
  if (overlap >= maxChars) {
    throw new Error(`overlap (${overlap}) must be less than maxChars (${maxChars})`);
  }
  const paragraphs = md
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);

  const pieces: string[] = [];
  let buf = "";
  const flush = () => {
    if (buf.trim().length > 0) pieces.push(buf.trim());
    buf = "";
  };

  for (const para of paragraphs) {
    if (para.length > maxChars) {
      flush();
      let start = 0;
      while (start < para.length) {
        const end = Math.min(start + maxChars, para.length);
        pieces.push(para.slice(start, end).trim());
        if (end >= para.length) break;
        start = end - overlap;
        if (start < 0) start = 0;
      }
      continue;
    }
    if (buf.length + para.length + 2 > maxChars) flush();
    buf = buf.length === 0 ? para : `${buf}\n\n${para}`;
  }
  flush();

  return pieces.map((text, n) => ({
    id: `${opts.source}#${n}`,
    source: opts.source,
    title: opts.title,
    text,
    cite: opts.cite,
  }));
}
