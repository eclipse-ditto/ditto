import type { Chunk, KnowledgeSource } from "./types.js";
import { chunkMarkdown } from "./chunker.js";

export type FetchFn = (url: string, signal?: AbortSignal) => Promise<string>;

export interface PublicSourceOptions {
  url: string;
  fetchFn?: FetchFn;
  maxDocs?: number;
  chunkOptions?: { maxChars?: number; overlap?: number };
}

interface Entry {
  title: string;
  url: string;
}

const defaultFetch: FetchFn = async (url, signal) => {
  const timeout = AbortSignal.timeout(15000);
  const sig = signal ? AbortSignal.any([signal, timeout]) : timeout;
  const res = await fetch(url, { signal: sig });
  if (!res.ok) throw new Error(`fetch ${url} -> ${res.status}`);
  const text = await res.text();
  if (text.length > 5_000_000) {
    throw new Error(`fetch ${url} -> response too large (${text.length} bytes)`);
  }
  return text;
};

export class PublicSource implements KnowledgeSource {
  readonly id = "public";
  private readonly opts: PublicSourceOptions;
  private readonly fetchFn: FetchFn;

  constructor(opts: PublicSourceOptions) {
    this.opts = opts;
    this.fetchFn = opts.fetchFn ?? defaultFetch;
  }

  async loadChunks(signal?: AbortSignal): Promise<Chunk[]> {
    const index = await this.fetchFn(this.opts.url, signal);
    let entries = parseEntries(index, this.opts.url);
    if (this.opts.maxDocs !== undefined) {
      entries = entries.slice(0, this.opts.maxDocs);
    }
    const chunks: Chunk[] = [];
    for (const entry of entries) {
      let md: string;
      try {
        md = await this.fetchFn(entry.url, signal);
      } catch (err) {
        process.stderr.write(
          `[ditto-mcp] public-source: skipping ${entry.url}: ${String(err)}\n`,
        );
        continue;
      }
      chunks.push(
        ...chunkMarkdown(md, {
          source: this.id,
          title: entry.title,
          cite: entry.url,
          maxChars: this.opts.chunkOptions?.maxChars,
          overlap: this.opts.chunkOptions?.overlap,
        }),
      );
    }
    // Re-key ids to be unique across documents (chunker numbers per-doc).
    return chunks.map((c, i) => ({ ...c, id: `${this.id}#${i}` }));
  }
}

function parseEntries(index: string, baseUrl: string): Entry[] {
  const re = /- \[([^\]]+)\]\(([^)]+)\)/g;
  const entries: Entry[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(index)) !== null) {
    const title = m[1].trim();
    const resolved = new URL(m[2].trim(), baseUrl);
    // Guard against SSRF: only allow http/https schemes
    if (resolved.protocol !== "http:" && resolved.protocol !== "https:") {
      continue;
    }
    entries.push({ title, url: resolved.toString() });
  }
  return entries;
}
