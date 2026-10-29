# Architecture

Five services form one Pekko cluster. They talk only via Pekko messages (cluster sharding,
Ditto's own pub/sub, direct actor messaging) — never HTTP. Each persisting service has its own
MongoDB database for journal and snapshots. Deep dives in `.claude/deep-dives/` cover the
flows end to end; ADRs in `documentation/src/main/resources/architecture/` record the why.

## Services

| Service          | Module          | Responsibility                                                        |
|------------------|-----------------|-----------------------------------------------------------------------|
| **things**       | `things/`       | Thing persistence; inline policy enforcement; twin + live; WoT checks |
| **policies**     | `policies/`     | Policy persistence and imports; source for all policy enforcer caches |
| **gateway**      | `gateway/`      | Stateless HTTP/WebSocket/SSE edge; authentication (JWT/OIDC); WoT TDs |
| **connectivity** | `connectivity/` | Connections to MQTT, AMQP 0.9.1/1.0, Kafka, HTTP; payload mapping     |
| **thingsearch**  | `thingsearch/`  | CQRS read side: MongoDB search index, RQL → Mongo queries, metrics    |

gateway and connectivity are the "edge" services: they share `edge/service` (signal
dispatching, acknowledgement aggregation, streaming) and are where signal enrichment happens
(`.claude/deep-dives/Enrichment.md`). Behaviour is customised via `DittoExtensionPoint`s
(`internal/utils/extension`).

## Patterns that span services

- **Event sourcing** — `AbstractPersistenceActor` (`internal/utils/persistent-actors`);
  persistence id `{entity-type}:{entity-id}`, e.g. `thing:org.eclipse.ditto:my-thing`.
  Persisted events and snapshots must stay deserializable across versions.
- **Commands and events as strategies** — `{service}/service/.../persistence/actors/strategies/
  commands/` (validate command, produce event) and `.../strategies/events/` (apply event to
  entity). A new command is a new strategy plus registration in the `*Strategies` collection.
- **Supervision** — `DittoRootActor` → `AbstractPersistenceSupervisor` → enforcer + persistence
  actor per entity. The supervisor runs enforcement before a command reaches persistence.
- **Policy enforcement** — in the things and policies services since the concierge service was
  removed (DADR-0007, `.claude/deep-dives/Concierge-Removal.md`). `PolicyEnforcerProvider`
  caches enforcers; thingsearch applies READ permissions inside its Mongo queries.
- **Ditto Protocol** — `protocol/` converts signals to/from the JSON envelope used on
  WebSocket and connections (`DittoProtocolAdapter`). Twin = persisted, live = pass-through.

## Module layering

```
{service}/service  ──►  internal/*, edge/*, base/service  ──►  */model, protocol, placeholders, rql/*
                                                        ──►  json, json-cbor, utils/jsr305
```

- Services never depend on another service's `service` module — only on its `model`.
- `*/model`, `json*`, `protocol`, `placeholders`, `rql/*`, `utils/jsr305` are published public
  API (Java 8, japicmp) — see `.claude/rules/java8-public-api.md`.
- `internal/*` is shared infrastructure without compatibility promises.

## Tech stack

Java 25 (public API at Java 8), Maven, Apache Pekko (actors, cluster, sharding, HTTP,
persistence-mongodb), MongoDB, Kamon (metrics/tracing, Prometheus), Logback/SLF4J, Ditto's own
JSON API plus Jackson for CBOR, JUnit 4 (mostly) + AssertJ + Pekko TestKit. The Explorer UI in
`ui/` is TypeScript.
