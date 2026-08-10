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

### Knowledge

The server exposes `search` and `get_chunk` tools backed by a pluggable
`KnowledgeSource` → `Retriever` core. You can index the public Ditto docs
(`llms.txt`), a local markdown directory, or both, and choose from three
retriever modes: keyword FTS (default), semantic vector search, or hybrid (RRF
fusion of both).

**On startup**, the server fetches the public docs and/or indexes the local
directory (if enabled). To disable this, set `knowledge.enabled=false` or
disable individual sources.

#### Retriever Modes

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.retriever` | `"fts" \| "vector" \| "hybrid"` | `"fts"` | Retriever mode: `fts` = SQLite FTS5 keyword search; `vector` = semantic vector search; `hybrid` = RRF fusion of FTS + vector |

**Default (`fts`)**: fast keyword search, no model download, works offline.

**Vector / Hybrid**: embed the entire corpus in memory at server startup and
download the BGE embedding model (~80MB, ONNX) on first run unless
`allowRemoteModels: false` + `modelPath` are set. The default `fts` mode loads
no embedding stack. See `embedding` config below.

#### Sources

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.enabled` | `boolean` | `true` | Enable knowledge tools (`search`, `get_chunk`) |
| `knowledge.publicSource.enabled` | `boolean` | `true` | Enable PublicSource (Ditto `llms.txt`) |
| `knowledge.publicSource.url` | `string` | `"https://eclipse.dev/ditto/llms.txt"` | URL to the `llms.txt` index |
| `knowledge.publicSource.maxDocs` | `number?` | `undefined` | Optional limit on the number of docs to fetch |
| `knowledge.localDir.enabled` | `boolean` | `false` | Enable LocalDirSource (index a local markdown directory) |
| `knowledge.localDir.path` | `string?` | `undefined` | Path to a local directory containing `.md` files |
| `knowledge.localDir.id` | `string` | `"local"` | Source ID for local chunks |

#### Embedding Config (for `vector` / `hybrid`)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.embedding.model` | `string` | `"Xenova/bge-small-en-v1.5"` | Hugging Face model ID |
| `knowledge.embedding.dim` | `number` | `384` | Embedding dimension (must match the model) |
| `knowledge.embedding.modelPath` | `string?` | `undefined` | Local path to the ONNX model (offline mode) |
| `knowledge.embedding.allowRemoteModels` | `boolean` | `true` | Allow model download from Hugging Face |
| `knowledge.embedding.cacheDir` | `string?` | `undefined` | Custom cache directory for downloaded models |

**Offline vector search**: set `allowRemoteModels: false` and provide
`modelPath` pointing to a pre-downloaded ONNX model directory.

#### Examples

**Hybrid retriever + local dir:**
```json
{
  "knowledge": {
    "retriever": "hybrid",
    "publicSource": { "enabled": true },
    "localDir": { "enabled": true, "path": "/home/user/my-docs" }
  }
}
```

**Offline vector search:**
```json
{
  "knowledge": {
    "retriever": "vector",
    "embedding": {
      "allowRemoteModels": false,
      "modelPath": "/opt/models/bge-small-en-v1.5"
    }
  }
}
```

**Disable knowledge:**
```json
{
  "knowledge": {
    "enabled": false
  }
}
```

### Persistence & Ingestion (P2b-2)

The knowledge index (chunks, FTS, and vectors) lives in a pluggable
`KnowledgeStore` backend. Currently, `SqliteKnowledgeStore` persists everything
to a single `.db` file; `PgKnowledgeStore` (pgvector + Postgres FTS) is next
(P2c).

#### Store Configuration

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `knowledge.store.kind` | `"sqlite" \| "pgvector"` | `"sqlite"` | Store backend: `sqlite` (file-based, default) or `pgvector` (Postgres + pgvector + FTS) |
| `knowledge.store.sqlite.path` | `string?` | `undefined` | Path to the SQLite file. If unset or missing, the server builds an in-memory index at startup. |
| `knowledge.store.pgvector.connectionString` | `string?` | `undefined` | Postgres connection string (e.g., `postgresql://user:pass@host:5432/ditto`). Required when `kind: "pgvector"`. |
| `knowledge.store.pgvector.table` | `string` | `"ditto_kn"` | Table name prefix for Postgres tables (chunks, FTS, vectors). |

