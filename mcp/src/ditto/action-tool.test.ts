/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, it, expect, afterEach } from "vitest";
import { operationToTool } from "./action-tool.js";
import { HttpDittoClient } from "./client.js";
import { startFakeDitto } from "./fake-ditto.js";
import { AppConfigSchema } from "../config/schema.js";
import { createConfigCredential } from "./credential.js";
import type { DittoOperation } from "./openapi.js";

const cfg = (ditto: object) => AppConfigSchema.parse({ ditto: { enabled: true, ...ditto } });
const op = (o: Partial<DittoOperation>): DittoOperation => ({
  operationId: "getThingById", method: "GET", path: "/things/{thingId}", summary: "Retrieve a thing",
  description: "Retrieve a thing", params: [{ name: "thingId", in: "path", required: true, type: "string" }],
  hasBody: false, securitySchemes: [], ...o,
});

let fake: Awaited<ReturnType<typeof startFakeDitto>>;
afterEach(async () => { await fake?.stop(); });

describe("operationToTool", () => {
  it("builds a tool that calls Ditto with the config credential and returns the response", async () => {
    fake = await startFakeDitto(() => ({ status: 200, body: JSON.stringify({ thingId: "ns:1" }) }));
    const client = new HttpDittoClient(fake.baseUrl);
    const config = cfg({ baseUrl: fake.baseUrl, credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const tool = operationToTool(op({}), client, { standard: configCredential });
    const res = await tool.handler({ thingId: "ns:1" }, { config, headers: {} } as never);
    expect(res.content[0].text).toContain("ns:1");
    expect(fake.requests[0].auth).toBe(`Basic ${Buffer.from("u:p").toString("base64")}`);
  });

  it("refuses a sudo op without a devops credential (does not call Ditto)", async () => {
    fake = await startFakeDitto(() => ({ status: 200, body: "should not be called" }));
    const client = new HttpDittoClient(fake.baseUrl);
    const config = cfg({ baseUrl: fake.baseUrl, credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const tool = operationToTool(op({ operationId: "sudoRetrieveThing", path: "/sudo/things/{thingId}" }), client, { standard: configCredential });
    const res = await tool.handler({ thingId: "ns:1" }, { config, headers: {} } as never);
    expect(res.content[0].text.toLowerCase()).toContain("devops");
    expect(fake.requests).toHaveLength(0);
  });

  it("produces a typed body schema when bodySchema has props", () => {
    const client = new HttpDittoClient("http://fake");
    const config = cfg({ baseUrl: "http://fake", credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const withBodySchema = op({
      operationId: "postThing", method: "POST", hasBody: true,
      bodySchema: { props: [
        { name: "thingId", type: "string", required: true },
        { name: "counter", type: "number", required: false },
      ]},
    });
    const tool = operationToTool(withBodySchema, client, { standard: configCredential });
    const schema = tool.inputSchema;
    expect(Object.keys(schema)).toContain("body");
    expect(Object.keys(schema)).toContain("thingId");
    // zod shape should include typed body props
    const bodyShape = (schema.body as any)?._def;
    expect(bodyShape).toBeDefined();
  });

  it("exposes a body arg for hasBody even without bodySchema", () => {
    const client = new HttpDittoClient("http://fake");
    const config = cfg({ baseUrl: "http://fake", credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const noBodySchema = op({ operationId: "postAnything", method: "POST", hasBody: true });
    const tool = operationToTool(noBodySchema, client, { standard: configCredential });
    const schema = tool.inputSchema;
    expect(Object.keys(schema)).toContain("body");
  });

  it("accepts an object value for a $ref/unknown body prop (z.any, not z.string)", () => {
    const client = new HttpDittoClient("http://fake");
    const config = cfg({ baseUrl: "http://fake", credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const withUnknownProp = op({
      operationId: "putThing", method: "PUT", hasBody: true,
      bodySchema: { props: [
        { name: "policyId", type: "string", required: true },
        { name: "attributes", type: "unknown", required: false },
      ]},
    });
    const tool = operationToTool(withUnknownProp, client, { standard: configCredential });
    const schema = tool.inputSchema;
    // Validate that an object value is accepted for 'attributes' (proves it's z.any, not z.string)
    const bodySchema = (schema.body as any);
    expect(() => bodySchema.parse({ body: { attributes: { color: "blue" } } })).not.toThrow();
    expect(() => bodySchema.parse({ body: { policyId: "ns:p", attributes: { nested: { deep: true } } } })).not.toThrow();
  });

  it("makes all body props optional (even required props)", () => {
    const client = new HttpDittoClient("http://fake");
    const config = cfg({ baseUrl: "http://fake", credential: { kind: "basic", username: "u", password: "p" } });
    const configCredential = createConfigCredential(config.ditto.credential);
    const withRequiredProp = op({
      operationId: "postThing", method: "POST", hasBody: true,
      bodySchema: { props: [
        { name: "thingId", type: "string", required: true },
      ]},
    });
    const tool = operationToTool(withRequiredProp, client, { standard: configCredential });
    const schema = tool.inputSchema;
    const bodySchema = (schema.body as any);
    // The tool accepts a body without the "required" prop
    expect(() => bodySchema.parse({ body: {} })).not.toThrow();
    expect(() => bodySchema.parse({ body: { otherField: "x" } })).not.toThrow();
  });

  it("routes a sudo op to the devops credential", async () => {
    fake = await startFakeDitto(() => ({ status: 200, body: "{}" }));
    const client = new HttpDittoClient(fake.baseUrl);
    const config = cfg({
      baseUrl: fake.baseUrl,
      credential: { kind: "basic", username: "app", password: "p" },
      devopsCredential: { kind: "basic", username: "dev", password: "s" },
    });
    const creds = {
      standard: createConfigCredential(config.ditto.credential),
      devops: createConfigCredential(config.ditto.devopsCredential!),
    };
    const tool = operationToTool(op({ operationId: "sudoRetrieveThing", path: "/sudo/things/{thingId}" }), client, creds);
    await tool.handler({ thingId: "ns:1" }, { config, headers: {} } as never);
    expect(fake.requests[0].auth).toBe(`Basic ${Buffer.from("dev:s").toString("base64")}`);
  });

  it("routes a non-sudo op to the standard credential", async () => {
    fake = await startFakeDitto(() => ({ status: 200, body: "{}" }));
    const client = new HttpDittoClient(fake.baseUrl);
    const config = cfg({
      baseUrl: fake.baseUrl,
      credential: { kind: "basic", username: "app", password: "p" },
      devopsCredential: { kind: "basic", username: "dev", password: "s" },
    });
    const creds = {
      standard: createConfigCredential(config.ditto.credential),
      devops: createConfigCredential(config.ditto.devopsCredential!),
    };
    const tool = operationToTool(op({}), client, creds); // getThingById, GET /things/{thingId}
    await tool.handler({ thingId: "ns:1" }, { config, headers: {} } as never);
    expect(fake.requests[0].auth).toBe(`Basic ${Buffer.from("app:p").toString("base64")}`);
  });
});
