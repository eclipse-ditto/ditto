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
