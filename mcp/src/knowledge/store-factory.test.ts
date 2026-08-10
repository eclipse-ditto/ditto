import { describe, it, expect, afterEach } from "vitest";
import { AppConfigSchema } from "../config/schema.js";
import { openStore } from "./store-factory.js";
import type { KnowledgeStore } from "./knowledge-store.js";

let store: KnowledgeStore;
afterEach(async () => { await store?.close(); });

describe("openStore", () => {
  it("opens an in-memory sqlite store by default", async () => {
    store = await openStore(AppConfigSchema.parse({}), { path: ":memory:" });
    expect(await store.isPopulated()).toBe(false);
  });
});
