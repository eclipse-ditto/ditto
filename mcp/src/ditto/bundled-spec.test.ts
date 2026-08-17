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

import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import YAML from "yaml";
import { parseOperations } from "./openapi.js";
import { isSudo } from "./tool-policy.js";

const specPath = join(
  dirname(fileURLToPath(import.meta.url)),
  "..", "..", "..",
  "documentation", "src", "main", "resources", "openapi", "ditto-api-2.yml",
);

describe("bundled Ditto spec", () => {
  it("parses and yields operations incl. a things GET with a resolved path param", () => {
    const spec = YAML.parse(readFileSync(specPath, "utf8"));
    const ops = parseOperations(spec);
    expect(ops.length).toBeGreaterThan(20);
    const getThing = ops.find((o) => o.method === "GET" && o.path.includes("/things/{thingId}"));
    expect(getThing).toBeDefined();
    expect(getThing!.params.some((p) => p.name === "thingId" && p.in === "path")).toBe(true);
  });

  it("classifies /api/2/connections GET as isSudo (DevOpsBasic security)", () => {
    const spec = YAML.parse(readFileSync(specPath, "utf8"));
    const ops = parseOperations(spec);
    const getConnections = ops.find((o) => o.method === "GET" && o.path.startsWith("/api/2/connections"));
    expect(getConnections).toBeDefined();
    expect(isSudo(getConnections!)).toBe(true);
  });

  it("resolves bodySchema for PUT /api/2/things/{thingId}", () => {
    const spec = YAML.parse(readFileSync(specPath, "utf8"));
    const ops = parseOperations(spec);
    const putThing = ops.find((o) => o.method === "PUT" && o.path === "/api/2/things/{thingId}");
    expect(putThing).toBeDefined();
    expect(putThing!.hasBody).toBe(true);
    // The NewThing schema is an object with properties, so bodySchema should be defined
    expect(putThing!.bodySchema).toBeDefined();
    expect(putThing!.bodySchema!.props.length).toBeGreaterThan(0);
    // Verify some expected properties are present (policyId, definition, attributes, features, _policy, _copyPolicyFrom)
    const propNames = putThing!.bodySchema!.props.map(p => p.name);
    expect(propNames).toContain("policyId");
  });
});
