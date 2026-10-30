# Build & test

Use `-T4` on every `mvn test` / `mvn verify`.

## Build

```bash
mvn clean install -DskipTests                     # everything, no tests
mvn compile -pl gateway/service -am -DskipTests   # one module plus what it depends on
mvn clean install -DskipTests -Djapicmp.skip=true # only for a deliberate public API break
./build-images.sh                                 # local Docker images (-p host:port for a proxy)
```

## Test

```bash
mvn test -T4 -pl things/service                                 # one module
mvn test -T4 -pl things/service -Dtest=ThingPersistenceActorTest # one class
mvn test -T4 -pl things/service -Dtest='ThingPersistenceActorTest#createThing'
mvn verify -T4 -pl connectivity/service                         # unit + *IT integration tests
mvn verify -T4 -pl connectivity/service -Dit.test=SomeIT        # one integration test
```

`-pl` on a module that depends on unbuilt SNAPSHOTs needs `-am`, or a prior
`mvn install -DskipTests`. MongoDB-backed tests (mostly `*IT`) start an embedded `mongod` via
flapdoodle — no Docker needed, but the first run downloads the binary.

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

Docker needs at least 2 CPUs and 4 GB RAM; services need 30–60 s to form the cluster.

## UI

```bash
cd ui && npm install && npm run build   # output in ui/dist/
```

## System tests

End-to-end tests live in a separate repository,
[eclipse-ditto/ditto-testing](https://github.com/eclipse-ditto/ditto-testing).
