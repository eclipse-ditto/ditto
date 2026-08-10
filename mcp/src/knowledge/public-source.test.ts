import { describe, it, expect } from "vitest";
import { PublicSource } from "./public-source.js";

const INDEX = `# Ditto docs
## Core
- [Things](https://eclipse.dev/ditto/things.md): about things
- [Policies](https://eclipse.dev/ditto/policies.md): about policies
Some prose that is not a link.
`;

const DOCS: Record<string, string> = {
  "https://eclipse.dev/ditto/llms.txt": INDEX,
  "https://eclipse.dev/ditto/things.md": "# Things\n\nA thing is a digital twin.",
  "https://eclipse.dev/ditto/policies.md": "# Policies\n\nPolicies control access.",
};

const fakeFetch = async (url: string): Promise<string> => {
  if (!(url in DOCS)) throw new Error(`404 ${url}`);
  return DOCS[url];
};

describe("PublicSource", () => {
  it("parses the index and chunks each linked document", async () => {
    const src = new PublicSource({
      url: "https://eclipse.dev/ditto/llms.txt",
      fetchFn: fakeFetch,
    });
    const chunks = await src.loadChunks();
    expect(src.id).toBe("public");
    const cites = new Set(chunks.map((c) => c.cite));
    expect(cites.has("https://eclipse.dev/ditto/things.md")).toBe(true);
    expect(cites.has("https://eclipse.dev/ditto/policies.md")).toBe(true);
    expect(chunks.some((c) => c.text.includes("digital twin"))).toBe(true);
    expect(chunks.every((c) => c.source === "public")).toBe(true);
  });

  it("honors maxDocs", async () => {
    const src = new PublicSource({
      url: "https://eclipse.dev/ditto/llms.txt",
      fetchFn: fakeFetch,
      maxDocs: 1,
    });
    const chunks = await src.loadChunks();
    expect(new Set(chunks.map((c) => c.cite)).size).toBe(1);
  });

  it("skips documents that fail to fetch instead of throwing", async () => {
    const src = new PublicSource({
      url: "https://eclipse.dev/ditto/llms.txt",
      fetchFn: async (u) => {
        if (u.endsWith("policies.md")) throw new Error("boom");
        return fakeFetch(u);
      },
    });
    const chunks = await src.loadChunks();
    expect(chunks.some((c) => c.cite.endsWith("things.md"))).toBe(true);
    expect(chunks.some((c) => c.cite.endsWith("policies.md"))).toBe(false);
  });
});
