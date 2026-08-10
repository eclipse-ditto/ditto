import { describe, it, expect } from "vitest";
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { loadConfig } from "./load.js";

describe("loadConfig", () => {
  it("returns defaults when no path is given", () => {
    const cfg = loadConfig();
    expect(cfg.server.name).toBe("ditto-mcp");
    expect(cfg.server.http.port).toBe(3000);
    expect(cfg.server.http.host).toBe("127.0.0.1");
    expect(cfg.server.http.enableDnsRebindingProtection).toBe(true);
    expect(cfg.tools.ping).toBe(true);
    expect(cfg.knowledge.enabled).toBe(true);
    expect(cfg.knowledge.publicSource.url).toBe(
      "https://eclipse.dev/ditto/llms.txt",
    );
  });

  it("merges values from a JSON file over defaults", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "config.json");
    writeFileSync(file, JSON.stringify({ server: { http: { port: 8080 } } }));
    const cfg = loadConfig(file);
    expect(cfg.server.http.port).toBe(8080);
    expect(cfg.server.http.host).toBe("127.0.0.1");
    expect(cfg.server.http.enableDnsRebindingProtection).toBe(true);
    expect(cfg.tools.ping).toBe(true);
  });

  it("throws on an invalid value", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "config.json");
    writeFileSync(file, JSON.stringify({ server: { http: { port: "nope" } } }));
    expect(() => loadConfig(file)).toThrow();
  });
});
