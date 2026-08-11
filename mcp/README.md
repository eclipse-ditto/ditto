# Ditto MCP Server

Model Context Protocol (MCP) server for Eclipse Ditto. Provides two classes of tools:

1. **Knowledge tools** (`search`, `get_chunk`) — semantic/keyword search over Ditto documentation (public llms.txt + optional local markdown corpus)
2. **Action tools** (dynamically generated from OpenAPI) — query and manage Things, Policies, Connections, and other Ditto resources

## Requirements

- Node >= 22

## Quickstart

### Install & Build

```bash
cd mcp/
npm install
npm run build
```

### Run (stdio transport)

The MCP server runs in stdio mode by default (for Claude Desktop, Cline, and other MCP clients):

```bash
# Development (with tsx)
npm run dev:stdio

# Production (built)
node dist/bin/stdio.js
```

### Connect to Claude Desktop

Add the server to Claude Desktop's MCP config:

```bash
claude mcp add ditto \
  -e DITTO_MCP_CONFIG=/absolute/path/to/config.json \
  -- node /absolute/path/to/ditto/mcp/dist/bin/stdio.js
```

Or edit `~/Library/Application Support/Claude/claude_desktop_config.json` (macOS) / `%APPDATA%\Claude\claude_desktop_config.json` (Windows) directly:

```json
{
  "mcpServers": {
    "ditto": {
      "command": "node",
      "args": ["/absolute/path/to/ditto/mcp/dist/bin/stdio.js"],
      "env": {
        "DITTO_MCP_CONFIG": "/absolute/path/to/config.json"
      }
    }
  }
}
```

### Run (HTTP transport)

The server can also run over HTTP (streamable SSE transport):

```bash
# Development
npm run dev:http

# Production
node dist/bin/http.js
```

By default, the HTTP server binds to `127.0.0.1:3000` and serves at `/mcp`. Configure via `server.http` in the config (see below).

## Two Processes: Ingest vs. Server

The MCP server supports **persistent knowledge indexes** (SQLite or Postgres). The index must be built **before** the server starts (or the server falls back to building it in memory at startup).

- **Ingest process** (`ditto-mcp-ingest` or `npm run dev:ingest`) — builds and persists the knowledge index (fetch → chunk → embed → write to store). Run this once, or whenever your corpus changes.
- **Server process** (`ditto-mcp-stdio` / `ditto-mcp-http`) — serves MCP tools. Reads the prebuilt index if it exists and is valid; otherwise builds in memory (SQLite) or refuses to start (Postgres).

**When to run ingest:**

- After changing `knowledge.retriever`, `knowledge.embedding.model`, or `knowledge.embedding.dim` (metadata mismatch requires a rebuild).
- After adding/removing sources (`publicSource`, `localDir`).
- When you want to persist the index to disk (SQLite) or Postgres (pgvector).

**Ingest command:**

```bash
DITTO_MCP_CONFIG=/path/to/config.json npm run dev:ingest  # development
DITTO_MCP_CONFIG=/path/to/config.json ditto-mcp-ingest    # production
```

The ingest command reads `knowledge.store` from your config and writes the index to the configured location (SQLite file path or Postgres connection). If the config specifies `kind: "sqlite"` but no `sqlite.path`, ingest will error (it requires an explicit path to write to).

## Configuration

All configuration is optional. The server uses sensible defaults when no config is provided. Pass a JSON config file via the `DITTO_MCP_CONFIG` environment variable.

See `examples/` for reference configs. All examples are parse-tested in CI and won't rot.

### Server (HTTP transport options)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `server.name` | `string` | `"ditto-mcp"` | Server name exposed to MCP clients |
| `server.http.port` | `number` | `3000` | HTTP server port |
| `server.http.host` | `string` | `"127.0.0.1"` | Bind address (loopback by default) |
| `server.http.enableDnsRebindingProtection` | `boolean` | `true` | Enable DNS rebinding protection (rejects requests with invalid Host/Origin headers) |
| `server.http.allowedHosts` | `string[]?` | `undefined` | Allowed Host header values (e.g., `["mcp.example.com:3000"]`). When undefined and protection is enabled, a loopback allowlist is derived: `["127.0.0.1:port", "localhost:port", "[::1]:port", "host:port"]` |
| `server.http.allowedOrigins` | `string[]?` | `undefined` | Allowed Origin header values (optional) |

**Remote deployments:** When binding a non-loopback host (e.g., `0.0.0.0` or a public IP), you **MUST** set `allowedHosts` explicitly. The SDK matches the full `Host` header (e.g., `mcp.example.com:3000`), so include the exact `host:port` values your clients will send.

Example:
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

### Knowledge Tools

