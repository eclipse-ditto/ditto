import { ToolRegistry } from "../registry/tool-registry.js";
import type { AppConfig } from "../config/schema.js";
import { pingTool } from "./ping.js";
import { makeKnowledgeTools } from "./knowledge.js";
import type { KnowledgeService } from "../knowledge/knowledge-service.js";

export function registerTools(
  config: AppConfig,
  knowledgeService?: KnowledgeService,
): ToolRegistry {
  const registry = new ToolRegistry();
  if (config.tools.ping) registry.register(pingTool);
  if (config.knowledge.enabled && knowledgeService) {
    for (const tool of makeKnowledgeTools(knowledgeService))
      registry.register(tool);
  }
  return registry;
}
