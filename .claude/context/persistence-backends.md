# Persistence backends

MongoDB is the default; PostgreSQL is an opt-in extension. Both hang on two `DittoExtensionPoint`s:

| Key under `ditto.extensions`   | Serves                                    | Default, in the image                                            | PostgreSQL, extension JAR                                                 |
|--------------------------------|-------------------------------------------|------------------------------------------------------------------|---------------------------------------------------------------------------|
| `persistence-backend-provider` | journal, snapshots, read journal, cleanup | `MongoPersistenceBackendProvider` (`internal/utils/persistence`) | `PostgresPersistenceBackendProvider` (`internal/utils/persistence-r2dbc`) |
| `search-persistence-provider`  | search index, queries, background sync    | `MongoSearchPersistenceProvider` (`thingsearch/service`)         | `PostgresSearchPersistenceProvider` (`internal/utils/search-r2dbc`)       |

The contracts are `PersistenceBackendProvider` (`internal/utils/persistence-api`: plugin IDs per entity
type, `DittoReadJournal`, `SnapshotCodec`, operations, health, streaming, `bootstrapSchema()`,
`verifyPluginWiring()`) and `SearchPersistenceProvider` (`thingsearch/persistence-api`). The persistence
root actors call `verifyPluginWiring`, then `bootstrapSchema`, before anything else starts; `SearchRootActor`
only `bootstrapSchema`. `AbstractPersistenceActor` wraps the service's JSON snapshot serializer
(`snapshot-adapter`; the Mongo-era class names stay) in the provider's codec, so no backend needs an adapter
of its own. ArchUnit tests keep PostgreSQL out of service, `persistent-actors` and API main code and ban the
switchable MongoDB artifacts (read journal, Pekko Mongo plugins, raw driver) there; thingsearch's
search-index packages stay MongoDB by design (`.claude/rules/tests.md`).

## PostgreSQL modules

`internal/utils/postgres-client` (R2DBC pool, SSL, config, schema manager and bootstrap) is shaded into
the base JAR `ditto-postgres-client-extension`; `persistence-r2dbc` and `search-r2dbc` into the thin
`ditto-postgres-persistence-extension` and `ditto-postgres-search-extension` (by the three
`postgres-*-extension` modules); `postgres-extension-it` loads the real shaded JARs in child class
loaders. All are internal Java 25 modules — none is Java 8 or japicmp-guarded.
`enforce-no-r2dbc-in-service` keeps R2DBC out of the service allinones; `ide-postgres-launcher` is an
unpublished classpath aggregator for the `.run/* (Postgres)` run configurations.

## Activation

A service mounts the base JAR plus its thin JAR in `/opt/ditto/extensions/` (on the image classpath),
from one release — each carries a version marker, and the boot fails on a mismatch or a thin JAR without
base. One top-level `include required(classpath("ditto-postgres-persistence"))` (search: `…-search`)
swaps the provider, defines the `ditto-postgres-<entity>-journal`/`-snapshots` plugin blocks and pulls
the `ditto.postgresql.*` defaults from `ditto-postgres-client.conf`. A persistence service then narrows
`pekko.persistence.*.auto-start-*` to its own entity's plugins — nothing checks this; the first
persistent actor fails with a Pekko `ConfigurationException` — and needs `read-journal.entity`, which
the stock service confs set. `<service>-pg-dev.conf` are the local-dev variants.

Deployment: `context/deployment.md`; config keys: `.claude/rules/config-and-helm.md`; design:
`concepts/postgresql-persistence-design.md`; operators: `installation-postgresql.md`,
`operating-postgresql.md` and the runbook `deployment/postgres/README.md`.
