import { z } from "zod";
import type { ToolDef, ToolResult } from "../core/types.js";
import type { Chunk } from "../knowledge/types.js";
import type { KnowledgeService } from "../knowledge/knowledge-service.js";

function formatChunk(c: Chunk): string {
  return `## ${c.title}\n${c.text}\n\n[source: ${c.cite} · id: ${c.id}]`;
}

function textResult(text: string): ToolResult {
  return { content: [{ type: "text", text }] };
}

export function makeKnowledgeTools(service: KnowledgeService): ToolDef[] {
  const search: ToolDef = {
    name: "search",
    description:
      "Search the Ditto knowledge base and return the most relevant documentation excerpts.",
    inputSchema: {
      query: z.string(),
      k: z.number().int().positive().max(20).optional(),
    },
    handler: async (args: unknown): Promise<ToolResult> => {
      const { query, k } = args as { query: string; k?: number };
      const hits = await service.search(query, k ?? 5);
      if (hits.length === 0) return textResult(`No results for "${query}".`);
      return textResult(hits.map(formatChunk).join("\n\n---\n\n"));
    },
  };

  const getChunk: ToolDef = {
    name: "get_chunk",
    description: "Fetch a single knowledge chunk by its id.",
    inputSchema: { id: z.string() },
    handler: async (args: unknown): Promise<ToolResult> => {
      const { id } = args as { id: string };
      const c = await service.getChunk(id);
      return textResult(c ? formatChunk(c) : `Chunk "${id}" not found.`);
    },
  };

  return [search, getChunk];
}
