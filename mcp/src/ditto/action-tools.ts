import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import YAML from "yaml";
import type { AppConfig } from "../config/schema.js";
import type { ToolDef } from "../core/types.js";
import type { DittoClient } from "./client.js";
import { HttpDittoClient } from "./client.js";
import { parseOperations } from "./openapi.js";
import { isAllowed } from "./tool-policy.js";
import { operationToTool } from "./action-tool.js";
import { createConfigCredential } from "./credential.js";

export interface ActionToolDeps {
  loadSpec?: () => Promise<unknown>;
  client?: DittoClient;
}

// Pinned Ditto OpenAPI bundled with the server. Resolves to mcp/assets/ from
// both src (tsx) and dist (tsc): dirname is src/ditto or dist/ditto → ../../assets.
const BUNDLED_SPEC = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "assets", "ditto-openapi.yml");

// YAML.parse also parses JSON, so this handles .yml, .yaml, and .json specs.
async function defaultLoadSpec(config: AppConfig): Promise<unknown> {
  const { path, url } = config.ditto.openApi;
  if (path) return YAML.parse(await readFile(path, "utf8"));
  if (url) {
    const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
    if (!res.ok) throw new Error(`openapi fetch ${url} -> ${res.status}`);
    return YAML.parse(await res.text());
  }
  // Fallback: the pinned Ditto spec shipped with the server.
  return YAML.parse(await readFile(BUNDLED_SPEC, "utf8"));
}

export async function makeActionTools(config: AppConfig, deps: ActionToolDeps = {}): Promise<ToolDef[]> {
  const client =
    deps.client ??
    (config.ditto.baseUrl
      ? new HttpDittoClient(config.ditto.baseUrl)
      : undefined);
  if (!client) {
    process.stderr.write("[ditto-mcp] action tools disabled: ditto.baseUrl is required\n");
    return [];
  }
  let spec: unknown;
  try {
    spec = deps.loadSpec ? await deps.loadSpec() : await defaultLoadSpec(config);
  } catch (err) {
    process.stderr.write(`[ditto-mcp] action tools disabled: ${String(err)}\n`);
    return [];
  }
  const configCredential = createConfigCredential(config);
  const tools = parseOperations(spec)
    .filter((op) => isAllowed(op, config.ditto.policy))
    .map((op) => operationToTool(op, client, configCredential));
  // De-duplicate tool names: on collision, append _2, _3, ...
  const seen = new Map<string, number>();
  for (const tool of tools) {
    const base = tool.name;
    const count = seen.get(base) ?? 0;
    seen.set(base, count + 1);
    if (count > 0) tool.name = `${base}_${count + 1}`;
  }
  return tools;
}
