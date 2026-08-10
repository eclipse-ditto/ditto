import { describe, it, expect } from "vitest";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const entry = resolve(here, "stdio.ts");

describe("stdio entrypoint (spawn e2e)", () => {
  it("serves ping over stdio", async () => {
    // knowledge disabled to keep this transport test hermetic; knowledge covered in knowledge tests.
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const cfgPath = join(dir, "config.json");
    writeFileSync(cfgPath, JSON.stringify({ knowledge: { enabled: false } }));

    const transport = new StdioClientTransport({
      command: process.execPath,
      args: ["--import", "tsx", entry],
      env: {
        ...process.env,
        DITTO_MCP_CONFIG: cfgPath,
      },
    });
    const client = new Client({ name: "stdio-test", version: "0.0.0" });
    await client.connect(transport);
    const res = await client.callTool({ name: "ping", arguments: {} });
    const content = res.content as Array<{ type: string; text: string }>;
    expect(content[0].text).toBe("pong");
    await client.close();
  });
});
