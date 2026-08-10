import express, { type Express, type Request, type Response } from "express";
import { randomUUID } from "node:crypto";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { isInitializeRequest } from "@modelcontextprotocol/sdk/types.js";
import type { AppConfig } from "../config/schema.js";
import { registerTools } from "../tools/index.js";
import { buildServer } from "./build-server.js";

export function createHttpApp(config: AppConfig): Express {
  const app = express();
  app.use(express.json());

  const transports = new Map<string, StreamableHTTPServerTransport>();

  app.post("/mcp", async (req: Request, res: Response) => {
    try {
      const sessionId = req.headers["mcp-session-id"] as string | undefined;
      let transport: StreamableHTTPServerTransport | undefined =
        sessionId ? transports.get(sessionId) : undefined;

      if (!transport) {
        if (sessionId || !isInitializeRequest(req.body)) {
          res.status(400).json({
            jsonrpc: "2.0",
            error: { code: -32000, message: "Bad Request: no valid session" },
            id: null,
          });
          return;
        }
        const http = config.server.http;
        const allowedHosts = http.allowedHosts ?? [
          `${http.host}:${http.port}`,
          `127.0.0.1:${http.port}`,
          `localhost:${http.port}`,
          `[::1]:${http.port}`,
        ];
        transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => randomUUID(),
          onsessioninitialized: (sid) => {
            transports.set(sid, transport as StreamableHTTPServerTransport);
          },
          enableDnsRebindingProtection: http.enableDnsRebindingProtection,
          allowedHosts: http.enableDnsRebindingProtection
            ? allowedHosts
            : undefined,
          allowedOrigins: http.allowedOrigins,
        });
        transport.onclose = () => {
          if (transport?.sessionId) transports.delete(transport.sessionId);
        };
        const registry = registerTools(config);
        const server = buildServer(registry, config);
        await server.connect(transport);
      }

      await transport.handleRequest(req, res, req.body);
    } catch (error) {
      process.stderr.write(
        `[ditto-mcp] POST /mcp error: ${error instanceof Error ? error.message : String(error)}\n`,
      );
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: { code: -32603, message: "Internal error" },
          id: null,
        });
      }
    }
  });

  const handleSession = async (req: Request, res: Response) => {
    try {
      const sessionId = req.headers["mcp-session-id"] as string | undefined;
      const transport = sessionId ? transports.get(sessionId) : undefined;
      if (!transport) {
        res.status(400).send("Invalid or missing session ID");
        return;
      }
      await transport.handleRequest(req, res);
    } catch (error) {
      process.stderr.write(
        `[ditto-mcp] ${req.method} /mcp error: ${error instanceof Error ? error.message : String(error)}\n`,
      );
      if (!res.headersSent) {
        res.status(500).json({
          jsonrpc: "2.0",
          error: { code: -32603, message: "Internal error" },
          id: null,
        });
      }
    }
  };

  app.get("/mcp", handleSession);
  app.delete("/mcp", handleSession);

  const http = config.server.http;
  const LOOPBACK = new Set(["127.0.0.1", "localhost", "::1", "[::1]"]);
  if (
    http.enableDnsRebindingProtection &&
    !http.allowedHosts &&
    !LOOPBACK.has(http.host)
  ) {
    process.stderr.write(
      `[ditto-mcp] WARNING: host '${http.host}' is non-loopback but server.http.allowedHosts is not set; ` +
        `DNS-rebinding protection will 403 remote requests whose Host header is not in the derived loopback allowlist. ` +
        `Set server.http.allowedHosts explicitly for remote deployments.\n`,
    );
  }

  return app;
}
