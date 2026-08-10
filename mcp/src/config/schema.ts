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
  })
  .default({});

export type AppConfig = z.infer<typeof AppConfigSchema>;
