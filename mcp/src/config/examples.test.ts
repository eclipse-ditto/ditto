import { describe, it, expect } from "vitest";
import { readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { loadConfig } from "./load.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const examplesDir = join(__dirname, "../../examples");

describe("example configs", () => {
  const exampleFiles = readdirSync(examplesDir).filter((f) => f.endsWith(".json"));

  it("should have at least one example", () => {
    expect(exampleFiles.length).toBeGreaterThan(0);
  });

  exampleFiles.forEach((file) => {
    it(`should parse ${file} without error`, () => {
      const path = join(examplesDir, file);
      expect(() => loadConfig(path)).not.toThrow();
    });
  });
});
