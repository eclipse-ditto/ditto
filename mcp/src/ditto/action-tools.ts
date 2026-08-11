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
import { operationToTool, type ToolCredentials } from "./action-tool.js";
import { createConfigCredential } from "./credential.js";

export interface ActionToolDeps {
  loadSpec?: () => Promise<unknown>;
  client?: DittoClient;
}

// Canonical Ditto OpenAPI committed in the monorepo. Resolves from both src (tsx)
// and dist (tsc): dirname is src/ditto or dist/ditto -> three levels up = repo root.
const CANONICAL_SPEC = join(
  dirname(fileURLToPath(import.meta.url)),
  "..", "..", "..",
  "documentation", "src", "main", "resources", "openapi", "ditto-api-2.yml",
);

// Substitute the literal ${version} placeholder in a version URL template.
export function buildVersionUrl(template: string, version: string): string {
  return template.replaceAll("${version}", version);
}

async function fetchSpec(url: string): Promise<unknown> {
  const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
  if (!res.ok) throw new Error(`openapi fetch ${url} -> ${res.status}`);
  return YAML.parse(await res.text());
}

// YAML.parse also parses JSON, so this handles .yml, .yaml, and .json specs.
async function defaultLoadSpec(config: AppConfig): Promise<unknown> {
  const { path, url, version, versionUrlTemplate } = config.ditto.openApi;
  if (path) return YAML.parse(await readFile(path, "utf8"));
  if (url) return fetchSpec(url);
  if (version) return fetchSpec(buildVersionUrl(versionUrlTemplate, version));
  // Fallback: the canonical Ditto spec committed in this monorepo.
  return YAML.parse(await readFile(CANONICAL_SPEC, "utf8"));
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
  const dc = config.ditto.devopsCredential;
  if (dc?.kind === "basic" && dc.username === undefined) {
    process.stderr.write(
      "[ditto-mcp] devopsCredential is 'basic' but has no username; sudo requests will be sent unauthenticated\n",
    );
  }
  const creds: ToolCredentials = {
    standard: createConfigCredential(config.ditto.credential),
    devops: dc ? createConfigCredential(dc) : undefined,
  };
  const tools = parseOperations(spec)
    .filter((op) => isAllowed(op, config.ditto.policy))
    .map((op) => operationToTool(op, client, creds));
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
