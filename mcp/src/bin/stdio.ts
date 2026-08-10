import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadConfig } from "../config/load.js";
import { registerTools } from "../tools/index.js";
import { buildServer } from "../server/build-server.js";

async function main(): Promise<void> {
  const config = loadConfig(process.env.DITTO_MCP_CONFIG);
  const registry = registerTools(config);
  const server = buildServer(registry, config);
  const transport = new StdioServerTransport();
  await server.connect(transport);
  // Never write to stdout except MCP protocol frames; logs go to stderr.
  process.stderr.write(`[ditto-mcp] stdio server ready: ${config.server.name}\n`);
}

main().catch((err) => {
  process.stderr.write(`[ditto-mcp] fatal: ${String(err)}\n`);
  process.exit(1);
});
