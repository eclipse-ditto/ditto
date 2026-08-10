import { ToolRegistry } from "../registry/tool-registry.js";
import type { AppConfig } from "../config/schema.js";
import { pingTool } from "./ping.js";
import { makeKnowledgeTools } from "./knowledge.js";
import type { KnowledgeService } from "../knowledge/knowledge-service.js";

export async function registerTools(
  config: AppConfig,
  knowledgeService?: KnowledgeService,
): Promise<ToolRegistry> {
  const registry = new ToolRegistry();
  if (config.tools.ping) registry.register(pingTool);
  if (config.knowledge.enabled && knowledgeService) {
    for (const tool of makeKnowledgeTools(knowledgeService))
      registry.register(tool);
  }
  if (config.ditto.enabled) {
    const { makeActionTools } = await import("../ditto/action-tools.js");
    for (const tool of await makeActionTools(config)) registry.register(tool);
  }
  return registry;
}
