---
paths:
  - "**/src/test/**"
---

# Tests

- Cover corner cases, not just the happy path: `null`/empty input, boundaries, malformed or
  invalid data, expected exceptions and error responses, unicode/special characters in ids
  and JSON keys.
- Most of the codebase is JUnit 4 (`org.junit.Test`) with AssertJ; a few modules use JUnit 5.
  Match what the module already uses.
- Value types get an `EqualsVerifier.forClass(...).verify()` test, as the existing
  `Immutable*Test` classes do.
- Integration tests are `*IT.java` and run in `mvn verify` (failsafe), not `mvn test`; skipping
  them takes `-DskipITs` (`-DskipTests` alone does not).
- Run with `-T4`: `mvn test -T4 -pl things/service -Dtest=ThingPersistenceActorTest`.
- Public API modules (`*/model`, `json`, `protocol`, `rql/*`, …) compile tests at Java 8 too.

## Actor tests

- Use Pekko `TestKit` / `TestProbe` and real actors. Avoid `TestActorRef`: it processes
  messages synchronously and deadlocks on low-core CI runners even when it passes locally.
- Avoid `MockedStatic` for factories; give the actor a package-private `propsForTests(...)`
  that takes the collaborator (see `MqttClientActor`).
- Tight timeouts that only fail on slow CI belong in test config overrides, not in changed
  production defaults.
- Reuse the existing bases where they fit: `PersistenceActorTestBase` (things, policies),
  `AbstractThingEnforcementTest`.

## Database-backed tests

- MongoDB `*IT`s start a container through `MongoDbResource` and fail without Docker;
  `HOSTING_ENVIRONMENT=IDE` points them at a MongoDB on localhost instead.
- PostgreSQL `*IT`s use Testcontainers through `PostgresDbResource` (`postgres-client` test-jar),
  the image from `-Dditto.test.postgres.image`, and skip via `Assume` without Docker — read the
  `Skipped` count, not the exit code. CI's `postgres-its` job fails on any skip.
- `SearchBackendParityIT` decides search semantics: one corpus
  (`internal/utils/search-r2dbc/src/test/resources/parity/`) on both backends, MongoDB's result is
  the truth, no Docker skip.
  A divergence needs an entry in `divergence-allowlist.json`, a `queries.json` case naming it as
  `divergence` and pins for both backends; `expectNonEmptySudo` keeps a case from passing on
  empty results.
- ArchUnit tests (`PersistenceBoundaryArchTest` per service, `MongoModuleNoPostgresArchTest`,
  `PostgresSearchNoMongoArchTest`) fail main code that imports the other backend.
