import { describe, it, expect } from "vitest";

describe("toolchain sanity", () => {
  it("runs vitest with ESM", () => {
    expect(1 + 1).toBe(2);
  });
});