The server exposes `search` and `get_chunk` tools backed by a pluggable knowledge core. You can index:
- **Public Ditto docs** (fetched from `llms.txt` at startup, enabled by default)
- **Local markdown directory** (disabled by default)
- **Both** (merged corpus)

Choose a retriever mode:
- `fts` (default) — fast keyword search, no model download, works offline
- `vector` — semantic vector search (downloads BGE embedding model ~80MB on first run)
- `hybrid` — RRF fusion of FTS + vector (best recall)

The index can be stored in **SQLite** (file-based, default) or **Postgres** (pgvector + FTS).

#### Knowledge Config

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.enabled` | `boolean` | `true` | Enable knowledge tools |
| `knowledge.retriever` | `"fts" \| "vector" \| "hybrid"` | `"fts"` | Retriever mode |

#### Sources

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.publicSource.enabled` | `boolean` | `true` | Enable PublicSource (Ditto llms.txt) |
| `knowledge.publicSource.url` | `string` | `"https://eclipse.dev/ditto/llms.txt"` | llms.txt URL |
| `knowledge.publicSource.maxDocs` | `number?` | `undefined` | Optional doc limit |
| `knowledge.localDir.enabled` | `boolean` | `false` | Enable LocalDirSource (index a local markdown directory) |
| `knowledge.localDir.path` | `string?` | `undefined` | Path to local markdown directory |
| `knowledge.localDir.id` | `string` | `"local"` | Source ID for local chunks |

#### Embedding (for vector/hybrid)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.embedding.model` | `string` | `"Xenova/bge-small-en-v1.5"` | Hugging Face model ID |
| `knowledge.embedding.dim` | `number` | `384` | Embedding dimension (must match the model) |
| `knowledge.embedding.modelPath` | `string?` | `undefined` | Local path to ONNX model (offline mode) |
| `knowledge.embedding.allowRemoteModels` | `boolean` | `true` | Allow model download from Hugging Face |
| `knowledge.embedding.cacheDir` | `string?` | `undefined` | Custom cache directory for downloaded models |
| `knowledge.embedding.batchSize` | `number` | `32` | Embedding batch size |

**Offline vector search:** Set `allowRemoteModels: false` and provide `modelPath` pointing to a pre-downloaded ONNX model directory.

#### Store (persistence)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.store.kind` | `"sqlite" \| "pgvector"` | `"sqlite"` | Store backend |
| `knowledge.store.sqlite.path` | `string?` | `undefined` | Path to SQLite file. If unset, the server builds an in-memory index at startup. |
| `knowledge.store.pgvector.connectionString` | `string?` | `undefined` | Postgres connection string (required when `kind: "pgvector"`) |
| `knowledge.store.pgvector.table` | `string` | `"ditto_kn"` | Table name prefix for Postgres tables |

**SQLite (default):** File-based index. If `sqlite.path` is set and the file exists, the server loads it instantly (no fetch/embed). If missing/corrupt/mismatched, the server builds the index in memory (fallback mode). The server never writes the file automatically — use `ditto-mcp-ingest` to persist.

**Postgres (pgvector):** Requires the `vector` extension. If the index exists and metadata matches, the server loads instantly. If missing/mismatched, the server **refuses to start** (no in-memory fallback). You **must** run `ditto-mcp-ingest` to populate Postgres before starting the server.

**Postgres setup (AWS RDS example):**
1. Create a Postgres database.
2. Enable the `vector` extension:
   - Create a custom parameter group with `rds.extensions = 'vector'`
   - Apply it to your instance
   - Connect and run `CREATE EXTENSION IF NOT EXISTS vector;`
3. Configure `knowledge.store.pgvector.connectionString` in your config.
4. Run `ditto-mcp-ingest` to build the index.

**Testing Postgres:** Run `npm run test:pg` (requires Docker + testcontainers). These tests are excluded from the default `npm test` suite to keep the default test run hermetic.

### Ditto Action Tools

The server can expose action tools dynamically generated from a Ditto OpenAPI spec. Each tool makes HTTP calls to a Ditto instance. Credentials are passed through to Ditto (the MCP never decides authorization beyond policy gating). The default policy is **read-only** (`GET` only).

#### Ditto Config

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ditto.enabled` | `boolean` | `false` | Enable action tools |
| `ditto.baseUrl` | `string` | (required if enabled) | Base URL to Ditto instance (e.g., `http://localhost:8080`) |
| `ditto.openApi.path` | `string?` | `undefined` | Path to a local OpenAPI spec file. |
| `ditto.openApi.url` | `string?` | `undefined` | URL to fetch the OpenAPI spec from. |
| `ditto.openApi.version` | `string?` | `undefined` | Ditto git tag/ref (e.g. `3.6.0`) to fetch the matching spec for, via `versionUrlTemplate`. |
| `ditto.openApi.versionUrlTemplate` | `string` | eclipse-ditto raw URL | URL template with a `${version}` placeholder; override for forks/mirrors. |

