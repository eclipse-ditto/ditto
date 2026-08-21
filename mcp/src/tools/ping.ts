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

import type { ToolDef } from "../core/types.js";

export const pingTool: ToolDef = {
  name: "ping",
  description: "Health check; returns pong",
  inputSchema: {},
  handler: async () => ({ content: [{ type: "text", text: "pong" }] }),
};