**Example (SQLite persistent store):**
```json
{
  "knowledge": {
    "retriever": "fts",
    "store": { "kind": "sqlite", "sqlite": { "path": "/var/lib/ditto-mcp/index.db" } }
  }
}
```

**Example (Postgres / pgvector store — AWS RDS):**
```json
{
  "knowledge": {
    "retriever": "hybrid",
    "store": {
      "kind": "pgvector",
      "pgvector": {
        "connectionString": "postgresql://user:password@my-rds.c9akciq32.us-east-1.rds.amazonaws.com:5432/ditto",
        "table": "ditto_kn"
      }
    }
  }
}
```

#### Postgres / pgvector (P2c-2)

When `knowledge.store.kind: "pgvector"`, the server uses Postgres for persistence:
- **Vectors**: stored in a `pgvector` column (requires the `vector` extension)
- **Keyword index**: built using Postgres `tsvector` FTS
- **Chunks**: stored in a text table

**Setup:**
1. Create a Postgres database (e.g., `ditto`).
2. Ensure the `vector` extension is available. For **AWS RDS**:
   - Create a custom parameter group with `rds.extensions = 'vector'`
   - Apply it to your Postgres instance
   - Connect and run `CREATE EXTENSION IF NOT EXISTS vector;`
3. Configure the connection string in your config JSON.

**Ingest:** The `ingest` command fetches the corpus, embeds vectors, and writes all chunks/FTS/vectors to Postgres:
```bash
DITTO_MCP_CONFIG=/path/to/config.json npm run dev:ingest
```

On the first run, the server validates index metadata (retriever, embedding model/dim, schema version). On re-ingest, the server calls `reset()` (drops the vec table and clears chunks/meta), rebuilds the index, and sets the completion flag — this is an **offline/maintenance operation** (not zero-downtime for a live instance). Re-ingest always replaces the full index from scratch, and can survive embedding-dim changes (reset() drops and recreates the vec table).

**Server Load-or-Build Behavior:**
- **Index exists & metadata matches**: server connects and serves immediately (no rebuild).
- **Index missing or metadata mismatch**: server logs a warning and disables knowledge tools (pgvector backend does NOT fall back to in-memory build — you MUST run `ingest` to populate Postgres before the server can serve knowledge).
- **Connection failure**: server exits with an error (Postgres backend requires a live database).

**Integration Tests:**
Postgres integration tests run via `npm run test:pg` (requires Docker and testcontainers):
```bash
npm run test:pg
```
These tests are **excluded** from the default `npm test` suite to keep the default test run hermetic (no Docker, no network, no external dependencies).

#### Ingest Command

To pre-build the index and persist it to a file (SQLite) or database (Postgres), use the `ingest` command:

```bash
# Development
DITTO_MCP_CONFIG=/path/to/config.json npm run dev:ingest

# Built
DITTO_MCP_CONFIG=/path/to/config.json ditto-mcp-ingest
```

The `ingest` command reads the config, fetches/indexes the corpus, and writes the store (file path for SQLite, or Postgres for pgvector). The config must specify a valid store location, or `ingest` will error.

#### Server Load-or-Build Behavior

When the server starts:
- **Populated store exists & metadata matches**: opens the prebuilt store and validates index metadata (retriever mode, embedding model/dim, schema version, and completion flag). If metadata matches the config, the store is served instantly (no fetch/embed).
- **SQLite — missing/empty/mismatched store**: builds the index in memory (fallback mode). The server never writes the file automatically — use `ingest` to persist. A corrupt or mismatched file at `knowledge.store.sqlite.path` never crashes the server or disables knowledge — it triggers the same in-memory fallback as a missing file.
- **Postgres — missing/empty/mismatched store**: server logs a warning and disables knowledge tools (pgvector backend does NOT fall back to in-memory build — you MUST run `ingest` to populate Postgres before the server can serve knowledge).
- **Postgres connection failure**: exits with an error. Postgres backend requires a live database.

