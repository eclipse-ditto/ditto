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
import { writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { loadConfig } from "./load.js";

describe("loadConfig", () => {
  it("returns defaults when no path is given", () => {
    const cfg = loadConfig();
    expect(cfg.server.name).toBe("ditto-mcp");
    expect(cfg.server.http.port).toBe(3000);
    expect(cfg.server.http.host).toBe("127.0.0.1");
    expect(cfg.server.http.enableDnsRebindingProtection).toBe(true);
    expect(cfg.tools.ping).toBe(true);
    expect(cfg.knowledge.enabled).toBe(true);
    expect(cfg.knowledge.publicSource.url).toBe(
      "https://eclipse.dev/ditto/llms.txt",
    );
    expect(cfg.knowledge.retriever).toBe("fts");
    expect(cfg.knowledge.embedding.model).toBe("Xenova/bge-small-en-v1.5");
    expect(cfg.knowledge.embedding.dim).toBe(384);
    expect(cfg.knowledge.embedding.batchSize).toBe(32);
    expect(cfg.knowledge.localDir.enabled).toBe(false);
    expect(cfg.knowledge.store.kind).toBe("sqlite");
    expect(cfg.ditto.enabled).toBe(false);
    expect(cfg.ditto.credential.kind).toBe("basic");
    expect(cfg.ditto.policy.allowMethods).toEqual(["GET"]);
  });

  it("accepts a pgvector store config", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "c.json");
    writeFileSync(file, JSON.stringify({
      knowledge: { store: { kind: "pgvector", pgvector: { connectionString: "postgres://x" } } },
    }));
    const cfg = loadConfig(file);
    expect(cfg.knowledge.store.kind).toBe("pgvector");
    expect(cfg.knowledge.store.pgvector.connectionString).toBe("postgres://x");
  });

  it("merges values from a JSON file over defaults", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "config.json");
    writeFileSync(file, JSON.stringify({ server: { http: { port: 8080 } } }));
    const cfg = loadConfig(file);
    expect(cfg.server.http.port).toBe(8080);
    expect(cfg.server.http.host).toBe("127.0.0.1");
    expect(cfg.server.http.enableDnsRebindingProtection).toBe(true);
    expect(cfg.tools.ping).toBe(true);
  });

  it("throws on an invalid value", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "config.json");
    writeFileSync(file, JSON.stringify({ server: { http: { port: "nope" } } }));
    expect(() => loadConfig(file)).toThrow();
  });

  it("accepts an oidc credential and an optional devopsCredential", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "c.json");
    writeFileSync(file, JSON.stringify({
      ditto: {
        enabled: true,
        credential: { kind: "oidc", tokenUrl: "https://idp/token", clientId: "c", clientSecret: "s", scope: "ditto" },
        devopsCredential: { kind: "oidc", tokenUrl: "https://idp/token", clientId: "dev", clientSecret: "s2" },
      },
    }));
    const cfg = loadConfig(file);
    expect(cfg.ditto.credential.kind).toBe("oidc");
    expect(cfg.ditto.credential.tokenUrl).toBe("https://idp/token");
    expect(cfg.ditto.devopsCredential?.clientId).toBe("dev");
  });

  it("rejects the removed devops credential kind", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "c.json");
    writeFileSync(file, JSON.stringify({ ditto: { enabled: true, credential: { kind: "devops", username: "d", password: "s" } } }));
    expect(() => loadConfig(file)).toThrow();
  });
});

describe("openApi version config", () => {
  it("defaults versionUrlTemplate to the eclipse-ditto raw URL", () => {
    const cfg = loadConfig();
    expect(cfg.ditto.openApi.version).toBeUndefined();
    expect(cfg.ditto.openApi.versionUrlTemplate).toBe(
      "https://raw.githubusercontent.com/eclipse-ditto/ditto/${version}/documentation/src/main/resources/openapi/ditto-api-2.yml",
    );
  });

  it("accepts an explicit version and custom template", () => {
    const dir = mkdtempSync(join(tmpdir(), "ditto-mcp-"));
    const file = join(dir, "c.json");
    writeFileSync(file, JSON.stringify({
      ditto: { openApi: { version: "3.6.0", versionUrlTemplate: "https://mirror.example/${version}/spec.yml" } },
    }));
    const cfg = loadConfig(file);
    expect(cfg.ditto.openApi.version).toBe("3.6.0");
    expect(cfg.ditto.openApi.versionUrlTemplate).toBe("https://mirror.example/${version}/spec.yml");
  });
});
