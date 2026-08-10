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
      })
      .default({
        enabled: true,
        retriever: "fts",
        embedding: { model: "Xenova/bge-small-en-v1.5", dim: 384, allowRemoteModels: true, batchSize: 32 },
        localDir: { enabled: false, id: "local" },
        publicSource: { enabled: true, url: "https://eclipse.dev/ditto/llms.txt" },
      }),
  })
  .default({});

export type AppConfig = z.infer<typeof AppConfigSchema>;
