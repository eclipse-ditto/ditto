import { describe, it, expect, afterEach } from "vitest";
import { buildKnowledgeService } from "./build.js";
import { AppConfigSchema } from "../config/schema.js";
import type { FetchFn } from "./public-source.js";
import type { KnowledgeService } from "./knowledge-service.js";

let service: KnowledgeService | undefined;
afterEach(() => {
  service = undefined;
});

const fakeIndex = `
# Ditto Docs
- [Things](thing.md)
`;

const fakeDoc = `
# Things
A thing is a digital twin of a physical device.
`;

const fakeFetch: FetchFn = async (url) => {
  if (url.endsWith("index.md")) return fakeIndex;
  if (url.endsWith("thing.md")) return fakeDoc;
  throw new Error(`unexpected fetch: ${url}`);
};

const failFetch: FetchFn = async () => {
  throw new Error("fetch failed");
};

describe("buildKnowledgeService", () => {
  it("returns undefined when knowledge disabled", async () => {
    const config = AppConfigSchema.parse({ knowledge: { enabled: false } });
    const result = await buildKnowledgeService(config, {
      fetchFn: fakeFetch,
    });
    expect(result).toBeUndefined();
  });

  it("returns undefined when no sources enabled", async () => {
    const config = AppConfigSchema.parse({
      knowledge: { enabled: true, publicSource: { enabled: false } },
    });
    const result = await buildKnowledgeService(config, {
      fetchFn: fakeFetch,
    });
    expect(result).toBeUndefined();
  });

  it("gracefully degrades on init failure (returns undefined, logs)", async () => {
    const config = AppConfigSchema.parse({
      knowledge: {
        enabled: true,
        publicSource: {
          enabled: true,
          url: "http://example.com/index.md",
        },
      },
    });
    const result = await buildKnowledgeService(config, {
      fetchFn: failFetch,
    });
    expect(result).toBeUndefined();
  });

  it("builds and inits a working service with fake fetch", async () => {
    const config = AppConfigSchema.parse({
      knowledge: {
        enabled: true,
        publicSource: {
          enabled: true,
          url: "http://example.com/index.md",
        },
      },
    });
    service = await buildKnowledgeService(config, { fetchFn: fakeFetch });
    expect(service).toBeDefined();
    const hits = await service!.search("digital twin", 5);
    expect(hits.length).toBeGreaterThan(0);
    expect(hits[0].text).toContain("digital twin");
  });
});
