import { describe, it, expect, afterEach } from "vitest";
import { makeActionTools } from "./action-tools.js";
import { HttpDittoClient } from "./client.js";
import { startFakeDitto } from "./fake-ditto.js";
import { AppConfigSchema } from "../config/schema.js";

const SPEC = {
  paths: {
    "/things/{thingId}": {
      get: { operationId: "getThingById", summary: "get", parameters: [{ name: "thingId", in: "path", required: true, schema: { type: "string" } }] },
      put: { operationId: "putThing", summary: "put", parameters: [{ name: "thingId", in: "path", required: true, schema: { type: "string" } }], requestBody: {} },
    },
    "/sudo/things/{thingId}": {
      get: { operationId: "sudoRetrieveThing", summary: "sudo get", parameters: [{ name: "thingId", in: "path", required: true, schema: { type: "string" } }] },
    },
  },
};

let fake: Awaited<ReturnType<typeof startFakeDitto>>;
afterEach(async () => { await fake?.stop(); });

async function tools(policy: object, credKind = "basic") {
  fake = await startFakeDitto((req) => ({ status: 200, body: JSON.stringify({ url: req.url }) }));
  const config = AppConfigSchema.parse({
    ditto: { enabled: true, baseUrl: fake.baseUrl, credential: { kind: credKind, username: "u", password: "p" }, policy },
  });
  const list = await makeActionTools(config, { loadSpec: async () => SPEC, client: new HttpDittoClient(fake.baseUrl) });
  return { config, byName: Object.fromEntries(list.map((t) => [t.name, t])) };
}

describe("makeActionTools", () => {
  it("registers only GET (read-only) by default", async () => {
    const { byName } = await tools({ allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [] });
    expect(byName.getThingById).toBeDefined();
    expect(byName.putThing).toBeUndefined();          // write blocked
    expect(byName.sudoRetrieveThing).toBeUndefined();  // sudo blocked
  });

  it("includes a write when allowlisted and a sudo when sudoAllowlisted", async () => {
    const { byName } = await tools({ allowMethods: ["GET"], writeAllowlist: ["putThing"], sudoAllowlist: ["sudoRetrieveThing"] });
    expect(byName.putThing).toBeDefined();
    expect(byName.sudoRetrieveThing).toBeDefined();
  });

  it("a registered GET tool calls the fake Ditto", async () => {
    const { byName, config } = await tools({ allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [] });
    const res = await byName.getThingById.handler({ thingId: "ns:1" }, { config, headers: {} } as never);
    expect(res.content[0].text).toContain("/things/ns:1");
  });

  it("de-duplicates colliding tool names by appending _2, _3, ...", async () => {
    const specWithCollision = {
      paths: {
        "/a": { get: { operationId: "foo-bar", summary: "a" } },
        "/b": { get: { operationId: "foo/bar", summary: "b" } },
        "/c": { get: { operationId: "foo.bar", summary: "c" } },
      },
    };
    fake = await startFakeDitto(() => ({ status: 200, body: "{}" }));
    const config = AppConfigSchema.parse({
      ditto: { enabled: true, baseUrl: fake.baseUrl, credential: { kind: "basic", username: "u", password: "p" }, policy: { allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [] } },
    });
    const list = await makeActionTools(config, { loadSpec: async () => specWithCollision, client: new HttpDittoClient(fake.baseUrl) });
    const names = list.map((t) => t.name);
    expect(names).toContain("foo_bar");
    expect(names).toContain("foo_bar_2");
    expect(names).toContain("foo_bar_3");
    expect(names.length).toBe(3);
  });
});
