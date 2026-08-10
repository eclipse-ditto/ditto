import { describe, it, expect, afterEach } from "vitest";
import { SqliteVecStore } from "./sqlite-vec-store.js";

let store: SqliteVecStore;
afterEach(() => store?.close());

describe("SqliteVecStore", () => {
  it("returns nearest neighbors ordered by distance", async () => {
    store = new SqliteVecStore(3);
    await store.upsert([
      { id: "x", vector: [1, 0, 0] },
      { id: "y", vector: [0, 1, 0] },
      { id: "z", vector: [0.9, 0.1, 0] },
    ]);
    const hits = await store.searchByVector([1, 0, 0], 2);
    expect(hits.map((h) => h.id)).toEqual(["x", "z"]);
    expect(hits[0].distance).toBeLessThanOrEqual(hits[1].distance);
  });

  it("respects k", async () => {
    store = new SqliteVecStore(2);
    await store.upsert([
      { id: "a", vector: [1, 0] },
      { id: "b", vector: [0, 1] },
      { id: "c", vector: [1, 1] },
    ]);
    expect(await store.searchByVector([1, 0], 1)).toHaveLength(1);
  });

  it("upsert replaces an existing id", async () => {
    store = new SqliteVecStore(2);
    await store.upsert([{ id: "a", vector: [1, 0] }]);
    await store.upsert([{ id: "a", vector: [0, 1] }]);
    const hits = await store.searchByVector([0, 1], 5);
    expect(hits.filter((h) => h.id === "a")).toHaveLength(1);
  });

  it("rejects a non-positive dim", () => {
    expect(() => new SqliteVecStore(0)).toThrow(/dim/);
  });

  it("rejects a vector whose length != dim", async () => {
    store = new SqliteVecStore(3);
    await expect(store.upsert([{ id: "a", vector: [1, 0] }])).rejects.toThrow(/length/);
  });
});
