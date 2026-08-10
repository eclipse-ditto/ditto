import { readFileSync } from "node:fs";
import { AppConfigSchema, type AppConfig } from "./schema.js";

export function loadConfig(path?: string): AppConfig {
  const raw: unknown = path ? JSON.parse(readFileSync(path, "utf8")) : {};
  return AppConfigSchema.parse(raw);
}
