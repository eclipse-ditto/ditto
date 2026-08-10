import { ToolRegistry } from "../registry/tool-registry.js";
import type { AppConfig } from "../config/schema.js";
import { pingTool } from "./ping.js";

export function registerTools(config: AppConfig): ToolRegistry {
  const registry = new ToolRegistry();
  if (config.tools.ping) registry.register(pingTool);
  return registry;
}
