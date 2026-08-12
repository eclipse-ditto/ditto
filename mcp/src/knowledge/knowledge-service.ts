import type { Chunk, RetrievedChunk, Retriever } from "./types.js";
import type { KnowledgeStore } from "./knowledge-store.js";

export interface SearchOptions {
  /** Positional neighbors to pull in around each anchor (same document). 0 = anchors only. */
  context?: number;
}

/** Parse a chunk id of the form `${source}#${ordinal}`. Returns null if the id
 *  has no numeric ordinal suffix (such chunks can't be expanded positionally). */
function parseId(id: string): { source: string; ord: number } | null {
  const h = id.lastIndexOf("#");
  if (h < 0) return null;
  const ord = Number(id.slice(h + 1));
  if (!Number.isInteger(ord)) return null;
  return { source: id.slice(0, h), ord };
}

interface Rec {
  chunk: Chunk;
  matchedBy: string[];
  isAnchor: boolean;
  rank: number; // best (lowest) anchor rank this chunk belongs to; drives ordering
  ord: number | null;
}

export class KnowledgeService {
  constructor(
    private readonly store: KnowledgeStore,
    private readonly retriever: Retriever,
  ) {}

  async search(query: string, k: number, opts: SearchOptions = {}): Promise<RetrievedChunk[]> {
    const anchors = await this.retriever.search(query, k);
    const context = opts.context ?? 0;
    if (context <= 0 || anchors.length === 0) {
      return anchors.map((a) => ({ ...a, role: "anchor" as const }));
    }
    return this.expand(anchors, context);
  }

  getChunk(id: string): Promise<Chunk | undefined> {
    return this.store.getChunk(id);
  }

  /** Retriever-agnostic neighbor expansion: fetch ±context same-document chunks
   *  around each anchor, dedup, and emit contiguous spans ordered by best anchor
   *  rank (ordinal order within a span). Neighbors are fetched by id, never
   *  re-scored; "same document" = identical `cite`, which also stops expansion at
   *  document boundaries even when ordinals are globally contiguous. */
  private async expand(anchors: RetrievedChunk[], context: number): Promise<RetrievedChunk[]> {
    const recs = new Map<string, Rec>();

    anchors.forEach((a, rank) => {
      recs.set(a.chunk.id, {
        chunk: a.chunk,
        matchedBy: a.matchedBy,
        isAnchor: true,
        rank,
        ord: parseId(a.chunk.id)?.ord ?? null,
      });
    });

    for (let rank = 0; rank < anchors.length; rank++) {
      const anchor = anchors[rank];
      const p = parseId(anchor.chunk.id);
      if (!p) continue;
      for (const dir of [-1, 1]) {
        for (let j = 1; j <= context; j++) {
          const nid = `${p.source}#${p.ord + dir * j}`;
          const existing = recs.get(nid);
          if (existing) {
            if (existing.chunk.cite !== anchor.chunk.cite) break; // boundary
            if (rank < existing.rank) existing.rank = rank;
            continue;
          }
          const c = await this.store.getChunk(nid);
          if (!c || c.cite !== anchor.chunk.cite) break; // boundary or missing
          recs.set(nid, { chunk: c, matchedBy: [], isAnchor: false, rank, ord: p.ord + dir * j });
        }
      }
    }

    return this.orderSegments([...recs.values()]);
  }

  /** Group recs by document (cite), split into contiguous ordinal runs, order
   *  runs by their best anchor rank, and flatten (ordinal order within a run). */
  private orderSegments(recs: Rec[]): RetrievedChunk[] {
    const byCite = new Map<string, Rec[]>();
    const loose: Rec[] = []; // chunks without an ordinal — emitted as singletons
    for (const r of recs) {
      if (r.ord === null) {
        loose.push(r);
        continue;
      }
      const arr = byCite.get(r.chunk.cite) ?? [];
      arr.push(r);
      byCite.set(r.chunk.cite, arr);
    }

    const segments: { rank: number; recs: Rec[] }[] = [];
    for (const arr of byCite.values()) {
      arr.sort((x, y) => (x.ord as number) - (y.ord as number));
      let seg: Rec[] = [];
      let prev: number | undefined;
      for (const r of arr) {
        if (prev !== undefined && (r.ord as number) !== prev + 1) {
          segments.push({ rank: Math.min(...seg.map((s) => s.rank)), recs: seg });
          seg = [];
        }
        seg.push(r);
        prev = r.ord as number;
      }
      if (seg.length) segments.push({ rank: Math.min(...seg.map((s) => s.rank)), recs: seg });
    }
    for (const r of loose) segments.push({ rank: r.rank, recs: [r] });

    segments.sort((a, b) => a.rank - b.rank);

    const out: RetrievedChunk[] = [];
    for (const s of segments) {
      for (const r of s.recs) {
        out.push({
          chunk: r.chunk,
          matchedBy: r.matchedBy,
          role: r.isAnchor ? "anchor" : "context",
        });
      }
    }
    return out;
  }
}
