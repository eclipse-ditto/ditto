# Persistence backends

MongoDB (default) and PostgreSQL are both extensions on two `DittoExtensionPoint`s:

| Key under `ditto.extensions`   | MongoDB (module, shaded JAR)                                                                          | PostgreSQL (module, shaded JAR)                                                                                   |
|--------------------------------|-------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `persistence-backend-provider` | `MongoPersistenceBackendProvider` (`internal/utils/persistence`, `ditto-mongo-persistence-extension`) | `PostgresPersistenceBackendProvider` (`internal/utils/persistence-r2dbc`, `ditto-postgres-persistence-extension`) |
| `search-persistence-provider`  | `MongoSearchPersistenceProvider` (`thingsearch/persistence-mongo`, `ditto-mongo-search-extension`)    | `PostgresSearchPersistenceProvider` (`internal/utils/search-r2dbc`, `ditto-postgres-search-extension`)            |

PostgreSQL adds the base JAR `ditto-postgres-client-extension` (`internal/utils/postgres-client`).
Contracts: `PersistenceBackendProvider` (`internal/utils/persistence-api`) and `SearchPersistenceProvider`
(`thingsearch/persistence-api`). Service main code depends on neither backend; ArchUnit tests pin this
(`.claude/rules/tests.md`).

## Selection

- Service confs include the fixed-name profiles `ditto-persistence-backend.conf` / `ditto-search-backend.conf`,
  which each backend JAR ships. They wire the provider, plugins and
  `ditto.persistence-backend.auto-start.<entity>` lists; the service confs assign those lists to Pekko.
- `PersistenceBackendBootstrap` (ServiceLoader) adjusts the raw config before the actor system exists;
  `DittoService.doStart()` first refuses zero or several distinct backends, naming `DITTO_PERSISTENCE_BACKEND`.
- Images: `/opt/ditto/backends/{mongodb,postgresql}/`; `DITTO_PERSISTENCE_BACKEND` (default `mongodb`) picks one
  (`context/deployment.md`).
- IDE: `ide-mongo-launcher` / `ide-postgres-launcher` run configurations.

On PostgreSQL, `read-journal.entity` (set by the stock service confs) binds the service's shared read
journal only; WoT validation configs read their history through a read journal bound to their own journal.

Design: `concepts/mongodb-persistence-extension-design.md`, `concepts/postgresql-persistence-design.md`;
config keys: `.claude/rules/config-and-helm.md`; operators: `installation-postgresql.md`,
`operating-postgresql.md`, `deployment/postgres/README.md`.
