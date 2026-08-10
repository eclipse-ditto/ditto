import { describe, it, expect } from "vitest";
import { toBatches } from "./embedding.js";

describe("toBatches", () => {
  it("splits into fixed-size batches with a remainder", () => {
    expect(toBatches([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
  });

  it("returns a single batch when size >= length", () => {
    expect(toBatches([1, 2], 5)).toEqual([[1, 2]]);
  });

  it("returns [] for empty input", () => {
    expect(toBatches<number>([], 3)).toEqual([]);
  });

  it("throws on a non-positive batch size", () => {
    expect(() => toBatches([1], 0)).toThrow(/batch size/i);
  });
});
