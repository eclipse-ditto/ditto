import { z } from "zod";

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
        localDir: { enabled: false, id: "local" },
        publicSource: { enabled: true, url: "https://eclipse.dev/ditto/llms.txt" },
        store: { kind: "sqlite", sqlite: {}, pgvector: { table: "ditto_kn" } },
      }),
    ditto: z
      .object({
        enabled: z.boolean().default(false),
        baseUrl: z.string().optional(),
        openApi: z
          .object({ path: z.string().optional(), url: z.string().optional() })
          .default({}),
        credential: z
          .object({
            kind: z.enum(["basic", "devops", "oidc"]).default("basic"),
            username: z.string().optional(),
            password: z.string().optional(),
            tokenUrl: z.string().optional(),
            clientId: z.string().optional(),
            clientSecret: z.string().optional(),
            scope: z.string().optional(),
            devops: z.boolean().optional(),
          })
          .default({ kind: "basic" }),
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