The `ingest` command uses backend-specific atomic writes:
- **SQLite**: temp file + rename on success (zero-downtime, crash-safe).
- **Postgres**: `reset()` → rebuild → set completion flag (offline operation; re-ingest requires downtime, but can survive embedding-dim changes since reset() drops and recreates the vec table).

The async `KnowledgeStore` lifecycle (`isPopulated()`, `getMeta()`, `setMeta()`, `reset()`, `close()`) enables both `SqliteKnowledgeStore` and `PgKnowledgeStore` to plug in behind the same interface with no churn to `build.ts` or `build-index.ts`. Both backends validate metadata and support offline re-ingest.

## Action tools (P3-1)

The server exposes action tools dynamically generated from a Ditto OpenAPI specification.
Each action tool makes HTTP calls to a Ditto instance, enforcing a configurable **ToolPolicy**
(read-only by default) and **credential passthrough** (the MCP forwards credentials to Ditto,
which enforces authorization; the MCP never decides access beyond policy gating).

### Enable Action Tools

Set `ditto.enabled: true` and provide either a `ditto.baseUrl` or fetch the OpenAPI spec from a custom location:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ditto.enabled` | `boolean` | `false` | Enable action tools |
| `ditto.baseUrl` | `string` | (required if enabled) | Base URL to the Ditto instance (e.g., `http://localhost:8080`) |
| `ditto.openApi.path` | `string?` | `undefined` | Path to the OpenAPI spec file (reads locally). If unset, falls back to bundled spec. |
| `ditto.openApi.url` | `string?` | `undefined` | URL to fetch the OpenAPI spec from (remote fetch). If unset, falls back to bundled spec. |

If both `path` and `url` are unset, the server uses the bundled pinned Ditto OpenAPI spec
(`mcp/assets/ditto-openapi.yml`), which is a snapshot of a known Ditto release and works offline.

### Credential Passthrough

Action tools support two credential modes:

1. **Session-level credentials** (per tool call): The caller can pass an `Authorization` header
   with each tool invocation. The header is forwarded to the Ditto backend.

2. **Config-level credentials** (`ditto.credential`): A static credential (basic auth or devops token)
   configured at server startup, forwarded to every action-tool call unless overridden by a per-session
   `Authorization` header.

**Credential types:**
- `basic` — username + password (sent as `Authorization: Basic <base64(user:pass)>`)
- `devops` — a static token (sent as `Authorization: Bearer <token>`)
- `oidc` — OAuth2 client-credentials flow (sends `Authorization: Bearer <access_token>`)

**OIDC client-credentials:** Set `ditto.credential.kind: "oidc"` to enable OAuth2 client-credentials.
The MCP exchanges `clientId` + `clientSecret` for an access token on the first action-tool call, caches it,
and auto-refreshes ~30 seconds before expiry. The access token is forwarded as `Authorization: Bearer <token>`.

OIDC credential fields:
- `tokenUrl` (required) — OAuth2 token endpoint (e.g., `https://auth.example.com/oauth/token`)
- `clientId` (required) — OAuth2 client identifier
- `clientSecret` (required) — OAuth2 client secret (never logged)
- `scope` (optional) — OAuth2 scopes (space-separated; e.g., `"scope1 scope2"`)

Example OIDC config:
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

**Authorization enforcement:** The MCP never decides authorization. It forwards the credential
(or `Authorization` header) and lets the Ditto backend enforce access control. Credentials are
never logged by the server.

### ToolPolicy

