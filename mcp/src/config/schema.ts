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

import { z } from "zod";

const CredentialSchema = z.object({
  kind: z.enum(["basic", "oidc"]).default("basic"),
  username: z.string().optional(),
  password: z.string().optional(),
  tokenUrl: z.string().optional(),
  clientId: z.string().optional(),
  clientSecret: z.string().optional(),
  scope: z.string().optional(),
});

export type CredentialConfig = z.infer<typeof CredentialSchema>;

export const AppConfigSchema = z
  .object({
    server: z
      .object({
        name: z.string().default("ditto-mcp"),
        http: z
          .object({
            port: z.number().int().positive().default(3000),
            host: z.string().default("127.0.0.1"),
            enableDnsRebindingProtection: z.boolean().default(true),
            allowedHosts: z.array(z.string()).optional(),
            allowedOrigins: z.array(z.string()).optional(),
          })
          .default({
            port: 3000,
            host: "127.0.0.1",
            enableDnsRebindingProtection: true,
          }),
      })
      .default({
        name: "ditto-mcp",
        http: { port: 3000, host: "127.0.0.1", enableDnsRebindingProtection: true },
      }),
    tools: z
      .object({ ping: z.boolean().default(true) })
      .default({ ping: true }),
    knowledge: z
      .object({
        enabled: z.boolean().default(true),
        retriever: z.enum(["fts", "vector", "hybrid"]).default("fts"),
        embedding: z
          .object({
            model: z.string().default("Xenova/bge-small-en-v1.5"),
            dim: z.number().int().positive().default(384),
            modelPath: z.string().optional(),
            allowRemoteModels: z.boolean().default(true),
            cacheDir: z.string().optional(),
            batchSize: z.number().int().positive().default(32),
          })
          .default({ model: "Xenova/bge-small-en-v1.5", dim: 384, allowRemoteModels: true, batchSize: 32 }),
        chunk: z
          .object({
            maxChars: z.number().int().positive().default(1000),
            overlap: z.number().int().min(0).default(150),
          })
          .refine((c) => c.overlap < c.maxChars, {
            message: "knowledge.chunk.overlap must be less than maxChars",
          })
          .default({ maxChars: 1000, overlap: 150 }),
        search: z
          .object({
            limit: z.number().int().positive().max(20).default(5),
            context: z.number().int().min(0).max(5).default(1),
          })
          .default({ limit: 5, context: 1 }),
        localDir: z
          .object({
            enabled: z.boolean().default(false),
            path: z.string().optional(),
            id: z.string().default("local"),
          })
          .default({ enabled: false, id: "local" }),
        publicSource: z
          .object({
            enabled: z.boolean().default(true),
            url: z
              .string()
              .default("https://eclipse.dev/ditto/llms.txt"),
            maxDocs: z.number().int().positive().optional(),
          })
          .default({ enabled: true, url: "https://eclipse.dev/ditto/llms.txt" }),
        store: z
          .object({
            kind: z.enum(["sqlite", "pgvector"]).default("sqlite"),
            sqlite: z
              .object({ path: z.string().optional() })
              .default({}),
            pgvector: z
              .object({
                connectionString: z.string().optional(),
                table: z.string().default("ditto_kn"),
              })
              .default({ table: "ditto_kn" }),
          })
          .default({ kind: "sqlite", sqlite: {}, pgvector: { table: "ditto_kn" } }),
      })
      .default({
        enabled: true,
        retriever: "fts",
        embedding: { model: "Xenova/bge-small-en-v1.5", dim: 384, allowRemoteModels: true, batchSize: 32 },
        chunk: { maxChars: 1000, overlap: 150 },
        search: { limit: 5, context: 1 },
        localDir: { enabled: false, id: "local" },
        publicSource: { enabled: true, url: "https://eclipse.dev/ditto/llms.txt" },
        store: { kind: "sqlite", sqlite: {}, pgvector: { table: "ditto_kn" } },
      }),
    ditto: z
      .object({
        enabled: z.boolean().default(false),
        baseUrl: z.string().optional(),
        openApi: z
          .object({
            path: z.string().optional(),
            url: z.string().optional(),
            version: z.string().optional(),
            versionUrlTemplate: z
              .string()
              .default(
                "https://raw.githubusercontent.com/eclipse-ditto/ditto/${version}/documentation/src/main/resources/openapi/ditto-api-2.yml",
              ),
          })
          .default({}),
        credential: CredentialSchema.default({ kind: "basic" }),
        devopsCredential: CredentialSchema.optional(),
        policy: z
          .object({
            allowMethods: z.array(z.string()).default(["GET"]),
            writeAllowlist: z.array(z.string()).default([]),
            sudoAllowlist: z.array(z.string()).default([]),
          })
          .default({ allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [] }),
      })
      .default({
        enabled: false,
        openApi: {},
        credential: { kind: "basic" },
        policy: { allowMethods: ["GET"], writeAllowlist: [], sudoAllowlist: [] },
      }),
  })
  .default({});

export type AppConfig = z.infer<typeof AppConfigSchema>;
