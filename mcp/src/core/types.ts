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
