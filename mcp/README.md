# ditto-mcp-server

Extensible MCP server for Ditto knowledge and tools. P1 = foundation
(config, plugin registry, server factory, stdio + streamable-HTTP transports,
`ping` tool). P2a = knowledge: `search` and `get_chunk` tools backed by a
pluggable `KnowledgeSource` → `Retriever` core. See the design spec and plans
under `docs/superpowers/`.

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

### Knowledge (P2a)

The server exposes `search` and `get_chunk` tools backed by a pluggable
`KnowledgeSource` → `Retriever` core. P2a ships with `PublicSource` (Ditto
`llms.txt`) and a SQLite FTS5 keyword retriever.

**On startup**, the server fetches the public docs (configurable with
`knowledge.publicSource.url` and `knowledge.publicSource.maxDocs`). To disable
this network fetch, set `knowledge.enabled=false` or
`knowledge.publicSource.enabled=false`.

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.enabled` | `boolean` | `true` | Enable knowledge tools (`search`, `get_chunk`) |
| `knowledge.publicSource.enabled` | `boolean` | `true` | Enable PublicSource (Ditto `llms.txt`) |
| `knowledge.publicSource.url` | `string` | `"https://eclipse.dev/ditto/llms.txt"` | URL to the `llms.txt` index |
| `knowledge.publicSource.maxDocs` | `number?` | `undefined` | Optional limit on the number of docs to fetch |

Example config to disable knowledge:
```json
{
  "knowledge": {
    "enabled": false
  }
}
```

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
- `src/tools/` — tool implementations (`ping`, knowledge tools) + wiring
- `src/knowledge/` — corpus/retrieval core (`KnowledgeSource`, `Retriever`, `PublicSource`, `FtsRetriever`)
- `src/server/` — `buildServer`, `createHttpApp`
- `src/bin/` — `stdio` and `http` entrypoints

Dependencies: `@modelcontextprotocol/sdk`, `express`, `zod`, `better-sqlite3`
