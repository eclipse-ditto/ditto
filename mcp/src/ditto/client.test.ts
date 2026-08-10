import { describe, it, expect, afterEach } from "vitest";
import { HttpDittoClient } from "./client.js";
import { startFakeDitto } from "./fake-ditto.js";
import type { DittoOperation } from "./openapi.js";

const op = (over: Partial<DittoOperation>): DittoOperation => ({
  operationId: "op", method: "GET", path: "/x", summary: "", description: "", params: [], hasBody: false, ...over,
});
const cred = (h?: string) => ({ isDevops: false, authHeader: async () => h });

let fake: Awaited<ReturnType<typeof startFakeDitto>>;
afterEach(async () => { await fake?.stop(); });

describe("HttpDittoClient", () => {
  it("substitutes path params, sends query, forwards auth", async () => {
    fake = await startFakeDitto((req) => ({ status: 200, body: JSON.stringify({ ok: req.url }) }));
    const client = new HttpDittoClient(fake.baseUrl);
    const res = await client.execute(
      op({ path: "/things/{thingId}", params: [
        { name: "thingId", in: "path", required: true, type: "string" },
        { name: "fields", in: "query", required: false, type: "string" }] }),
      { thingId: "ns:1", fields: "attributes" },
      cred("Basic abc"),
    );
    expect(res.status).toBe(200);
    expect(fake.requests[0].url).toBe("/things/ns:1?fields=attributes");
    expect(fake.requests[0].auth).toBe("Basic abc");
  });

  it("sends a JSON body for write ops", async () => {
    fake = await startFakeDitto(() => ({ status: 201, body: "" }));
    const client = new HttpDittoClient(fake.baseUrl);
    const res = await client.execute(
      op({ method: "PUT", path: "/things/{id}", hasBody: true,
           params: [{ name: "id", in: "path", required: true, type: "string" }] }),
      { id: "ns:1", body: { attributes: { a: 1 } } },
      cred(),
    );
    expect(res.status).toBe(201);
    expect(JSON.parse(fake.requests[0].body!)).toEqual({ attributes: { a: 1 } });
  });

  it("encodes unsafe path-param chars but keeps the Ditto namespace colon", async () => {
    fake = await startFakeDitto(() => ({ status: 200, body: "" }));
    const client = new HttpDittoClient(fake.baseUrl);
    await client.execute(
      op({ path: "/things/{thingId}", params: [{ name: "thingId", in: "path", required: true, type: "string" }] }),
      { thingId: "ns:a b/c" },
      cred(),
    );
    expect(fake.requests[0].url).toBe("/things/ns:a%20b%2Fc"); // colon kept; space+slash encoded
  });
});
