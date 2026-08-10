import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    globals: false,
    environment: "node",
    include: ["src/**/*.test.ts", "src/**/*.itest.ts"],
    exclude: ["**/node_modules/**", "**/*.pgtest.ts"],
    testTimeout: 20000,
  },
});
