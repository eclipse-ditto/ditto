import { z } from "zod";
import type { ToolDef, ToolResult } from "../core/types.js";
import type { Chunk, RetrievedChunk } from "../knowledge/types.js";
import type { KnowledgeService } from "../knowledge/knowledge-service.js";

function formatChunk(c: Chunk, extraFooter = ""): string {
  const footer = `source: ${c.cite} · id: ${c.id}${extraFooter}`;
  return `## ${c.title}\n${c.text}\n\n[${footer}]`;
}

function formatRetrievedChunk(rc: RetrievedChunk): string {
  return formatChunk(rc.chunk, ` · matched: ${rc.matchedBy.join("+")}`);
}

function textResult(text: string): ToolResult {
  return { content: [{ type: "text", text }] };
}

export function makeKnowledgeTools(service: KnowledgeService): ToolDef[] {
  const search: ToolDef = {
    name: "search",
    description:
      "Search the Ditto knowledge base (official docs plus any configured corpora) and " +
      "return the most relevant documentation excerpts, each with a source URL and a chunk id. " +
      "Use natural-language questions about Ditto concepts, configuration, HTTP/Ditto protocol, " +
      "connectivity, policies, or operations.",
    inputSchema: {
      query: z
        .string()
        .describe(
          "Natural-language search query about Ditto (e.g. 'how do policies grant access' " +
            "or 'why does connectivity crash on reconnect').",
        ),
      limit: z
        .number()
        .int()
        .positive()
        .max(20)
        .optional()
        .describe(
          "Maximum number of documentation excerpts to return. Default 5, maximum 20. " +
            "Increase for broader context, decrease for only the top matches.",
        ),
    },
    handler: async (args: unknown): Promise<ToolResult> => {
      const { query, limit } = args as { query: string; limit?: number };
      const hits = await service.search(query, limit ?? 5);
      if (hits.length === 0) return textResult(`No results for "${query}".`);
      return textResult(hits.map(formatRetrievedChunk).join("\n\n---\n\n"));
    },
  };

  const getChunk: ToolDef = {
    name: "get_chunk",
    description:
      "Fetch the full text of a single knowledge chunk by its id. Use an id returned by " +
      "the `search` tool (shown as 'id: <value>', e.g. 'public#12').",
    inputSchema: {
      id: z
        .string()
        .describe("A chunk id returned by the `search` tool, e.g. 'public#12'."),
    },
    handler: async (args: unknown): Promise<ToolResult> => {
      const { id } = args as { id: string };
      const c = await service.getChunk(id);
      return textResult(c ? formatChunk(c) : `Chunk "${id}" not found.`);
    },
  };

  return [search, getChunk];
}
