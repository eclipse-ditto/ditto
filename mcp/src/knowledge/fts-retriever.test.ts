import { describe, it, expect, afterEach } from "vitest";
import { FtsRetriever } from "./fts-retriever.js";
import type { Chunk } from "./types.js";

const chunk = (id: string, text: string): Chunk => ({
  id,
  source: "s",
  title: "T",
  text,
  cite: `https://x/${id}`,
});

let r: FtsRetriever;
afterEach(() => r?.close());

describe("FtsRetriever", () => {
  it("has kind 'fts'", () => {
    r = new FtsRetriever();
    expect(r.kind).toBe("fts");
  });

  it("finds chunks by keyword, best match first", async () => {
    r = new FtsRetriever();
    await r.add([
      chunk("a", "The Netty leak causes an out of memory crash on reconnect"),
      chunk("b", "Policies define access control for things"),
      chunk("c", "Connectivity manages MQTT and Kafka connections"),
    ]);
    const hits = await r.search("memory crash", 5);
    expect(hits.length).toBeGreaterThanOrEqual(1);
    expect(hits[0].chunk.id).toBe("a");
    expect(hits[0].matchedBy).toEqual(["fts"]);
  });

  it("respects the k limit", async () => {
    r = new FtsRetriever();
    await r.add([chunk("a", "alpha token"), chunk("b", "alpha token"), chunk("c", "alpha token")]);
    expect(await r.search("alpha", 2)).toHaveLength(2);
  });

  it("returns [] for a query with no matches", async () => {
    r = new FtsRetriever();
    await r.add([chunk("a", "hello world")]);
    expect(await r.search("nonexistentterm", 5)).toEqual([]);
  });

  it("does not throw on FTS-special characters in the query", async () => {
    r = new FtsRetriever();
    await r.add([chunk("a", "quotes and parens matter")]);
    await expect(r.search('"(quotes) AND *', 5)).resolves.not.toThrow();
  });

  it("getChunk returns the stored chunk or undefined", async () => {
    r = new FtsRetriever();
    await r.add([chunk("a", "hello")]);
    expect((await r.getChunk("a"))?.text).toBe("hello");
    expect(await r.getChunk("missing")).toBeUndefined();
  });
});