Spec resolution precedence (first match wins): `path` > `url` > `version` > the in-repo canonical spec (`documentation/src/main/resources/openapi/ditto-api-2.yml`), which matches the checked-out Ditto version and works offline. Set `version` to target a different Ditto release at runtime (requires network).

#### Credentials

Action tools support two credential modes:

| Kind | Description |
|------|-------------|
| `basic` | Username + password (sent as `Authorization: Basic <base64(user:pass)>`) |
| `oidc` | OAuth2 client-credentials flow: requests a token from `tokenUrl` using `clientId` + `clientSecret`, sends it as `Authorization: Bearer <token>`, auto-refreshes ~30s before expiry |

**OIDC credential fields:**
- `tokenUrl` (required) — OAuth2 token endpoint
- `clientId` (required) — OAuth2 client identifier
- `clientSecret` (required) — OAuth2 client secret (never logged)
- `scope` (optional) — OAuth2 scopes (space-separated)

Example:
```json
{
  "ditto": {
    "enabled": true,
    "baseUrl": "http://localhost:8080",
    "credential": {
      "kind": "oidc",
      "tokenUrl": "https://auth.example.com/oauth/token",
      "clientId": "my-client-id",
      "clientSecret": "my-client-secret",
      "scope": "ditto:read ditto:write"
    }
  }
}
```

**Standard vs devops credentials:**

`ditto.credential` authenticates standard operations. `ditto.devopsCredential` (optional, same shape) authenticates **sudo** operations — `/devops/*`, `sudo*`, and the secret-bearing connectivity API (`/api/2/connections*`).

