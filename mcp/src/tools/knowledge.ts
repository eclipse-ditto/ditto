import { z } from "zod";
import type { ToolDef, ToolResult } from "../core/types.js";
import type { Chunk, RetrievedChunk } from "../knowledge/types.js";
import type { KnowledgeService } from "../knowledge/knowledge-service.js";

function formatChunk(c: Chunk, extraFooter = ""): string {
  const footer = `source: ${c.cite} · id: ${c.id}${extraFooter}`;
  return `## ${c.title}\n${c.text}\n\n[${footer}]`;
}

function formatRetrievedChunk(rc: RetrievedChunk): string {
  // Neighbors pulled in by context expansion aren't retriever matches — label
  // them so their relevance isn't over-weighted vs. the actual anchors.
  const tag = rc.role === "context" ? "context" : `matched: ${rc.matchedBy.join("+")}`;
  return formatChunk(rc.chunk, ` · ${tag}`);
}

function textResult(text: string): ToolResult {
  return { content: [{ type: "text", text }] };
}

export interface SearchDefaults {
  limit: number;
  context: number;
}

export function makeKnowledgeTools(
  service: KnowledgeService,
  defaults: SearchDefaults = { limit: 5, context: 0 },
): ToolDef[] {
  const search: ToolDef = {
    name: "search",
    description:
      "Search the Ditto knowledge base (official docs plus any configured corpora) and " +
      "return the most relevant documentation excerpts, each with a source URL and a chunk id. " +
      "Use natural-language questions about Ditto concepts, configuration, HTTP/Ditto protocol, " +
      "connectivity, policies, or operations. Each match ('matched: ...') may be followed by " +
      "adjacent 'context' excerpts from the same document to preserve surrounding meaning.",
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
          `Maximum number of matching excerpts (anchors) to return. Default ${defaults.limit}, maximum 20. ` +
            "Neighbors added by 'context' do not count against this.",
        ),
      context: z
        .number()
        .int()
        .min(0)
        .max(5)
        .optional()
        .describe(
          `Adjacent same-document excerpts to include on each side of every match, for ` +
            `surrounding context. Default ${defaults.context}, maximum 5. Set 0 for matches only.`,
        ),
    },
    handler: async (args: unknown): Promise<ToolResult> => {
      const { query, limit, context } = args as { query: string; limit?: number; context?: number };
      const hits = await service.search(query, limit ?? defaults.limit, {
        context: context ?? defaults.context,
      });
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
