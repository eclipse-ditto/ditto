import { describe, it, expect } from "vitest";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const entry = resolve(here, "stdio.ts");

describe("stdio entrypoint (spawn e2e)", () => {
  it("serves ping over stdio", async () => {
    const transport = new StdioClientTransport({
      command: process.execPath,
      args: ["--import", "tsx", entry],
    });
    const client = new Client({ name: "stdio-test", version: "0.0.0" });
    await client.connect(transport);
    const res = await client.callTool({ name: "ping", arguments: {} });
    const content = res.content as Array<{ type: string; text: string }>;
    expect(content[0].text).toBe("pong");
    await client.close();
  });
});