- Sudo operations use `devopsCredential` **exclusively**. If `devopsCredential` is not set, every sudo tool is refused at the MCP layer (even if `credential` could reach it).
- `devopsCredential` may be `basic` (Ditto `DevOpsBasic`: a devops user's username/password) or `oidc` (Ditto `DevOpsBearer`: OAuth2). For a separate devops OIDC identity, give it its own `clientId`/`clientSecret`.
- A per-session `Authorization` header overrides whichever credential the operation selected (standard for normal ops, devops for sudo ops).

Example (separate OIDC identities):

```json
{
  "ditto": {
    "enabled": true,
    "baseUrl": "http://localhost:8080",
    "credential":       { "kind": "oidc", "tokenUrl": "https://idp/token", "clientId": "app",    "clientSecret": "..." },
    "devopsCredential": { "kind": "oidc", "tokenUrl": "https://idp/token", "clientId": "devops", "clientSecret": "..." },
    "policy": { "sudoAllowlist": ["getConnections"] }
  }
}
```

**Migration from earlier configs:** the `devops` credential kind and the `devops: true` flag are removed. Move a devops credential into `ditto.devopsCredential` (use `kind: "basic"` for a devops username/password).

**Authorization enforcement:** The MCP never decides authorization. It forwards the credential (or `Authorization` header) and lets Ditto enforce access control. Credentials are never logged.

#### Policy

By default, action tools only expose **read** (`GET`) operations. Write and privileged operations require explicit allowlisting:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ditto.policy.allowMethods` | `string[]` | `["GET"]` | Wholesale HTTP method allowlist (applies to all non-sudo operations) |
| `ditto.policy.writeAllowlist` | `string[]` | `[]` | Per-operation granular allowlist for enabling specific write operations (operationIds) |
| `ditto.policy.sudoAllowlist` | `string[]` | `[]` | Per-operation allowlist for sudo/devops-privileged operations (operationIds) |

**Sudo operations & devops credential:**

Ditto secures `/api/2/connections*` (secret-bearing) with `DevOpsBasic`/`DevOpsBearer` security, and `/devops/*` paths are devops-privileged. These operations are classified as "sudo" and:
- Must be explicitly listed in `sudoAllowlist` (by operationId)
- Require `ditto.devopsCredential` to be configured.
- Are NOT auto-allowed even if the method is `GET` and in `allowMethods`.

Connectivity is always devops-gated (classified sudo regardless of the OpenAPI spec's declared security), but policy granularity is unchanged — `sudoAllowlist` is a per-`operationId` opt-in (default `[]` = all sudo blocked). Allow connections while blocking direct-actor/devops commands by listing only the connection operationIds:

- Connections read only: `"sudoAllowlist": ["getConnections", "getConnection"]`
- Full connections CRUD, still blocking piggyback/devops: `"sudoAllowlist": ["getConnections","getConnection","createConnection","modifyConnection","deleteConnection"]`

All sudo operations still require `ditto.devopsCredential` to be set.

**Examples:**

- **Read-only (default):** `{ "allowMethods": ["GET"] }` — only non-sudo GET operations are allowed.
- **Enable specific writes:** `{ "allowMethods": ["GET", "POST", "PATCH"], "writeAllowlist": ["putThing", "modifyThing"] }` — enables specific write operations.
- **Enable sudo:** `{ "allowMethods": ["GET"], "sudoAllowlist": ["getConnections", "getLogging"] }` — enables specific devops-privileged operations (requires devops credential).

### Tools Exposed

When running, the server exposes:

**Core tools:**
- `ping` — health check (enabled by default via `tools.ping`; independent of `knowledge`/`ditto`)

**Knowledge tools (if `knowledge.enabled: true`):**
- `search` — semantic/keyword search over the knowledge corpus
- `get_chunk` — retrieve a specific chunk by ID

**Action tools (if `ditto.enabled: true`):**

Dynamically generated from the Ditto OpenAPI spec. Examples:
- `getThing`, `putThing`, `modifyThing`, `deleteThing`
- `getPolicy`, `putPolicy`, `modifyPolicy`, `deletePolicy`
- `getConnections`, `createConnection`, `modifyConnection`, `deleteConnection` (sudo, requires devops credential + `sudoAllowlist`)
- `sudoRetrieveThing`, `piggybackSend` (sudo)

Write tools (POST, PATCH, PUT) expose their top-level request body fields in the tool schema, allowing clients to discover and validate the shape of the request. Nested objects are passed through as freeform JSON.

## Security & OSS Notes

**Commit the engine, NOT your corpus/built index/secrets:**

- The built knowledge index (`.db` files) is gitignored. Commit the code, not the index.
- Local corpus directories (markdown files) may contain proprietary content — do NOT commit them to public repos unless intended.
- Credentials in `config.local.json` or other configs may contain secrets — do NOT commit them. Use environment variables or secret managers in production.
- Downloaded embedding models (`models/`, `.cache/`) are gitignored.

**Credential passthrough:**

The MCP forwards credentials to Ditto, which enforces authorization. The MCP never decides access beyond policy gating (allowMethods, writeAllowlist, sudoAllowlist). Credentials are never logged by the server.

**Read-only default:**

The default policy (`allowMethods: ["GET"]`) ensures action tools are read-only unless you explicitly enable writes. Sudo operations (connections, devops) require explicit `sudoAllowlist` + devops credential.

**Sudo/devops/connections gating:**

Connections and devops endpoints are gated at the MCP layer (sudo policy) to prevent accidental exposure of secret-bearing APIs. Ditto still enforces the real authorization.

## Testing

```bash
npm test              # hermetic unit tests (no Docker, no network)
npm run test:pg       # Postgres integration tests (requires Docker + testcontainers)
npm run typecheck     # TypeScript type check
```

The examples are parse-tested in `src/config/examples.test.ts` to ensure they stay valid.

## Examples

See `examples/` for reference configs:

- `public-fts.json` — simplest public quickstart (FTS, SQLite, llms.txt only)
- `hybrid-local.json` — hybrid retriever + local markdown directory + llms.txt
- `pgvector.json` — Postgres (pgvector) store for persistent index
- `ditto-readonly.json` — Ditto action tools with read-only policy (basic auth)
- `ditto-oidc-write.json` — Ditto action tools with OIDC client-credentials + write/sudo operations

All examples use placeholders (e.g., `REPLACE_ME`, `/path/to/...`) for secrets and paths. Replace these with your own values before use.

## Project Layout

- `src/core/` — shared types (`ToolDef`, `RequestCtx`)
- `src/registry/` — `ToolRegistry`
- `src/config/` — zod schema + loader
- `src/tools/` — tool implementations (`ping`, `search`, `get_chunk`) + wiring
- `src/knowledge/` — corpus/retrieval core (`KnowledgeSource`, `Retriever`, `PublicSource`, `LocalDirSource`, `FtsRetriever`, `VectorRetriever`, `HybridRetriever`)
- `src/ditto/` — action tools (OpenAPI → MCP tool schema, credential handling, policy enforcement)
- `src/server/` — `buildServer`, `createHttpApp`
- `src/bin/` — `stdio`, `http`, `ingest` entrypoints
- `examples/` — reference configs (parse-tested)

## Dependencies

- `@modelcontextprotocol/sdk` — MCP protocol
- `express` — HTTP server
- `zod` — config validation
- `better-sqlite3` — SQLite store
- `pg` — Postgres client (pgvector store)
- `sqlite-vec` — SQLite vector extension
- `@huggingface/transformers` — ONNX embedding models
- `yaml` — OpenAPI spec parsing
