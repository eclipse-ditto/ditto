# Build & test

Use `-T4` on every `mvn test` / `mvn verify`.

## Build

```bash
mvn clean install -DskipTests -DskipITs                     # everything, no tests (failsafe ignores -DskipTests)
mvn compile -pl gateway/service -am -DskipTests             # one module plus what it depends on
mvn clean install -DskipTests -DskipITs -Djapicmp.skip=true # only for a deliberate public API break
./build-images.sh                                           # local Docker images (-p host:port for a proxy)
BAKE_POSTGRES_EXTENSIONS=true ./build-images.sh             # + the PostgreSQL extension JARs (snapshot images)
```

Baking refuses shaded extension JARs left stale by an incremental build: `clean install` first.

## Test

```bash
mvn test -T4 -pl things/service                                 # one module
mvn test -T4 -pl things/service -Dtest=ThingPersistenceActorTest # one class
mvn test -T4 -pl things/service -Dtest='ThingPersistenceActorTest#createThing'
mvn verify -T4 -pl connectivity/service                         # unit + *IT integration tests
mvn verify -T4 -pl connectivity/service -Dit.test=SomeIT        # one integration test
mvn verify -T4 -pl internal/utils/search-r2dbc -Dditto.test.postgres.image=postgres:17  # PostgreSQL ITs
```

`-pl` on a module that depends on unbuilt SNAPSHOTs needs `-am`, or a prior
`mvn install -DskipTests -DskipITs`. Database `*IT`s need Docker: MongoDB ones (`MongoDbResource`)
fail without it, PostgreSQL ones (Testcontainers) skip — check the `Skipped` count. CI's
`postgres-its` job runs them on PostgreSQL 16–18 and fails on any skip. It runs the four modules in
`POSTGRES_IT_MODULES` plus connectivity's `EncryptionMigrationPostgresIT` by name
(`.github/workflows/maven.yml`) — a new PostgreSQL IT elsewhere must be added there.

## Quality checks

```bash
mvn license:check                      # EPL-2.0 headers (license:format adds them)
mvn clean install -P inspect-deprecations
mvn clean verify                       # JaCoCo report in target/site/jacoco/
```

## Run locally

```bash
cd deployment/docker
cp dev.env .env          # use locally built SNAPSHOT images; omit for released images
docker compose up -d     # API on http://localhost:8080, devops user devops:foobar
docker compose logs -f gateway
```

Services need 30–60 s to form the cluster. On PostgreSQL: `deployment/postgres-local/README.md`
(Compose file for the database, `run-compound-postgres.sh`, `.run/*-Postgres.run.xml` run configurations).

## UI

```bash
cd ui && npm install && npm run build   # output in ui/dist/
```

## System tests

End-to-end tests live in a separate repository,
[eclipse-ditto/ditto-testing](https://github.com/eclipse-ditto/ditto-testing).
