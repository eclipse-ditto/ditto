import type { ToolDef } from "../core/types.js";

export const pingTool: ToolDef = {
  name: "ping",
  description: "Health check; returns pong",
  inputSchema: {},
  handler: async () => ({ content: [{ type: "text", text: "pong" }] }),
};
