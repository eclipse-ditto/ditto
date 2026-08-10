import { defineConfig } from "vitest/config";
export default defineConfig({
  test: { globals: false, environment: "node", include: ["src/**/*.pgtest.ts"], testTimeout: 120000, hookTimeout: 120000 },
});
