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

import type { AppConfig } from "../config/schema.js";
import type { RequestCtx } from "../core/types.js";

/** Minimal structural view of the SDK's tool-handler `extra` argument. */
export interface McpExtra {
  sessionId?: string;
  requestInfo?: { headers?: Record<string, string | string[] | undefined> };
  signal?: AbortSignal;
}

export function buildCtx(config: AppConfig, extra: McpExtra): RequestCtx {
  return {
    config,
    sessionId: extra.sessionId,
    headers: extra.requestInfo?.headers,
    signal: extra.signal,
  };
}