By default, action tools only expose read (`GET`) operations. Write and privileged operations
require explicit allowlisting:

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `ditto.policy.allowMethods` | `string[]` | `["GET"]` | Wholesale HTTP method allowlist (applies to all non-sudo operations). Keep this `["GET"]` for read-only; expand for writes. |
| `ditto.policy.writeAllowlist` | `string[]` | `[]` | Per-operation granular allowlist for enabling specific write operations (operationIds). Use this to enable individual writes when `allowMethods` includes write verbs. |
| `ditto.policy.sudoAllowlist` | `string[]` | `[]` | Per-operation allowlist for sudo/devops-privileged operations (operationIds). Required for `/api/2/connections*`, `/devops/*`, and `sudo*` operations. |

**Sudo operations & devops flag:**
Ditto secures `/api/2/connections*` (secret-bearing) with `DevOpsBasic`/`DevOpsBearer` security,
and `/devops/*` paths are devops-privileged. These operations are classified as "sudo" and:
- Must be explicitly listed in `sudoAllowlist` (by operationId).
- Require a devops-capable credential. If a devops credential is not present, the operation is refused and never sent to Ditto.
- Are NOT auto-allowed even if the method is `GET` and in `allowMethods`.

Set `credential.devops: true` as an **operator assertion** that the credential is devops-capable.
This gates `sudo*`/`/devops`/`/connections` tools at the MCP layer — Ditto still enforces the real
authorization. Deriving `devops` from token introspection/claims is a future enhancement (currently,
only `basic` and `devops` kinds are implicitly devops-capable; `oidc` requires explicit `devops: true`).

**Examples:**
- Read-only (default): `{ "allowMethods": ["GET"] }` — only non-sudo GET operations are allowed.
- Enable specific writes: `{ "allowMethods": ["GET", "POST", "PATCH"], "writeAllowlist": ["putThing", "modifyThing"] }` — enables specific write operations.
- Enable sudo: `{ "allowMethods": ["GET"], "sudoAllowlist": ["getConnections", "getLogging"] }` — enables specific devops-privileged operations (requires devops credential).

### Typed Request Bodies

Write tools (POST, PATCH, PUT) expose their top-level request body fields in the tool schema,
allowing clients to discover and validate the shape of the request. Nested objects are passed through
as freeform JSON (no further schema introspection). This surfaces the Ditto OpenAPI operation's
request body schema to the MCP tool layer without deeply traversing `allOf`, `oneOf`, or nested `$ref`s.

For example, a `createThing` operation with a top-level `body.attributes` field will expose
`attributes` as a schema input field; callers can then pass nested objects like `{ "color": "blue" }`
within that field.

### Action Tools Configuration Examples

**Example 1: Read-only access (default policy):**
```json
{
  "ditto": {
    "enabled": true,
    "baseUrl": "http://localhost:8080",
    "credential": {
      "type": "basic",
      "username": "ditto",
      "password": "ditto"
    }
  }
}
```
This uses the bundled spec (offline) and forwards basic auth to Ditto. Only `GET` operations are available.

**Example 2: Write + sudo operations with devops credential:**
```json
{
  "ditto": {
    "enabled": true,
    "baseUrl": "http://localhost:8080",
    "credential": {
      "type": "devops",
      "token": "my-devops-secret"
    },
    "policy": {
      "allowMethods": ["GET", "POST", "PATCH", "DELETE"],
      "writeAllowlist": ["/api/2/things", "/api/2/things/{thingId}"],
      "sudoAllowlist": ["/devops/piggyback/send"]
    }
  }
}
```
This enables write operations on things and `/devops/piggyback/send` (sudo). The devops credential
is forwarded to Ditto for all requests.

**Example 3: Custom OpenAPI spec from URL:**
```json
{
  "ditto": {
    "enabled": true,
    "baseUrl": "http://localhost:8080",
    "openApi": {
      "url": "http://my-ditto:8080/openapi.json"
    },
    "credential": {
      "type": "basic",
      "username": "user",
      "password": "pass"
    }
  }
}
```
This fetches the OpenAPI spec from a remote URL instead of using the bundled spec.

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

Dependencies: `@modelcontextprotocol/sdk`, `express`, `zod`, `better-sqlite3`, `@huggingface/transformers`, `sqlite-vec`
