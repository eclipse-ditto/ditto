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

import type { ZodRawShape } from "zod";
import type { AppConfig } from "../config/schema.js";

export interface ToolResult {
  content: Array<{ type: "text"; text: string }>;
  [key: string]: unknown;
}

export interface RequestCtx {
  config: AppConfig;
  /** Present for HTTP sessions; absent over stdio. */
  sessionId?: string;
  /** HTTP request headers when available (used by later credential passthrough). */
  headers?: Record<string, string | string[] | undefined>;
  /** Abort signal for the in-flight request; cancels downstream work. */
  signal?: AbortSignal;
}

export interface ToolDef {
  name: string;
  description: string;
  /** A zod raw shape (object of zod validators); `{}` for no inputs. */
  inputSchema: ZodRawShape;
  handler(args: unknown, ctx: RequestCtx): Promise<ToolResult>;
}
