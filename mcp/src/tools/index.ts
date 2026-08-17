/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

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
    for (const tool of makeKnowledgeTools(knowledgeService, config.knowledge.search))
      registry.register(tool);
  }
  if (config.ditto.enabled) {
    const { makeActionTools } = await import("../ditto/action-tools.js");
    for (const tool of await makeActionTools(config)) registry.register(tool);
  }
  return registry;
}
