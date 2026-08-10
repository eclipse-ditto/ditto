# ditto-mcp-server

Extensible MCP server for Ditto knowledge and tools. P1 = foundation
(config, plugin registry, server factory, stdio + streamable-HTTP transports,
`ping` tool). See the design spec and plans under `docs/superpowers/`.

## Requirements
- Node >= 22

## Develop
    npm install
    npm test          # vitest
    npm run typecheck # tsc --noEmit
    npm run dev:stdio # run stdio transport
    npm run dev:http  # run streamable-HTTP transport on :3000/mcp

## Configuration

Optional JSON config via `DITTO_MCP_CONFIG=/path/to/config.json`. All fields have defaults; see `src/config/schema.ts`.

### HTTP Server Options (`server.http`)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `host` | `string` | `"127.0.0.1"` | Bind address (loopback by default) |
| `port` | `number` | `3000` | Port to listen on |
| `enableDnsRebindingProtection` | `boolean` | `true` | Enable DNS rebinding protection (rejects requests with invalid Host/Origin headers) |
| `allowedHosts` | `string[]?` | `undefined` | Allowed Host header values (e.g., `["mcp.example.com:3000"]`). When undefined and protection is enabled, a loopback allowlist is derived: `["127.0.0.1:port", "localhost:port", "[::1]:port", "host:port"]` |
| `allowedOrigins` | `string[]?` | `undefined` | Allowed Origin header values (optional) |

### Remote Deployments

When binding a **non-loopback** host (e.g., `0.0.0.0` or a public IP), you **MUST** set `allowedHosts` explicitly. The SDK matches the full `Host` header (e.g., `mcp.example.com:3000`), so include the exact `host:port` values your clients will send. Without explicit `allowedHosts`, DNS-rebinding protection will reject all remote requests with HTTP 403.

Example config for remote deployment:
```json
{
  "server": {
    "http": {
      "host": "0.0.0.0",
      "port": 3000,
      "allowedHosts": ["mcp.example.com:3000"]
    }
  }
}
```

## Layout
- `src/core/` — shared types (`ToolDef`, `RequestCtx`)
- `src/registry/` — `ToolRegistry`
- `src/config/` — zod schema + loader
- `src/tools/` — tool implementations (`ping`) + wiring
- `src/server/` — `buildServer`, `createHttpApp`
- `src/bin/` — `stdio` and `http` entrypoints
