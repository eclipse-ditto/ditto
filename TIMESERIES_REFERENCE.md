# Eclipse Ditto — Timeseries Feature Reference

Complete reference for the timeseries subsystem: ingest, storage, read APIs (single-Thing and cross-Thing), authorization, the pluggable-backend SPI, retention, configuration, and cross-protocol support.

| | |
|---|---|
| **Issue** | [#2291](https://github.com/eclipse-ditto/ditto/issues/2291) |
| **Branch** | `timeseries-main` |
| **Scope** | Ingest, single-Thing and cross-Thing read APIs, authorization, backend SPI, retention, configuration |
| **Verified** | 2026-08-03 against a local six-service cluster + MongoDB `ditto_ts` |

Every behavioural claim is backed by a live request/response pair in [§14 Scenario transcript](#14-scenario-transcript) (84 cases across HTTP, MongoDB and WebSocket).

---

## Table of contents

1. [What the feature is](#1-what-the-feature-is)
2. [Architecture and data flow](#2-architecture-and-data-flow)
3. [Storage model](#3-storage-model)
4. [Ingest](#4-ingest)
5. [Read API — single-Thing](#5-read-api--single-thing)
6. [Read API — cross-Thing aggregation](#6-read-api--cross-thing-aggregation)
7. [Authorization](#7-authorization)
8. [Pluggable-backend SPI](#8-pluggable-backend-spi)
9. [Retention and operations](#9-retention-and-operations)
10. [Configuration reference](#10-configuration-reference)
11. [Cross-protocol support](#11-cross-protocol-support)
12. [Limits and guards](#12-limits-and-guards)
13. [Findings for review](#13-findings-for-review)
14. [Scenario transcript](#14-scenario-transcript)
15. [Appendix — reproducing this run](#15-appendix--reproducing-this-run)

---

## 1. What the feature is

Ditto stores only the *current* state of a Thing. The timeseries feature adds durable **history** for selected feature properties, plus a read API to query it.

What it provides:

| Area | Capabilities |
|---|---|
| **Ingest** | WoT-driven opt-in, per-Thing sharded ingest entity, ingest-time tags |
| **Read — single Thing** | Raw reads, twelve aggregations, `fill=linear`, DST-aware bucketing, keyset pagination, tag filtering |
| **Read — cross-Thing** | Namespace-wide aggregation with `groupBy` and RQL `filter` (`GET /timeseries/things`) |
| **Platform** | `READ_TS` enforcement, pluggable-backend SPI with push-down/portable planning, retention |

Two decisions shape everything else:

- **Ingest is opt-in through the WoT Thing Model.** A property is recorded only if its schema carries a `ditto:timeseries` annotation. A Thing with no annotated property produces no points and no traffic (§E1).
- **Ingest is forward-only.** Annotating a property records values from that moment on; there is no backfill.

---

## 2. Architecture and data flow

```
                    WRITE PATH                                    READ PATH

  PUT /things/{id}/features/.../properties/x          GET /api/2/timeseries/things[/{id}/...]
                │                                                    │
                ▼                                                    ▼
      ThingPersistenceActor                                   TimeseriesRoute        (gateway)
        emits ThingEvent                                             │
                │                                                    ▼
                ▼                                          EdgeCommandForwarderActor (edge)
   TimeseriesIngestPublisher   (things service)                      │
     • ThingEventLeafExtractor → changed scalar leaves               ▼
     • WotLeafResolver → which leaves are annotated       ┌──────────┴──────────┐
     • resolves tag placeholders                          ▼                     ▼
                │  IngestDataPoints (batch, retried)   TimeseriesIngestActor  TimeseriesAggregateActor
                ▼                                      (sharded, per Thing)   (cluster singleton-ish)
   TimeseriesIngestActor  (timeseries service, sharded per Thing)     │                │
                │                                            RetrieveTimeseries   RetrieveAggregated…
                ▼                                                     └────────┬───────┘
        TimeseriesAdapter (SPI)  ── MongoDbTimeseriesAdapter                    ▼
                │                                                    TimeseriesQueryPlanner
                ▼                                                     push-down │ portable
      MongoDB Time Series collection  ts_<namespace>                            ▼
                                                                    TimeseriesComputeKernel
```

**`TimeseriesIngestPublisher`** (one per Things-service node) decomposes each `ThingEvent` into the scalar leaves it changed, keeps only those whose WoT schema declares `ingest: ALL`, resolves tag placeholders against the post-event Thing, and ships all points from one event as a single `IngestDataPoints` batch — retried with bounded attempts until the shard entity acks.

**`TimeseriesIngestActor`** is a sharded per-Thing entity that writes batches through the adapter and serves single-Thing reads. It deliberately uses **no Pekko Persistence**: there is no entity state that evolves over events — the durable truth is the MongoDB collection — and the publisher's retry already covers the same crash window a journal would. Adding one would cost an extra Mongo write per batch plus snapshot churn for redundant protection.

**`TimeseriesAggregateActor`** serves cross-Thing aggregation and owns the three-layer authorization described in §7.

---

## 3. Storage model

One native MongoDB **Time Series collection per namespace**, named `<collection-prefix><namespace with dots → underscores>` — e.g. namespace `ts.demo` → collection `ts_ts_demo`.

```json
{
  "timestamp": ISODate("2026-08-03T07:13:26.810Z"),   // timeField
  "value": 21,
  "meta": {                                            // metaField — indexed
    "thingId": "ts.demo:ref-a1",
    "path":    "/features/env/properties/flowTemperature",
    "unit":    "Cel",
    "tags":    { "attributes/building": "A" }
  },
  "revision": 2
}
```

Collection options as created (§F1):

```
timeseries: {"timeField":"timestamp","metaField":"meta","granularity":"seconds","bucketMaxSpanSeconds":3600}
expireAfterSeconds: 7776000  (90d)
```

Everything needed for grouping and filtering lives in the indexed `meta` sub-document, so **grouping and tag filtering never read measurement values**.

---

## 4. Ingest

### 4.1 The WoT annotation

```json
"flowTemperature": {
  "type": "number",
  "unit": "Cel",
  "ditto:timeseries": {
    "ingest": "ALL",
    "tags": { "attributes/building": "{{ thing-json:attributes/building }}" }
  }
}
```

- `ingest`: `ALL` records every change; `NONE` (or omitting the annotation) records nothing.
- `tags`: a map of tag key → value. Keys are **full Thing paths**; values may be constants or WoT placeholders resolved against the post-event Thing. A placeholder that resolves to nothing, or is malformed, drops that tag — it never breaks ingest.
- The annotation may sit on a **nested** property, so one scalar can opt in without dragging in siblings (e.g. an `updatedAt` field next to it).

The `@context` must bind the Ditto extension prefix:

```json
"@context": ["https://www.w3.org/2022/wot/td/v1.1",
             { "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#" }]
```

### 4.2 Verified ingest behaviour

| Behaviour | Evidence |
|---|---|
| Unannotated property produces **zero** points (`PUT` still returns 204) | §E1 |
| `unit` and resolved `tags` are stored on every point | §E2 |
| Tag placeholder `{{ thing-json:attributes/building }}` resolves to `"A"` | §E2 |
| Thing revision is recorded per point | §E2 |
| **Creating** a Thing with an annotated property already ingests a point | §6 fixtures, bucket B0 |
| Ingest is **not** gated by `READ_TS` — the permission gates reads only | §E4 |

That last row is what makes revocation retroactive: the data continues to exist, it simply stops being readable.

---

## 5. Read API — single-Thing

```
GET /api/2/timeseries/things/{thingId}/features/{featureId}/properties/{propertyPath}
GET /api/2/timeseries/things/{thingId}?paths=<p1>,<p2>,...
```

| Parameter | Meaning |
|---|---|
| `from`, `to` | ISO-8601 instants or relative (`now-1h`, `now`), both anchored to one `now`. |
| `step` | Bucket width. Omit for a raw read. |
| `agg` | Aggregation (see below). Requires `step` for the bucketed ones. |
| `percentile` | Companion to `agg=percentile`. |
| `fill` | `linear` interpolates empty interior buckets; `previous` carries forward. |
| `tz` | Zone for bucket alignment (DST-aware stepping). |
| `limit`, `cursor`, `order` | Keyset pagination and sort direction on raw reads. |
| `tagFilter` | Tag predicate, `key:value` form. |
| `timeFormat` | `ms` for epoch millis. |

### Aggregations

| Kind | Values | Available cross-Thing? |
|---|---|---|
| **Per-bucket** | `avg` `min` `max` `sum` `count` `first` `last` `stddev` | ✅ |
| **Window** | `derivative` `rate` `integral` `percentile` | ❌ — need the full ordered series of one Thing |

### Response

A bare JSON **array**, one entry per requested path:

```json
[{ "thingId": "...", "path": "...", "query": {...},
   "result": { "count": 1, "dataType": "number", "unit": "Cel",
               "tags": {"attributes/building": "A"},
               "hasMore": true, "nextCursor": "eyJ0IjoiMjAyNi0w..." },
   "data": [ {"t": "2026-08-03T07:11:50.703Z", "v": 20} ] }]
```

### Keyset pagination

`limit` bounds the page; `result.nextCursor` (base64 of `{"t":<timestamp>,"r":<revision>}`) fetches the next one; `hasMore` goes false at the end. Verified as a full round-trip in §E5: page 1 → `20`, page 2 → `21`, page 3 → `22`, `hasMore: false`.

Keyset rather than offset because timeseries data is append-heavy — an offset would skip or repeat rows as new points land mid-pagination.

---

## 6. Read API — cross-Thing aggregation

```
GET /api/2/timeseries/things
```

Aggregates across **all authorized Things of one namespace**, returning one series per `(group, path)` — "average `flowTemperature` per building, per minute, over the last hour".

| Parameter | Required | Meaning |
|---|---|---|
| `namespaces` | **yes** | Exactly one. A list is rejected (§C5) — storage is one collection per namespace. |
| `paths` | **yes** | Comma-separated property paths. |
| `from`, `to` | **yes** | As single-Thing. |
| `step` | **yes** | Aggregation is mandatory here. |
| `agg` | **yes** | Per-bucket aggregations only. |
| `groupBy` | no | `thingId`, `path`, or a bare tag path (`attributes/building`). `tag:<key>` is the legacy spelling. |
| `filter` | no | **RQL** predicate over ingest-time tags. |
| `fill`, `tz`, `timeFormat` | no | As single-Thing. |
| `maxGroups` | no | Default 1 000, hard ceiling 10 000. |

**Why aggregation is mandatory:** a raw cross-Thing read would stream every point of every Thing, making the response size a function of the tenant's Thing count rather than of the time range.

**Silently ignored:** `tagFilter`, `limit`, `cursor`, `order`, `percentile` — valid on the single-Thing endpoints, no effect here, no warning ([finding 3](#13-findings-for-review)).

### `filter` uses RQL and selects *points*, not Things

Same language as `/api/2/search/things`. Field references are tag keys, which are full Thing paths:

```
filter=and(eq(attributes/building,'A'),ge(attributes/floor,2))
```

Tags are frozen at ingest, so the filter selects by the state of the world **when the measurement was recorded**. For a Thing moved from building A to B, `filter=eq(attributes/building,'A')` returns only the points recorded while it was in A — not its whole history. Deliberate, and different from selecting Things by current attributes.

Safety of the translation: a tag key is a Thing path, so it contains `/` and never `.` — it cannot be misread by MongoDB as a nested field reference. Only the `meta.tags` sub-document is addressable, so a filter cannot reach `meta.thingId`, `meta.path` or the value to sidestep the per-path allow-list.

Verified operators: `eq` `ne` `in` `like` `exists` `and` `or` (§B7–§B13).

### Response

An **object**, not a bare array:

```json
{
  "results": [ { "group": {"attributes/building": "A"}, "path": "...",
                 "result": {...}, "data": [...] } ],
  "authorization": {
    "contributingThings": 4,
    "excludedThings": 2,
    "partial": true,
    "withheldByPath": { "/features/env/properties/flowTemperature": 2 }
  }
}
```

The `authorization` block is the feature's honesty mechanism — `partial: true` means the numbers are **not** the whole namespace.

---

## 7. Authorization

### 7.1 `READ_TS`

A dedicated permission, grantable per resource path like any other. Two modes:

| `ditto.timeseries.simplified-read-permission` | Behaviour |
|---|---|
| `false` (default) | Strict — timeseries reads require `READ_TS`. A plain `READ` grant is not enough. |
| `true` | A `READ` grant on the resource also unlocks timeseries reads. |

**Gotcha:** the grant must exist before the read, and in practice you want the policy in place before creating Things, since Thing creation itself already ingests a point.

### 7.2 Why nothing is stored with the data

Access is a statement about the **present**; data points are **historical**. Any snapshot of grants taken at ingest time would be wrong in at least one direction — a later grant would hide history, a revoke would keep leaking it. So authorization state is never written alongside points, and every request is evaluated against current policy. A subject granted access today sees history from before the grant; a revoked subject immediately loses access to history it could previously read.

This also keeps enforcement out of the adapter, so a new backend never re-implements it.

### 7.3 Cross-Thing: three layers

Cross-Thing enumerates, so it needs more than the single-Thing check.

**Layer 0 — `READ` on every field you `filter` or `groupBy`.**
Filtering by a tag reveals which points carry which value; grouping reveals its distinct values. Both require `READ` on that field **independently of `READ_TS` on the data**. Because tag keys are Thing paths, they are ordinary policy resources. Implemented with `FieldNamesPredicateVisitor` — the same visitor `ThingCommandEnforcement` uses for the `condition` header.
> §D4 groupBy → 403 · §D5 filter → 403 · §D6 no field referenced → 200

**Layer 1 — namespace-wide `READ_TS` via a namespace root policy.**
Requires `ditto.namespace-policies` to map the namespace to a policy granting `READ_TS` on **every** requested path. No root policy ⇒ denied outright — the most common cause of an unexpected 403. This bounds the work an unauthorized caller can provoke. It does **not** prove every Thing grants: root entries merge *additively*, so a Thing's own policy can still revoke.
> §D2 no root policy → 403 · §D8 revoked path → 403 · §D9 mixed path set → 403 (all-or-nothing)

**Layer 2 — per-Thing verification.**
Every Thing with data in the window is verified against live policy before any value is aggregated. Revoked Things are dropped, and the drop is *reported* rather than hidden.
> §D3 — `ref-x` (building A, value 99, `READ_TS` revoked) contributes nothing; building A stays 23.0/22.0; `partial: true`

Cost is one Thing lookup per contributing Thing (enforcers cached per policy) — bounded by `max-verified-things`. Exceeding it **fails the request** rather than authorizing a truncated set.

### 7.4 Deliberately absent

There is no "enforcement disabled" branch. One previously existed for tests and passed a `null` allow-list, which the adapter reads as *every Thing in the namespace*. An authorization boundary should not carry a bypass that only tests use.

---

## 8. Pluggable-backend SPI

`TimeseriesAdapter` is the backend contract; MongoDB is the only implementation today. The design goal is that a scan-only backend still works correctly, just slower.

**`Capabilities`** — what the backend can do natively:

| Flag | Meaning |
|---|---|
| `supportsNativeQuery()` | Can execute a whole `TimeseriesQuery` itself. |
| `supportsNativeCrossThingQuery()` | Can group/aggregate across Things in its own engine. |
| `pushableAggregations()` | Which aggregations it computes natively per bucket. |
| `nativeFillStrategies()` | Which fill strategies it implements. |

**`TimeseriesQueryPlanner`** picks per query between:
- **push-down** — capabilities say the backend can do it ⇒ delegate wholesale;
- **portable** — otherwise `scan()` raw points and compute in the shared kernel.

Both paths produce identical results.

**`TimeseriesComputeKernel`** is the backend-neutral reference implementation of bucket-grid stepping, gap fill, and the derived aggregations (`derivative`, `rate`, `integral`, `percentile`). It defines the *meaning* of each operation once — "the database reduces, the kernel decides" — and is the reference answer any native push-down must match.

**Cross-Thing has no portable fallback.** `queryCrossThing` defaults to `UnsupportedOperationException`, and a backend that doesn't advertise the capability gets a 400. Computing a cross-Thing grouping in service heap would mean scanning every matching series into memory — precisely the fan-out the endpoint's guard rails exist to prevent.

---

## 9. Retention and operations

Retention is MongoDB's native `expireAfterSeconds` on the timeseries collection.

- Default `90d`, per-namespace overrides via `retention-overrides`.
- `"unlimited"` / `"off"` / `"none"` disables expiry (default only — per-namespace unlimited is not supported).
- Applied at collection creation, and reconciled on an existing collection via `collMod`, so changing the config takes effect without a manual migration.
- Reconcile is **best-effort**: a failure logs a warning and leaves the existing retention alone rather than blocking startup.

**Important nuance — reconcile is lazy, not a startup sweep.** `ensureCollectionConfigured` runs *per collection, on first touch, once per JVM* (gated by an `ensuredCollections` cache). A namespace that stops receiving writes is never touched, so it keeps its old retention indefinitely. Live proof in §F1: `ts_ts_retention_demo` still carries `expireAfterSeconds: 604800` (7d) although no current config specifies an override for it. The config comment claiming reconciliation happens "on startup" overstates what the code does ([finding 6](#13-findings-for-review)).

Also visible in §F1: `ts_org_eclipse_ditto` is a **plain collection, not a timeseries collection, with no `expireAfterSeconds` at all** — a legacy artifact that predates the creation logic. It has no retention and will grow unbounded.

---

## 10. Configuration reference

`ditto.timeseries` (see `timeseries.conf`):

| Key | Default | Env override | Meaning |
|---|---|---|---|
| `simplified-read-permission` | `false` | `TIMESERIES_SIMPLIFIED_READ_PERMISSION` | `true` lets plain `READ` unlock timeseries reads. |
| `max-verified-things` | `1000` | `TIMESERIES_MAX_VERIFIED_THINGS` | Ceiling on Things authorized per cross-Thing request. |
| `adapter.type` | `mongodb` | — | Backend selection. |
| `adapter.mongodb.collection-prefix` | `ts_` | — | Collection name prefix. |
| `adapter.mongodb.granularity` | `seconds` | — | Mongo timeseries granularity. |
| `adapter.mongodb.retention` | `90d` | `TIMESERIES_MONGODB_RETENTION` | Default expiry; `unlimited` disables. |
| `adapter.mongodb.retention-overrides` | `{}` | (Helm) | Per-namespace expiry. |
| `adapter.mongodb.max-query-result-size` | `1000000` | `TIMESERIES_MONGODB_MAX_QUERY_RESULT_SIZE` | Per-path scan ceiling for raw/window paths. |
| `adapter.mongodb.query-timeout` | `60s` | — | Per-query timeout. |
| `adapter.mongodb.capabilities.*` | see conf | — | Declared push-down capabilities. |

Cross-Thing additionally requires namespace root policies:

```hocon
ditto.namespace-policies {
  "org.example.devices"   = ["org.example:tenant-root"]   # exact
  "org.example.*"         = ["org.example:tenant-root"]   # prefix wildcard
  "*"                     = ["root:catch-all-policy"]     # catch-all
}
```

`timeseries.conf` newly `include`s `ditto-namespace-policies.conf` — without it the config path is absent entirely and **every** cross-Thing aggregation is denied.

**Helm:** `global.namespacePolicies` renders into `service-config/timeseries-extension.conf.tpl`, consistent with policies/search/things. Retention overrides come from `timeseries.config.adapter.mongodb.retentionOverrides`.

**Local dev** needs an explicit entry (added to `timeseries-dev.conf` for this verification):

```hocon
namespace-policies { "ts.demo" = ["ts.demo:root"] }
```

**Dead key:** `ditto.mongodb.database = "ditto_ts"` in `timeseries.conf` is **vestigial** — `DefaultMongoDbConfig` has no `database` key and nothing reads it. The database name must be the path segment of the URI (`mongodb://host:27017/ditto_ts`) ([finding 7](#13-findings-for-review)).

---

## 11. Cross-protocol support

Ditto commands are expected to work over HTTP, WebSocket and Connectivity alike.

| Command | HTTP | WebSocket / Connectivity | Topic path |
|---|---|---|---|
| `RetrieveTimeseries` (single-Thing) | ✅ | ✅ fully wired | `<ns>/<name>/things/twin/timeseries/retrieve` |
| `RetrieveAggregatedTimeseries` (cross-Thing) | ✅ | ❌ **no adapter** | — none exists |

Single-Thing has the complete chain: `TimeseriesTopicPathBuilder`, `TimeseriesQueryCommandAdapter`, `TimeseriesQueryCommandResponseAdapter`, `TimeseriesQuerySignalMapper` (+ response), both mapping-strategies, and registrations in `MappingStrategiesFactory`, `SignalMapperFactory` and `AdapterResolverBySignal`.

Cross-Thing has none of it. Proven live in §G:

- **§G1** single-Thing over WebSocket → `200` with correct data.
- **§G2** cross-Thing over WebSocket → `400 protocoladapter:unknown.topicpath — Action name <aggregate> is unknown.`

---

## 12. Limits and guards

| Limit | Value | Where | On exceed |
|---|---|---|---|
| `maxGroups` default | 1 000 | `CrossThingTimeseriesQuery.DEFAULT_MAX_GROUPS` | — |
| `maxGroups` ceiling | 10 000 | `CrossThingTimeseriesQuery.MAX_GROUPS_CEILING` | 400 (§C11) |
| Distinct groups matched | caller's `maxGroups` | Mongo `$limit` in-pipeline | 400 — **fails, never truncates** (§C12) |
| Things authorized / request | 1 000 | `max-verified-things` | 400 |
| Scan ceiling per path | 1 000 000 pts | `max-query-result-size` | truncated + warning logged |
| Paths per query | 100 | `ImmutableTimeseriesQuery.MAX_PATHS` | 400 |
| Namespaces per request | exactly 1 | route | 400 (§C5) |
| `step` | positive, whole seconds | model | 400 (§C7, §C8) |
| `from` / `to` | `from` strictly before `to` | model | 400 (§C9) |
| Cross-Thing aggregations | the 8 per-bucket ones | `Aggregation.BUCKETED` | 400 (§C6) |

---

## 13. Findings for review

Ordered by severity. Findings 1–5 concern cross-Thing aggregation; 6–10 apply to the subsystem as a whole.

**1 — Cross-Thing is HTTP-only; no protocol-adapter wiring.** `RetrieveAggregatedTimeseries`/`…Response` appear nowhere under `protocol/`, while the sibling `RetrieveTimeseries` is fully wired. `TimeseriesRoute` even carries a comment claiming "*HTTP, WebSocket and Connectivity reject the same inputs*" — a parity that does not exist. **Reproduced live: §G2.**

**2 — Error messages name a parameter this endpoint ignores.** `MongoDbTimeseriesAdapter.java:600` and `TimeseriesAggregateActor.java:534` advise narrowing with `'tagFilter'`, but cross-Thing reads `filter` (RQL) and never `tagFilter`. Verbatim in §C12.

**3 — Sibling parameters silently dropped.** `tagFilter`, `limit`, `cursor`, `order`, `percentile` are accepted-and-ignored by the cross-Thing route rather than rejected. Each is meaningful on the single-Thing endpoints, so a query can look accepted while quietly returning unfiltered data. Compounds finding 2.

**4 — Dead code shadowing a canonical primitive.** `TimeseriesRqlTranslator.referencedFields` / `collectFields` are unused outside their own tests and hand-roll what `FieldNamesPredicateVisitor` already does. The actor correctly uses the canonical visitor; the copy is a liability if a future caller picks it for an authorization decision. Delete both.

**5 — The route test disables the control it should prove.** `TimeseriesRouteCrossThingTest:65` uses `new TimeseriesRoute(routeBaseProperties)`; that overload passes `null` for the validator factory, so namespace access control is off for all 223 lines. Production wiring (`GatewayRootActor:286`) is correct — the control is simply unverified at the route layer.

**6 — Retention reconcile is lazy, and the config comment says otherwise.** `ensureCollectionConfigured` runs per collection on first touch per JVM, not as a startup sweep, so a namespace that stops receiving writes keeps its old retention forever. Live proof: `ts_ts_retention_demo` at 7d with no configured source (§F1). Separately, `ts_org_eclipse_ditto` is a plain non-timeseries collection with **no retention at all** and will grow unbounded.

**7 — Vestigial config key.** `ditto.mongodb.database` in `timeseries.conf` is read by nothing; the DB name must live in the URI path segment. Reviewers will ask what consumes it — nothing does.

**8 — Deleting a Thing does not delete its timeseries data.** Discovered during this run: `ts.demo:probe` was deleted via the API, but its points survive until retention expires them. No policy exists to authorize them any more, so they are permanently unreadable *and* counted in `excludedThings` on every cross-Thing query in the window — which is why §B1–§B3 report `excludedThings: 2` when only `ref-x` was deliberately revoked. **`partial: true` can therefore be caused by data hygiene rather than by any authorization decision the caller can act on.** Needs an explicit decision: purge on delete, exclude orphans from the counts, or document it.

**9 — A mistyped reserved token degrades silently.** `GroupBy.parse` treats anything that is not `thingId`/`path` as a bare tag path — deliberate and documented, but `groupBy=thingid` (lowercase d) becomes a tag dimension matching nothing and returns 200 with `{"thingid": ""}` (§C14) instead of erroring.

**10 — No narrative documentation for the feature at all.** No page under `pages/ditto/`, no `ditto_sidebar.yml` entry, no `llms.txt` line — for any phase. A recent upstream commit ("Require all doc pages in llms.txt") suggests this is actively policed.

### Verified clean

OpenAPI complete: `crossThing.yml` registered in `api-2-index.yml`, `AggregatedTimeseriesResult` registered as a component schema, bundle `ditto-api-2.yml` regenerated and in sync, documented params exactly match the route (12 = 12), `200`/`400`/`401`/`403` all documented, EPL-2.0 headers present.

Also clean: cross-tenant trust boundary holds (operator-mandated root policy gates entry, live evaluation, additive-merge/revoke interaction correct); `@since 4.0.0` on all six new public types; `Immutable*` package-private; actor concurrency clean (sender captured before async hops, all fields final, no blocking, private ctor + `Props`); `logback.xml` consistent with every sibling service; RQL dependency layering matches `thingsearch/service`; `timeseries/model` Java 8 clean (bytecode major 52); Helm config surface complete.

---

## 14. Scenario transcript

84 cases, verbatim request and response.

| Section | Cases | Outcome |
|---|---|---|
| **A** — single-Thing read API | 21 | all 200 |
| **B** — cross-Thing happy paths | 25 | all 200 |
| **C** — validation and limits | 20 | 19 × 400, 1 × 200 (§C14, documented — finding 9) |
| **D** — authorization | 10 | 401 ×1, 403 ×4, 404 ×1, 200 ×4 — all as designed |
| **E** — ingest semantics | 5 | opt-in, tags, revision, read-not-write gating, pagination |
| **F** — storage and retention | 1 | collection layout, 90d default, orphaned 7d override |
| **G** — cross-protocol (WebSocket) | 2 | single-Thing 200; cross-Thing `unknown.topicpath` |

### Fixtures behind every response below

**Namespace** `ts.demo`, root policy `ts.demo:root` (`importable: implicit`) with three subjects:

| Entry | Subject | Grants |
|---|---|---|
| `owner` | `nginx:ditto` | `READ`,`WRITE`,`READ_TS` on `thing:/` |
| `limited` | `nginx:limited` | `READ`,`READ_TS` on `thing:/`; **`READ` revoked on `thing:/attributes/building`** |
| `pathlimited` | `nginx:pathlimited` | `READ_TS` on `flowTemperature` only; **revoked on `pressure`** |

Plus `ts.demo:norts` — `READ`,`WRITE` with **`READ_TS` explicitly revoked** (the namespace-root merge is additive, so a plain "don't grant" would still inherit the root grant).

**Five Things** on a two-property model, three one-minute buckets:

| Thing | building | policy | B0 07:11 | B1 07:13 | B2 07:14 |
|---|---|---|---|---|---|
| `ref-a1` | A | root | 20 | 21 | 22 |
| `ref-a2` | A | root | 20 | 25 | *(gap — deliberate)* |
| `ref-b1` | B | root | 20 | 31 | 32 |
| `ref-b2` | B | root | 20 | 35 | 36 |
| `ref-x` | A | **norts** | 20 | 99 | 99 |

B0 is the creation write. Expected arithmetic, all confirmed:

- building A → B1 `(21+25)/2 = 23`, B2 `22` (a2 has the gap)
- building B → B1 `(31+35)/2 = 33`, B2 `(32+36)/2 = 34`
- ungrouped → B1 `(21+25+31+35)/4 = 28`, B2 `(22+32+36)/3 = 30`
- `ref-x`'s 99s appear **nowhere**



## A. Single-Thing read API

The per-Thing read endpoints, included so cross-Thing behaviour can be compared against them.
### A1 — Raw read (no aggregation)

Without `step`/`agg` a single-Thing read streams the stored points as-is. The cross-Thing endpoint deliberately forbids this — see C5.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:22.713333Z",
            "to": "2026-08-03T07:15:22.713333Z"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel",
            "tags": {
                "attributes/building": "A"
            },
            "hasMore": false
        },
        "data": [
            {
                "t": "2026-08-03T07:11:50.703Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:26.810Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:37.074Z",
                "v": 22
            }
        ]
    }
]
```

### A2 — Bucketed average

The baseline aggregation: one value per `step` bucket.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:22.863234Z",
            "to": "2026-08-03T07:15:22.863234Z",
            "step": "PT1M",
            "aggregation": "avg"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20.0
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21.0
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22.0
            }
        ]
    }
]
```

### A3.min — agg=min

Per-bucket aggregation `min`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=min'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:22.921171Z",
            "to": "2026-08-03T07:15:22.921171Z",
            "step": "PT1M",
            "aggregation": "min"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22
            }
        ]
    }
]
```

### A3.max — agg=max

Per-bucket aggregation `max`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=max'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:22.972312Z",
            "to": "2026-08-03T07:15:22.972312Z",
            "step": "PT1M",
            "aggregation": "max"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22
            }
        ]
    }
]
```

### A3.sum — agg=sum

Per-bucket aggregation `sum`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=sum'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.023894Z",
            "to": "2026-08-03T07:15:23.023894Z",
            "step": "PT1M",
            "aggregation": "sum"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22
            }
        ]
    }
]
```

### A3.count — agg=count

Per-bucket aggregation `count`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=count'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.077478Z",
            "to": "2026-08-03T07:15:23.077478Z",
            "step": "PT1M",
            "aggregation": "count"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 1
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 1
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 1
            }
        ]
    }
]
```

### A3.first — agg=first

Per-bucket aggregation `first`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=first'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.128272Z",
            "to": "2026-08-03T07:15:23.128272Z",
            "step": "PT1M",
            "aggregation": "first"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22
            }
        ]
    }
]
```

### A3.last — agg=last

Per-bucket aggregation `last`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=last'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.179585Z",
            "to": "2026-08-03T07:15:23.179585Z",
            "step": "PT1M",
            "aggregation": "last"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22
            }
        ]
    }
]
```

### A3.stddev — agg=stddev

Per-bucket aggregation `stddev`, pushed down to MongoDB's $group.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=stddev'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.229855Z",
            "to": "2026-08-03T07:15:23.229855Z",
            "step": "PT1M",
            "aggregation": "stddev"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": null,
                "_gap": true
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": null,
                "_gap": true
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": null,
                "_gap": true
            }
        ]
    }
]
```

### A4 — agg=percentile

Window function — single-Thing only; rejected cross-Thing (C5).

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=percentile&percentile=95'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.283405Z",
            "to": "2026-08-03T07:15:23.283405Z",
            "step": "PT1M",
            "aggregation": "percentile",
            "percentile": 95.0
        },
        "result": {
            "count": 3,
            "dataType": "number"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20.0
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21.0
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22.0
            }
        ]
    }
]
```

### A5 — agg=derivative

Window function: rate of change between buckets. Single-Thing only.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=derivative'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.334856Z",
            "to": "2026-08-03T07:15:23.334856Z",
            "step": "PT1M",
            "aggregation": "derivative"
        },
        "result": {
            "count": 2,
            "dataType": "number"
        },
        "data": [
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 0.008333333333333333
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 0.016666666666666666
            }
        ]
    }
]
```

### A6 — agg=rate

Window function. Single-Thing only.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=rate'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.386551Z",
            "to": "2026-08-03T07:15:23.386551Z",
            "step": "PT1M",
            "aggregation": "rate"
        },
        "result": {
            "count": 2,
            "dataType": "number"
        },
        "data": [
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 0.008333333333333333
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 0.016666666666666666
            }
        ]
    }
]
```

### A7 — agg=integral

Window function. Single-Thing only.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=integral'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.436028Z",
            "to": "2026-08-03T07:15:23.436028Z",
            "step": "PT1M",
            "aggregation": "integral"
        },
        "result": {
            "count": 1,
            "dataType": "number"
        },
        "data": [
            {
                "t": "2026-08-03T07:14:37.074Z",
                "v": 3480.8695
            }
        ]
    }
]
```

### A8 — fill=linear over a gap

`ref-a2` deliberately has no B1 write, so bucket 2 is empty. `fill=linear` interpolates it.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a2/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=avg&fill=linear'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a2",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a2",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.487574Z",
            "to": "2026-08-03T07:15:23.487574Z",
            "step": "PT1M",
            "aggregation": "avg",
            "fillStrategy": "linear"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20.0
            },
            {
                "t": "2026-08-03T07:12:00Z",
                "v": 22.5,
                "_gap": true
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 25.0
            }
        ]
    }
]
```

### A9 — fill omitted (same gap)

Same query without `fill`: the empty bucket is simply absent.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a2/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a2",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a2",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.538666Z",
            "to": "2026-08-03T07:15:23.538666Z",
            "step": "PT1M",
            "aggregation": "avg"
        },
        "result": {
            "count": 2,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20.0
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 25.0
            }
        ]
    }
]
```

### A10 — tz=Europe/Berlin

Bucket boundaries align to the given zone (DST-aware stepping).

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1h&agg=avg&tz=Europe/Berlin'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.588439Z",
            "to": "2026-08-03T07:15:23.588439Z",
            "step": "PT1H",
            "aggregation": "avg",
            "timezone": "Europe/Berlin"
        },
        "result": {
            "count": 1,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:00:00Z",
                "v": 21.0
            }
        ]
    }
]
```

### A11 — limit (keyset pagination page 1)

Raw read bounded by `limit`; response carries a cursor.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=2'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.638510Z",
            "to": "2026-08-03T07:15:23.638510Z",
            "limit": 2
        },
        "result": {
            "count": 2,
            "dataType": "number",
            "unit": "Cel",
            "tags": {
                "attributes/building": "A"
            },
            "hasMore": true,
            "nextCursor": "eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMzoyNi44MTBaIiwiciI6Mn0"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:50.703Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:26.810Z",
                "v": 21
            }
        ]
    }
]
```

### A12 — order=desc

Sort order on the raw read.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=3&order=desc'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.689331Z",
            "to": "2026-08-03T07:15:23.689331Z",
            "limit": 3,
            "order": "desc"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel",
            "tags": {
                "attributes/building": "A"
            },
            "hasMore": false
        },
        "data": [
            {
                "t": "2026-08-03T07:14:37.074Z",
                "v": 22
            },
            {
                "t": "2026-08-03T07:13:26.810Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:11:50.703Z",
                "v": 20
            }
        ]
    }
]
```

### A13 — tagFilter

Single-Thing tag predicate. NOTE: the cross-Thing endpoint uses `filter` (RQL) instead.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&tagFilter=attributes/building:A'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.741718Z",
            "to": "2026-08-03T07:15:23.741718Z",
            "tagFilters": {
                "attributes/building": "A"
            }
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel",
            "tags": {
                "attributes/building": "A"
            },
            "hasMore": false
        },
        "data": [
            {
                "t": "2026-08-03T07:11:50.703Z",
                "v": 20
            },
            {
                "t": "2026-08-03T07:13:26.810Z",
                "v": 21
            },
            {
                "t": "2026-08-03T07:14:37.074Z",
                "v": 22
            }
        ]
    }
]
```

### A14 — Multi-property read (paths=)

Two properties of one Thing in a single request.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1?paths=/features/env/properties/flowTemperature,/features/env/properties/pressure&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature",
                "/features/env/properties/pressure"
            ],
            "from": "2026-08-03T06:15:23.794462Z",
            "to": "2026-08-03T07:15:23.794462Z",
            "step": "PT1M",
            "aggregation": "avg"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 20.0
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 21.0
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 22.0
            }
        ]
    },
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/pressure",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature",
                "/features/env/properties/pressure"
            ],
            "from": "2026-08-03T06:15:23.794462Z",
            "to": "2026-08-03T07:15:23.794462Z",
            "step": "PT1M",
            "aggregation": "avg"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "bar"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:00Z",
                "v": 1.0
            },
            {
                "t": "2026-08-03T07:13:00Z",
                "v": 1.1
            },
            {
                "t": "2026-08-03T07:14:00Z",
                "v": 1.2
            }
        ]
    }
]
```

### A15 — timeFormat=ms

Timestamps as epoch millis instead of ISO-8601.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=avg&timeFormat=ms'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:15:23.845619Z",
            "to": "2026-08-03T07:15:23.845619Z",
            "step": "PT1M",
            "aggregation": "avg"
        },
        "result": {
            "count": 3,
            "dataType": "number",
            "unit": "Cel"
        },
        "data": [
            {
                "t": 1785741060000,
                "v": 20.0
            },
            {
                "t": 1785741180000,
                "v": 21.0
            },
            {
                "t": 1785741240000,
                "v": 22.0
            }
        ]
    }
]
```



## B. Cross-Thing aggregation — happy paths

`GET /api/2/timeseries/things` — the new endpoint. Aggregates across every authorized Thing of ONE namespace and returns one series per (group, path).
### B1 — Ungrouped, one path

No `groupBy`: every authorized Thing folds into a single series per path. `authorization` reports how many Things contributed.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B2 — groupBy=thingId

One series per Thing. `ref-x` must be absent (READ_TS revoked).

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "thingId": "ts.demo:ref-a1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-a2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 2,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 25.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 31.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 32.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B3 — groupBy=tag:attributes/building

Groups on the ingest-time tag frozen on each point — not on the Thing's current attribute.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=tag:attributes/building'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "attributes/building": "A"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 23.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "attributes/building": "B"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 33.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 34.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B4 — groupBy=path

Results are always per path, so this dimension only affects the emitted group identity.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature,/features/env/properties/pressure&from=now-1h&to=now&step=1m&agg=avg&groupBy=path'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "path": "/features/env/properties/flowTemperature"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        },
        {
            "group": {
                "path": "/features/env/properties/pressure"
            },
            "path": "/features/env/properties/pressure",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "bar"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 1.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 2.6
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 2.866666666666667
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2,
            "/features/env/properties/pressure": 2
        }
    }
}
```

### B5 — Multi-dimension groupBy

Two dimensions compose into a composite group key.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=tag:attributes/building,thingId'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "attributes/building": "A",
                "thingId": "ts.demo:ref-a1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "attributes/building": "A",
                "thingId": "ts.demo:ref-a2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 2,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 25.0
                }
            ]
        },
        {
            "group": {
                "attributes/building": "B",
                "thingId": "ts.demo:ref-b1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 31.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 32.0
                }
            ]
        },
        {
            "group": {
                "attributes/building": "B",
                "thingId": "ts.demo:ref-b2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B6 — Multi-path cross-Thing

Two properties aggregated across the namespace in one request.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature,/features/env/properties/pressure&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        },
        {
            "path": "/features/env/properties/pressure",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "bar"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 1.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 2.6
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 2.866666666666667
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2,
            "/features/env/properties/pressure": 2
        }
    }
}
```

### B7 — filter=eq (RQL)

RQL predicate over ingest-time tags — same language as /search/things.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=eq(attributes/building,'A')'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 23.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 2,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B8 — filter=ne

Negated equality.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=ne(attributes/building,'A')'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 33.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 34.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 2,
        "excludedThings": 0,
        "partial": false,
        "withheldByPath": {}
    }
}
```

### B9 — filter=in

Set membership.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=in(attributes/building,'A','B')'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B10 — filter=like

Wildcard match.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=like(attributes/building,'A*')'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 23.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 2,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B11 — filter=exists

Presence of the tag.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=exists(attributes/building)'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B12 — filter=and(...)

Boolean composition.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=and(exists(attributes/building),ne(attributes/building,'B'))'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 23.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 2,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B13 — filter=or(...)

Boolean composition.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=or(eq(attributes/building,'A'),eq(attributes/building,'B'))'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B14 — filter + groupBy combined

Filter narrows the point set, groupBy splits what remains.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=eq(attributes/building,'B')&groupBy=thingId'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "thingId": "ts.demo:ref-b1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 31.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 32.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 2,
        "excludedThings": 0,
        "partial": false,
        "withheldByPath": {}
    }
}
```

### B15 — fill=linear cross-Thing

Gap interpolation applies to grouped series too.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId&fill=linear'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "thingId": "ts.demo:ref-a1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 4,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:12:00Z",
                    "v": 20.5,
                    "_gap": true
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-a2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:12:00Z",
                    "v": 22.5,
                    "_gap": true
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 25.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 4,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:12:00Z",
                    "v": 25.5,
                    "_gap": true
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 31.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 32.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 4,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:12:00Z",
                    "v": 27.5,
                    "_gap": true
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B16 — tz=Europe/Berlin

Zone-aligned bucket boundaries.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1h&agg=avg&tz=Europe/Berlin'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 1,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:00:00Z",
                    "v": 25.636363636363637
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B17 — timeFormat=ms

Epoch millis. Note the body is an object ({results,authorization}), not a bare array.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&timeFormat=ms'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": 1785741060000,
                    "v": 20.0
                },
                {
                    "t": 1785741180000,
                    "v": 28.0
                },
                {
                    "t": 1785741240000,
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B18 — maxGroups explicitly set (within ceiling)

Accepted when the group count fits.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId&maxGroups=50'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "thingId": "ts.demo:ref-a1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-a2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 2,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 25.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b1"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 31.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 32.0
                }
            ]
        },
        {
            "group": {
                "thingId": "ts.demo:ref-b2"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.min — cross-Thing agg=min

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=min'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.max — cross-Thing agg=max

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=max'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.sum — cross-Thing agg=sum

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=sum'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 80
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 112
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 90
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.count — cross-Thing agg=count

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=count'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 4
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 4
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 3
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.first — cross-Thing agg=first

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=first'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 21
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.last — cross-Thing agg=last

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=last'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 35
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 36
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### B19.stddev — cross-Thing agg=stddev

All eight per-bucket aggregations are available cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=stddev'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 0.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 6.2182527020592095
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 7.211102550927978
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```



## C. Validation and limits

Every rejection below is produced by `CrossThingTimeseriesQuery.of(...)` or the route's parameter parsing, so HTTP, WebSocket and Connectivity would reject identically.
### C1 — Missing 'namespaces'

Required — the endpoint is namespace-scoped by construction.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <namespaces> has an invalid value: <>.",
    "description": "Query parameter 'namespaces' is required."
}
```

### C2 — Missing 'paths'

Required.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <paths> has an invalid value: <>.",
    "description": "Query parameter 'paths' is required."
}
```

### C3 — Missing 'step'

Required cross-Thing: a raw read would stream every point of every Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <step> has an invalid value: <>.",
    "description": "Query parameter 'step' is required."
}
```

### C4 — Missing 'agg'

Required cross-Thing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <agg> has an invalid value: <>.",
    "description": "Query parameter 'agg' is required."
}
```

### C5 — Two namespaces

Storage is one collection per namespace; a list would need a multi-collection merge.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo,other&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <namespaces> has an invalid value: <ts.demo,other>.",
    "description": "Cross-Thing aggregation is scoped to a single namespace; pass exactly one value."
}
```

### C6.derivative — Window function 'derivative' rejected

Window functions need the full ordered series of ONE Thing; across Things that is not well defined.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=derivative'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "Cross-Thing aggregation supports the per-bucket aggregations (avg, min, max, sum, count, first, last, stddev); <derivative> is a window function and is only available on single-Thing queries.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C6.rate — Window function 'rate' rejected

Window functions need the full ordered series of ONE Thing; across Things that is not well defined.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=rate'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "Cross-Thing aggregation supports the per-bucket aggregations (avg, min, max, sum, count, first, last, stddev); <rate> is a window function and is only available on single-Thing queries.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C6.integral — Window function 'integral' rejected

Window functions need the full ordered series of ONE Thing; across Things that is not well defined.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=integral'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "Cross-Thing aggregation supports the per-bucket aggregations (avg, min, max, sum, count, first, last, stddev); <integral> is a window function and is only available on single-Thing queries.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C6.percentile — Window function 'percentile' rejected

Window functions need the full ordered series of ONE Thing; across Things that is not well defined.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=percentile'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "Cross-Thing aggregation supports the per-bucket aggregations (avg, min, max, sum, count, first, last, stddev); <percentile> is a window function and is only available on single-Thing queries.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C7 — step with sub-second precision

Buckets must be whole seconds.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=PT0.5S&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "The 'step' must be a whole number of seconds, but was <PT0.5S>. A fractional step cannot be expressed as a bucket width: the backend would truncate it to a zero-sized bin and the fill grid would never advance.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C8 — step=0

Must be a positive duration.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=0s&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "'step' must be a positive duration, was <PT0S>.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C9 — from equals to

'from' must be strictly before 'to'.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now&to=now&step=1m&agg=avg'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "'from' (2026-08-03T07:15:25.787728Z) must be strictly before 'to' (2026-08-03T07:15:25.787728Z).",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C10 — maxGroups=0

Must be positive.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId&maxGroups=0'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "'maxGroups' must be positive, was <0>.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C11 — maxGroups above the 10000 ceiling

Hard ceiling regardless of caller intent.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId&maxGroups=10001'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "'maxGroups' must not exceed 10000, was <10001>.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C12 — maxGroups exceeded by the data

Fails rather than silently truncating the group set.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId&maxGroups=1'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "The query matches more than 1 distinct groups. Narrow it with 'tagFilter', fewer 'groupBy' dimensions or a smaller namespace, or raise 'maxGroups' (ceiling 10000).",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C13 — Duplicate groupBy dimension

Rejected as a caller mistake.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=thingId,thingId'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "Duplicate groupBy dimension <thingId>.",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C14 — Unknown groupBy token

**Correction to the original expectation:** this is NOT rejected. `GroupBy.parse` treats any token that is not `thingId`/`path` as a *bare tag path* (the primary form; `tag:<key>` is the legacy spelling), so `nonsense` becomes a tag dimension. Since no point carries that tag, every point collapses into a single group with an empty-string value. Documented behaviour — but it means a mistyped reserved word (e.g. `thingid`) silently degrades to a meaningless grouping instead of erroring.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=nonsense'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "nonsense": ""
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 28.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 30.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### C15 — Malformed RQL filter

Parse errors surface with the parser's column pointer.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=notafunction(x)'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "timeseries:query.invalid",
    "message": "The 'filter' is not a valid RQL predicate: Invalid input 'a', expected '(' (line 1, column 4):\nnotafunction(x)\n   ^",
    "description": "Check the timeseries query parameters (from, to, step, agg, fill, tz, percentile, limit).",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### C16 — Unknown aggregation name

Rejected at parameter parsing.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=bogus'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <agg> has an invalid value: <bogus>.",
    "description": "Expected one of: avg, min, max, sum, count, first, last, derivative, rate, integral, stddev, percentile."
}
```

### C17 — Unknown timezone

Validated against the JDK zone database.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&tz=Mars/Olympus'
```

**HTTP 400**

```json
{
    "status": 400,
    "error": "json.invalid",
    "message": "Query parameter <tz> has an invalid value: <Mars/Olympus>.",
    "description": "Expected an IANA time-zone ID, e.g. \"Europe/Berlin\" or \"UTC\"."
}
```



## D. Authorization

Three independent layers, all evaluated against CURRENT policy state on every request. Nothing about authorization is stored alongside the data points.
### D1 — No credentials

Pre-authentication is the only provider configured locally.

```bash
curl -s 'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 401**

```json
{
    "status": 401,
    "error": "gateway:authentication.failed",
    "message": "No applicable authentication provider was found!",
    "description": "Check if your credentials were correct."
}
```

### D2 — Namespace with no root policy

Layer 1. Cross-Thing requires a namespace-wide READ_TS grant, expressible only through a namespace root policy (`ditto.namespace-policies`). No root policy = denied outright.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=no.such.namespace&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 403**

```json
{
    "status": 403,
    "error": "timeseries:aggregation.forbidden",
    "message": "Not authorized to aggregate timeseries across namespace <no.such.namespace>: no namespace-wide 'READ_TS' grant covers every requested path.",
    "description": "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally expressed through a namespace root policy. Per-Thing grants scattered across individual policies are not yet supported for cross-Thing queries; query those Things individually instead.",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### D3 — Thing revoking READ_TS is excluded

Layer 2. `ref-x` (building A, value 99) revokes READ_TS. Building A's average must be unaffected and `partial` must be true.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=tag:attributes/building'
```

**HTTP 200**

```json
{
    "results": [
        {
            "group": {
                "attributes/building": "A"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 23.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 22.0
                }
            ]
        },
        {
            "group": {
                "attributes/building": "B"
            },
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 33.0
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 34.0
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 4,
        "excludedThings": 2,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 2
        }
    }
}
```

### D4 — groupBy a field the caller cannot READ

Layer 0. `nginx:limited` holds READ_TS on the data but has READ revoked on attributes/building. Grouping by it would reveal its distinct values.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:limited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&groupBy=tag:attributes/building'
```

**HTTP 403**

```json
{
    "status": 403,
    "error": "timeseries:aggregation.forbidden",
    "message": "Not authorized to aggregate timeseries across namespace <ts.demo>: no namespace-wide 'READ' grant covers every requested path.",
    "description": "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally expressed through a namespace root policy. Per-Thing grants scattered across individual policies are not yet supported for cross-Thing queries; query those Things individually instead.",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### D5 — filter on a field the caller cannot READ

Layer 0. Filtering reveals which points carry which value — same disclosure, same gate.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:limited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg&filter=eq(attributes/building,'A')'
```

**HTTP 403**

```json
{
    "status": 403,
    "error": "timeseries:aggregation.forbidden",
    "message": "Not authorized to aggregate timeseries across namespace <ts.demo>: no namespace-wide 'READ' grant covers every requested path.",
    "description": "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally expressed through a namespace root policy. Per-Thing grants scattered across individual policies are not yet supported for cross-Thing queries; query those Things individually instead.",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### D6 — Same subject, no field reference

Layer 0 only bites when a field is actually referenced.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:limited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 42.2
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 47.25
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 5,
        "excludedThings": 1,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 1
        }
    }
}
```

### D7 — Path-granular READ_TS — permitted path

`nginx:pathlimited` holds READ_TS on flowTemperature only.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:pathlimited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 200**

```json
{
    "results": [
        {
            "path": "/features/env/properties/flowTemperature",
            "result": {
                "count": 3,
                "dataType": "number",
                "unit": "Cel"
            },
            "data": [
                {
                    "t": "2026-08-03T07:11:00Z",
                    "v": 20.0
                },
                {
                    "t": "2026-08-03T07:13:00Z",
                    "v": 42.2
                },
                {
                    "t": "2026-08-03T07:14:00Z",
                    "v": 47.25
                }
            ]
        }
    ],
    "authorization": {
        "contributingThings": 5,
        "excludedThings": 1,
        "partial": true,
        "withheldByPath": {
            "/features/env/properties/flowTemperature": 1
        }
    }
}
```

### D8 — Path-granular READ_TS — forbidden path

Same subject asking for pressure, where READ_TS is revoked.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:pathlimited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/pressure&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 403**

```json
{
    "status": 403,
    "error": "timeseries:aggregation.forbidden",
    "message": "Not authorized to aggregate timeseries across namespace <ts.demo>: no namespace-wide 'READ_TS' grant covers every requested path.",
    "description": "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally expressed through a namespace root policy. Per-Thing grants scattered across individual policies are not yet supported for cross-Thing queries; query those Things individually instead.",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### D9 — Path-granular READ_TS — mixed request

**Correction to the original expectation:** this is denied outright, not partially served. Layer 1 requires the namespace root policy to grant READ_TS on *every* requested path; `pathlimited` has it revoked on `pressure`, so the whole request fails before any Thing is examined. `withheldByPath` reports per-Thing exclusions *within* an authorized path — it is not a mechanism for partially authorizing the path set.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:pathlimited' \
  'http://localhost:8080/api/2/timeseries/things?namespaces=ts.demo&paths=/features/env/properties/flowTemperature,/features/env/properties/pressure&from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 403**

```json
{
    "status": 403,
    "error": "timeseries:aggregation.forbidden",
    "message": "Not authorized to aggregate timeseries across namespace <ts.demo>: no namespace-wide 'READ_TS' grant covers every requested path.",
    "description": "A cross-Thing aggregation requires a namespace-wide READ_TS grant, which is normally expressed through a namespace root policy. Per-Thing grants scattered across individual policies are not yet supported for cross-Thing queries; query those Things individually instead.",
    "href": "https://github.com/eclipse-ditto/ditto/issues/2291"
}
```

### D10 — Single-Thing read of a revoked Thing

For contrast: the single-Thing endpoint denies outright rather than excluding-and-reporting.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-x/features/env/properties/flowTemperature?from=now-1h&to=now&step=1m&agg=avg'
```

**HTTP 404**

```json
{
    "status": 404,
    "error": "things:thing.notfound",
    "message": "The Thing with ID 'ts.demo:ref-x' could not be found or requester had insufficient permissions to access it.",
    "description": "Check if the ID of your requested Thing was correct and you have sufficient permissions."
}
```



## E. Ingest semantics

Ingest is **WoT opt-in**: a property is recorded only if its schema carries `ditto:timeseries` with `ingest: ALL`. It is forward-only — there is no backfill of values written before the annotation existed.
### E1 — Unannotated property produces no points

`serial` is declared in the TM but carries no `ditto:timeseries` annotation. It was written with `PUT .../properties/serial` (HTTP 204) yet produced zero data points, while the annotated sibling has three.

```bash
mongosh "mongodb://localhost:27017/ditto_ts" --quiet --eval 'print("serial points          : " + db.ts_ts_demo.countDocuments({"meta.path":/serial/}));
print("flowTemperature (ref-a1): " + db.ts_ts_demo.countDocuments({"meta.thingId":"ts.demo:ref-a1","meta.path":/flowTemperature/}));'
```

```
serial points          : 0
flowTemperature (ref-a1): 3
```

### E2 — Stored point shape

`meta` carries the identity and the resolved tags; `timestamp` is the Mongo timeField; `revision` is the Thing revision the value came from.

```bash
mongosh "mongodb://localhost:27017/ditto_ts" --quiet --eval 'printjson(db.ts_ts_demo.findOne({"meta.thingId":"ts.demo:ref-a1","meta.path":/flowTemperature/},{_id:0}));'
```

```
{
  timestamp: ISODate('2026-08-03T07:11:50.703Z'),
  meta: {
    path: '/features/env/properties/flowTemperature',
    tags: {
      'attributes/building': 'A'
    },
    thingId: 'ts.demo:ref-a1',
    unit: 'Cel'
  },
  value: 20,
  revision: Long('1')
}
```

### E3 — Tags and unit read back in result meta

`unit` comes from the WoT schema; `tags` are the ingest-time values frozen on the point. `hasMore`/`nextCursor` drive keyset pagination.

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=1'
```

**HTTP 200**

```json
[
    {
        "thingId": "ts.demo:ref-a1",
        "path": "/features/env/properties/flowTemperature",
        "query": {
            "thingId": "ts.demo:ref-a1",
            "paths": [
                "/features/env/properties/flowTemperature"
            ],
            "from": "2026-08-03T06:25:29.456029Z",
            "to": "2026-08-03T07:25:29.456029Z",
            "limit": 1
        },
        "result": {
            "count": 1,
            "dataType": "number",
            "unit": "Cel",
            "tags": {
                "attributes/building": "A"
            },
            "hasMore": true,
            "nextCursor": "eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMTo1MC43MDNaIiwiciI6MX0"
        },
        "data": [
            {
                "t": "2026-08-03T07:11:50.703Z",
                "v": 20
            }
        ]
    }
]
```

### E4 — Ingest is NOT gated by READ_TS

`ref-x` revokes `READ_TS`, yet its points are stored — the permission gates **reads**, not writes. This is what makes revocation retroactive: the data exists, it simply stops being readable.

```bash
mongosh "mongodb://localhost:27017/ditto_ts" --quiet --eval 'print("ref-x stored points: " + db.ts_ts_demo.countDocuments({"meta.thingId":"ts.demo:ref-x"}));'
```

```
ref-x stored points: 6
```



## F. Storage, retention and operations

Each namespace maps to one native MongoDB **Time Series collection** named `<collection-prefix><namespace-with-dots-as-underscores>`.
### F1 — Collection layout and retention

`timeField`/`metaField`/`granularity` come from the adapter config; `expireAfterSeconds` is the retention (default 90d).

```bash
mongosh "mongodb://localhost:27017/ditto_ts" --quiet --eval 'db.getCollectionInfos({name:/^ts_/}).forEach(c=>{print(c.name);print("   type: "+c.type);
if(c.options.timeseries) print("   timeseries: "+JSON.stringify(c.options.timeseries));
print("   expireAfterSeconds: "+(c.options.expireAfterSeconds===undefined?"(none)":c.options.expireAfterSeconds+"  ("+(c.options.expireAfterSeconds/86400)+"d)"));});'
```

```
ts_org_eclipse_ditto
   type: collection
   expireAfterSeconds: (none)
ts_org_eclipse_ditto_ts
   type: timeseries
   timeseries: {"timeField":"timestamp","metaField":"meta","granularity":"seconds","bucketMaxSpanSeconds":3600}
   expireAfterSeconds: 7776000  (90d)
ts_ts_demo
   type: timeseries
   timeseries: {"timeField":"timestamp","metaField":"meta","granularity":"seconds","bucketMaxSpanSeconds":3600}
   expireAfterSeconds: 7776000  (90d)
ts_ts_ref
   type: timeseries
   timeseries: {"timeField":"timestamp","metaField":"meta","granularity":"seconds","bucketMaxSpanSeconds":3600}
   expireAfterSeconds: 7776000  (90d)
ts_ts_retention_demo
   type: timeseries
   timeseries: {"timeField":"timestamp","metaField":"meta","granularity":"seconds","bucketMaxSpanSeconds":3600}
   expireAfterSeconds: 604800  (7d)
```

### E5 — Keyset pagination round-trip

Three pages of one point each. `nextCursor` is base64 of `{"t":<timestamp>,"r":<revision>}`; `hasMore` goes false on the last page. Keyset rather than offset because timeseries data is append-heavy — an offset would skip or repeat rows as new points land mid-pagination.

**Page 1**

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=1'
```

```json
{
  "data": [
    {
      "t": "2026-08-03T07:11:50.703Z",
      "v": 20
    }
  ],
  "hasMore": true,
  "nextCursor": "eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMTo1MC43MDNaIiwiciI6MX0"
}
```

**Page 2**

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=1&cursor=eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMTo1MC43MDNaIiwiciI6MX0'
```

```json
{
  "data": [
    {
      "t": "2026-08-03T07:13:26.810Z",
      "v": 21
    }
  ],
  "hasMore": true,
  "nextCursor": "eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMzoyNi44MTBaIiwiciI6Mn0"
}
```

**Page 3**

```bash
curl -s -H 'x-ditto-pre-authenticated: nginx:ditto' \
  'http://localhost:8080/api/2/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=1&cursor=eyJ0IjoiMjAyNi0wOC0wM1QwNzoxMzoyNi44MTBaIiwiciI6Mn0'
```

```json
{
  "data": [
    {
      "t": "2026-08-03T07:14:37.074Z",
      "v": 22
    }
  ],
  "hasMore": false,
  "nextCursor": null
}
```



## G. Cross-protocol support (WebSocket)

Ditto commands are expected to work identically over HTTP, WebSocket and Connectivity. These two cases establish that the single-Thing command does, and the cross-Thing command does not.

Client: `python3 websockets` against `ws://localhost:8080/ws/2` with the pre-authentication header.

### G1 — single-Thing RetrieveTimeseries over WebSocket
The single-Thing command IS protocol-adapter wired (`TimeseriesQueryCommandAdapter`, `TimeseriesQuerySignalMapper`, both mapping-strategies, `AdapterResolverBySignal`). This establishes that cross-protocol parity is achievable and already in place for this command.

**Sent:**
```json
{
  "topic": "ts.demo/ref-a1/things/twin/timeseries/retrieve",
  "headers": {
    "correlation-id": "ws-g1"
  },
  "path": "/features/env/properties/flowTemperature",
  "value": {
    "thingId": "ts.demo:ref-a1",
    "paths": [
      "/features/env/properties/flowTemperature"
    ],
    "from": "2026-08-03T06:00:00Z",
    "to": "2026-08-03T09:00:00Z",
    "step": "PT1M",
    "aggregation": "avg"
  }
}
```

**Received:**
```json
{
  "topic": "ts.demo/ref-a1/things/twin/timeseries/retrieve",
  "headers": {
    "tracestate": "",
    "correlation-id": "ws-g1",
    "response-required": false,
    "context-tags": "upstream.name=ditto-gateway;",
    "traceparent": "00-aac27151a85596156d87df6dd5cabdc1-5837a6a9232d3165-01",
    "content-type": "application/json"
  },
  "path": "/",
  "value": [
    {
      "thingId": "ts.demo:ref-a1",
      "path": "/features/env/properties/flowTemperature",
      "query": {
        "thingId": "ts.demo:ref-a1",
        "paths": [
          "/features/env/properties/flowTemperature"
        ],
        "from": "2026-08-03T06:00:00Z",
        "to": "2026-08-03T09:00:00Z",
        "step": "PT1M",
        "aggregation": "avg"
      },
      "result": {
        "count": 3,
        "dataType": "number",
        "unit": "Cel"
      },
      "data": [
        {
          "t": "2026-08-03T07:11:00Z",
          "v": 20.0
        },
        {
          "t": "2026-08-03T07:13:00Z",
          "v": 21.0
        },
        {
          "t": "2026-08-03T07:14:00Z",
          "v": 22.0
        }
      ]
    }
  ],
  "status": 200
}
```

### G2 — cross-Thing aggregation over WebSocket
There is no `aggregate` action in `TimeseriesTopicPathBuilder` and no adapter for `RetrieveAggregatedTimeseries`, so no topic path can express this command. This is the live demonstration of review finding 1.

**Sent:**
```json
{
  "topic": "ts.demo/_/things/twin/timeseries/aggregate",
  "headers": {
    "correlation-id": "ws-g2"
  },
  "path": "/",
  "value": {
    "namespace": "ts.demo",
    "paths": [
      "/features/env/properties/flowTemperature"
    ],
    "from": "2026-08-03T06:00:00Z",
    "to": "2026-08-03T09:00:00Z",
    "step": "PT1M",
    "aggregation": "avg"
  }
}
```

**Received:**
```json
{
  "topic": "unknown/unknown/things/twin/errors",
  "headers": {
    "correlation-id": "ws-g2",
    "tracestate": "",
    "context-tags": "upstream.name=ditto-gateway;",
    "traceparent": "00-f75df8e9162e783b00ce5eb42ea29e4b-d0a5aab3ea349363-01",
    "response-required": false,
    "content-type": "application/json"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "protocoladapter:unknown.topicpath",
    "message": "The topic path 'ts.demo/_/things/twin/timeseries/aggregate' is not supported.",
    "description": "Action name <aggregate> is unknown."
  },
  "status": 400
}
```


---

## 15. Appendix — reproducing this run

Serve the two Thing Models on `:8099`, apply the dev-config prerequisite, then run `setup.sh`, `ingest.sh`, `run.sh <out.md>`, `run_ef.sh <out.md>` and `ws_test.py`.

### 15.1 `sensor2.tm.jsonld` (root TM)

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    { "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#" }
  ],
  "@type": "tm:ThingModel",
  "title": "TS Demo Sensor",
  "version": { "model": "2.0.0" },
  "links": [
    {
      "rel": "tm:submodel",
      "href": "http://localhost:8099/env2.tm.jsonld",
      "type": "application/tm+json",
      "instanceName": "env"
    }
  ],
  "properties": {
    "building": { "type": "string", "title": "Building" }
  }
}
```

### 15.2 `env2.tm.jsonld` (submodel — carries the `ditto:timeseries` annotations)

```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    { "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#" }
  ],
  "@type": "tm:ThingModel",
  "title": "Environment",
  "version": { "model": "2.0.0" },
  "properties": {
    "flowTemperature": {
      "type": "number", "unit": "Cel",
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": { "attributes/building": "{{ thing-json:attributes/building }}" }
      }
    },
    "pressure": {
      "type": "number", "unit": "bar",
      "ditto:timeseries": {
        "ingest": "ALL",
        "tags": { "attributes/building": "{{ thing-json:attributes/building }}" }
      }
    },
    "serial": { "type": "string" }
  }
}
```

```bash
cd <dir with the two .tm.jsonld files> && python3 -m http.server 8099 --bind 127.0.0.1
```

### 15.3 Dev-config prerequisite

Cross-Thing denies every request unless the namespace has a root policy. Added to `timeseries/service/src/main/resources/timeseries-dev.conf` (requires a TimeseriesService restart):

```hocon
namespace-policies {
  "ts.demo" = ["ts.demo:root"]
}
```

Pre-authentication is already enabled in `gateway-dev.conf`, so all requests use `-H 'x-ditto-pre-authenticated: <subject>'`.

### 15.4 `setup.sh` — policies and Things

```bash
#!/bin/bash
# Fixtures for the cross-Thing verification run. Namespace ts.demo must have a
# namespace root policy configured (see §9.3) or every cross-Thing query 403s.
G="http://localhost:8080/api/2"
H=(-H "x-ditto-pre-authenticated: nginx:ditto" -H "Content-Type: application/json")
NS="ts.demo"
put(){ printf '%-42s -> %s\n' "$1" "$(curl -s -m 15 -o /dev/null -w '%{http_code}' -X PUT "${H[@]}" "$2" -d "$3")"; }

# Namespace root policy. Three subjects with deliberately different reach:
#   owner       - full access (the happy path)
#   limited     - READ_TS on data, but READ revoked on attributes/building (layer-0 tests)
#   pathlimited - READ_TS on flowTemperature only, revoked on pressure (path-granular tests)
put "policy $NS:root" "$G/policies/$NS:root" '{
  "entries": {
    "owner": { "subjects": { "nginx:ditto": { "type": "dev" } },
      "resources": { "thing:/": { "grant": ["READ","WRITE","READ_TS"], "revoke": [] },
        "policy:/": { "grant": ["READ","WRITE"], "revoke": [] },
        "message:/": { "grant": ["READ","WRITE"], "revoke": [] } }, "importable": "implicit" },
    "limited": { "subjects": { "nginx:limited": { "type": "dev" } },
      "resources": { "thing:/": { "grant": ["READ","READ_TS"], "revoke": [] },
        "thing:/attributes/building": { "grant": [], "revoke": ["READ"] },
        "policy:/": { "grant": ["READ"], "revoke": [] } }, "importable": "implicit" },
    "pathlimited": { "subjects": { "nginx:pathlimited": { "type": "dev" } },
      "resources": { "thing:/": { "grant": ["READ"], "revoke": [] },
        "thing:/features/env/properties/flowTemperature": { "grant": ["READ_TS"], "revoke": [] },
        "thing:/features/env/properties/pressure": { "grant": [], "revoke": ["READ_TS"] },
        "policy:/": { "grant": ["READ"], "revoke": [] } }, "importable": "implicit" }
  } }'

# Explicitly revokes READ_TS. Namespace-root entries merge ADDITIVELY into every policy of the
# namespace, so a plain "don't grant" would still inherit the root grant - the revoke must be explicit.
put "policy $NS:norts" "$G/policies/$NS:norts" '{
  "entries": { "owner": {
    "subjects": { "nginx:ditto": { "type": "dev" }, "nginx:limited": { "type": "dev" } },
    "resources": { "thing:/": { "grant": ["READ","WRITE"], "revoke": ["READ_TS"] },
      "policy:/": { "grant": ["READ","WRITE"], "revoke": [] } } } } }'

# Five Things. Only attributes declared in the TM may be set (WoT validation is on).
# NOTE: an existing Thing cannot be repointed at a different definition - create fresh IDs.
# NOTE: creating a Thing with an annotated property already ingests one data point (bucket B0).
mk(){ put "thing $NS:$1 (bld=$2 pol=$3)" "$G/things/$NS:$1" "{
    \"policyId\": \"$NS:$3\",
    \"definition\": \"http://localhost:8099/sensor2.tm.jsonld\",
    \"attributes\": { \"building\": \"$2\" },
    \"features\": { \"env\": { \"properties\": { \"flowTemperature\": 20.0, \"pressure\": 1.0 } } } }"; }
mk ref-a1 A root
mk ref-a2 A root
mk ref-b1 B root
mk ref-b2 B root
mk ref-x  A norts
```

### 15.5 `ingest.sh` — three one-minute buckets with a deliberate gap

```bash
#!/bin/bash
G="http://localhost:8080/api/2"
H=(-H "x-ditto-pre-authenticated: nginx:ditto" -H "Content-Type: application/json")
w(){ curl -s -m 10 -o /dev/null -X PUT "${H[@]}" \
     "$G/things/ts.demo:$1/features/env/properties/$2" -d "$3"; }
echo "B0 = the creation writes (flowTemperature 20.0, pressure 1.0 on all five) @ $(date -u +%H:%M:%S)"
sleep 70
echo "B1 @ $(date -u +%H:%M:%S)"
w ref-a1 flowTemperature 21; w ref-a1 pressure 1.1
w ref-a2 flowTemperature 25; w ref-a2 pressure 2.1
w ref-b1 flowTemperature 31; w ref-b1 pressure 3.1
w ref-b2 flowTemperature 35; w ref-b2 pressure 4.1
w ref-x  flowTemperature 99; w ref-x  pressure 9.1
sleep 70
echo "B2 @ $(date -u +%H:%M:%S)  (ref-a2 deliberately skipped -> gap for fill)"
w ref-a1 flowTemperature 22; w ref-a1 pressure 1.2
w ref-b1 flowTemperature 32; w ref-b1 pressure 3.2
w ref-b2 flowTemperature 36; w ref-b2 pressure 4.2
w ref-x  flowTemperature 99; w ref-x  pressure 9.2
echo "DONE @ $(date -u +%H:%M:%S)"
```

### 15.6 `run.sh` — scenario runner for sections A–D

```bash
#!/bin/bash
# Emits a markdown transcript of every scenario: exact curl + verbatim response.
OUT="$1"; : > "$OUT"
BASE="http://localhost:8080/api/2"
SUB_DEFAULT="nginx:ditto"

sec() { printf '\n\n## %s\n\n%s\n' "$1" "$2" >> "$OUT"; }
# case <id> <title> <why> <subject|-> <url> [curl-extra...]
case_() {
  local id="$1" title="$2" why="$3" subj="$4" url="$5"; shift 5
  local hdr=() shown=""
  if [ "$subj" != "-" ]; then hdr=(-H "x-ditto-pre-authenticated: $subj"); shown="-H 'x-ditto-pre-authenticated: $subj' \\
  "; fi
  local body code
  body=$(curl -s -m 30 -w $'\n%{http_code}' "${hdr[@]}" "$@" "$url")
  code="${body##*$'\n'}"; body="${body%$'\n'*}"
  {
    printf '### %s — %s\n\n' "$id" "$title"
    printf '%s\n\n' "$why"
    printf '```bash\ncurl -s %s'"'"'%s'"'"'\n```\n\n' "$shown" "$url"
    printf '**HTTP %s**\n\n```json\n' "$code"
    if [ -n "$body" ]; then echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"; else echo "(empty body)"; fi
    printf '```\n\n'
  } >> "$OUT"
  printf '%-6s %-52s %s\n' "$id" "$title" "$code"
}

TS="$BASE/timeseries/things"
P="/features/env/properties/flowTemperature"
PR="/features/env/properties/pressure"
ONE="$TS/ts.demo:ref-a1$P"
W="from=now-1h&to=now"
XT="namespaces=ts.demo&paths=$P&$W&step=1m&agg=avg"

sec "A. Single-Thing read API" \
"The per-Thing read endpoints, included so cross-Thing behaviour can be compared against them."

case_ A1 "Raw read (no aggregation)" \
"Without \`step\`/\`agg\` a single-Thing read streams the stored points as-is. The cross-Thing endpoint deliberately forbids this — see C5." \
"$SUB_DEFAULT" "$ONE?$W"
case_ A2 "Bucketed average" "The baseline aggregation: one value per \`step\` bucket." \
"$SUB_DEFAULT" "$ONE?$W&step=1m&agg=avg"
for a in min max sum count first last stddev; do
  case_ "A3.$a" "agg=$a" "Per-bucket aggregation \`$a\`, pushed down to MongoDB's \$group." \
    "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=$a"
done
case_ A4 "agg=percentile" "Window function — single-Thing only; rejected cross-Thing (C5)." \
  "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=percentile&percentile=95"
case_ A5 "agg=derivative" "Window function: rate of change between buckets. Single-Thing only." \
  "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=derivative"
case_ A6 "agg=rate" "Window function. Single-Thing only." "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=rate"
case_ A7 "agg=integral" "Window function. Single-Thing only." "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=integral"
case_ A8 "fill=linear over a gap" \
"\`ref-a2\` deliberately has no B1 write, so bucket 2 is empty. \`fill=linear\` interpolates it." \
  "$SUB_DEFAULT" "$TS/ts.demo:ref-a2$P?$W&step=1m&agg=avg&fill=linear"
case_ A9 "fill omitted (same gap)" "Same query without \`fill\`: the empty bucket is simply absent." \
  "$SUB_DEFAULT" "$TS/ts.demo:ref-a2$P?$W&step=1m&agg=avg"
case_ A10 "tz=Europe/Berlin" "Bucket boundaries align to the given zone (DST-aware stepping)." \
  "$SUB_DEFAULT" "$ONE?$W&step=1h&agg=avg&tz=Europe/Berlin"
case_ A11 "limit (keyset pagination page 1)" "Raw read bounded by \`limit\`; response carries a cursor." \
  "$SUB_DEFAULT" "$ONE?$W&limit=2"
case_ A12 "order=desc" "Sort order on the raw read." "$SUB_DEFAULT" "$ONE?$W&limit=3&order=desc"
case_ A13 "tagFilter" "Single-Thing tag predicate. NOTE: the cross-Thing endpoint uses \`filter\` (RQL) instead." \
  "$SUB_DEFAULT" "$ONE?$W&tagFilter=attributes/building:A"
case_ A14 "Multi-property read (paths=)" "Two properties of one Thing in a single request." \
  "$SUB_DEFAULT" "$TS/ts.demo:ref-a1?paths=$P,$PR&$W&step=1m&agg=avg"
case_ A15 "timeFormat=ms" "Timestamps as epoch millis instead of ISO-8601." \
  "$SUB_DEFAULT" "$ONE?$W&step=1m&agg=avg&timeFormat=ms"

sec "B. Cross-Thing aggregation — happy paths" \
"\`GET /api/2/timeseries/things\` — the new endpoint. Aggregates across every authorized Thing of ONE namespace and returns one series per (group, path)."

case_ B1 "Ungrouped, one path" \
"No \`groupBy\`: every authorized Thing folds into a single series per path. \`authorization\` reports how many Things contributed." \
  "$SUB_DEFAULT" "$TS?$XT"
case_ B2 "groupBy=thingId" "One series per Thing. \`ref-x\` must be absent (READ_TS revoked)." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId"
case_ B3 "groupBy=tag:attributes/building" \
"Groups on the ingest-time tag frozen on each point — not on the Thing's current attribute." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=tag:attributes/building"
case_ B4 "groupBy=path" "Results are always per path, so this dimension only affects the emitted group identity." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P,$PR&$W&step=1m&agg=avg&groupBy=path"
case_ B5 "Multi-dimension groupBy" "Two dimensions compose into a composite group key." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=tag:attributes/building,thingId"
case_ B6 "Multi-path cross-Thing" "Two properties aggregated across the namespace in one request." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P,$PR&$W&step=1m&agg=avg"
case_ B7 "filter=eq (RQL)" "RQL predicate over ingest-time tags — same language as /search/things." \
  "$SUB_DEFAULT" "$TS?$XT&filter=eq(attributes/building,'A')"
case_ B8 "filter=ne" "Negated equality." "$SUB_DEFAULT" "$TS?$XT&filter=ne(attributes/building,'A')"
case_ B9 "filter=in" "Set membership." "$SUB_DEFAULT" "$TS?$XT&filter=in(attributes/building,'A','B')"
case_ B10 "filter=like" "Wildcard match." "$SUB_DEFAULT" "$TS?$XT&filter=like(attributes/building,'A*')"
case_ B11 "filter=exists" "Presence of the tag." "$SUB_DEFAULT" "$TS?$XT&filter=exists(attributes/building)"
case_ B12 "filter=and(...)" "Boolean composition." \
  "$SUB_DEFAULT" "$TS?$XT&filter=and(exists(attributes/building),ne(attributes/building,'B'))"
case_ B13 "filter=or(...)" "Boolean composition." \
  "$SUB_DEFAULT" "$TS?$XT&filter=or(eq(attributes/building,'A'),eq(attributes/building,'B'))"
case_ B14 "filter + groupBy combined" "Filter narrows the point set, groupBy splits what remains." \
  "$SUB_DEFAULT" "$TS?$XT&filter=eq(attributes/building,'B')&groupBy=thingId"
case_ B15 "fill=linear cross-Thing" "Gap interpolation applies to grouped series too." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId&fill=linear"
case_ B16 "tz=Europe/Berlin" "Zone-aligned bucket boundaries." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=1h&agg=avg&tz=Europe/Berlin"
case_ B17 "timeFormat=ms" "Epoch millis. Note the body is an object ({results,authorization}), not a bare array." \
  "$SUB_DEFAULT" "$TS?$XT&timeFormat=ms"
case_ B18 "maxGroups explicitly set (within ceiling)" "Accepted when the group count fits." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId&maxGroups=50"
for a in min max sum count first last stddev; do
  case_ "B19.$a" "cross-Thing agg=$a" "All eight per-bucket aggregations are available cross-Thing." \
    "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=1m&agg=$a"
done

sec "C. Validation and limits" \
"Every rejection below is produced by \`CrossThingTimeseriesQuery.of(...)\` or the route's parameter parsing, so HTTP, WebSocket and Connectivity would reject identically."

case_ C1 "Missing 'namespaces'" "Required — the endpoint is namespace-scoped by construction." \
  "$SUB_DEFAULT" "$TS?paths=$P&$W&step=1m&agg=avg"
case_ C2 "Missing 'paths'" "Required." "$SUB_DEFAULT" "$TS?namespaces=ts.demo&$W&step=1m&agg=avg"
case_ C3 "Missing 'step'" "Required cross-Thing: a raw read would stream every point of every Thing." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&agg=avg"
case_ C4 "Missing 'agg'" "Required cross-Thing." "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=1m"
case_ C5 "Two namespaces" "Storage is one collection per namespace; a list would need a multi-collection merge." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo,other&paths=$P&$W&step=1m&agg=avg"
for a in derivative rate integral percentile; do
  case_ "C6.$a" "Window function '$a' rejected" \
"Window functions need the full ordered series of ONE Thing; across Things that is not well defined." \
    "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=1m&agg=$a"
done
case_ C7 "step with sub-second precision" "Buckets must be whole seconds." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=PT0.5S&agg=avg"
case_ C8 "step=0" "Must be a positive duration." "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=0s&agg=avg"
case_ C9 "from equals to" "'from' must be strictly before 'to'." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&from=now&to=now&step=1m&agg=avg"
case_ C10 "maxGroups=0" "Must be positive." "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId&maxGroups=0"
case_ C11 "maxGroups above the 10000 ceiling" "Hard ceiling regardless of caller intent." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId&maxGroups=10001"
case_ C12 "maxGroups exceeded by the data" "Fails rather than silently truncating the group set." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId&maxGroups=1"
case_ C13 "Duplicate groupBy dimension" "Rejected as a caller mistake." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=thingId,thingId"
case_ C14 "Unknown groupBy token" "Only thingId, path and tag:<key> are valid." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=nonsense"
case_ C15 "Malformed RQL filter" "Parse errors surface with the parser's column pointer." \
  "$SUB_DEFAULT" "$TS?$XT&filter=notafunction(x)"
case_ C16 "Unknown aggregation name" "Rejected at parameter parsing." \
  "$SUB_DEFAULT" "$TS?namespaces=ts.demo&paths=$P&$W&step=1m&agg=bogus"
case_ C17 "Unknown timezone" "Validated against the JDK zone database." \
  "$SUB_DEFAULT" "$TS?$XT&tz=Mars/Olympus"

sec "D. Authorization" \
"Three independent layers, all evaluated against CURRENT policy state on every request. Nothing about authorization is stored alongside the data points."

case_ D1 "No credentials" "Pre-authentication is the only provider configured locally." "-" "$TS?$XT"
case_ D2 "Namespace with no root policy" \
"Layer 1. Cross-Thing requires a namespace-wide READ_TS grant, expressible only through a namespace root policy (\`ditto.namespace-policies\`). No root policy = denied outright." \
  "$SUB_DEFAULT" "$TS?namespaces=no.such.namespace&paths=$P&$W&step=1m&agg=avg"
case_ D3 "Thing revoking READ_TS is excluded" \
"Layer 2. \`ref-x\` (building A, value 99) revokes READ_TS. Building A's average must be unaffected and \`partial\` must be true." \
  "$SUB_DEFAULT" "$TS?$XT&groupBy=tag:attributes/building"
case_ D4 "groupBy a field the caller cannot READ" \
"Layer 0. \`nginx:limited\` holds READ_TS on the data but has READ revoked on attributes/building. Grouping by it would reveal its distinct values." \
  "nginx:limited" "$TS?$XT&groupBy=tag:attributes/building"
case_ D5 "filter on a field the caller cannot READ" \
"Layer 0. Filtering reveals which points carry which value — same disclosure, same gate." \
  "nginx:limited" "$TS?$XT&filter=eq(attributes/building,'A')"
case_ D6 "Same subject, no field reference" "Layer 0 only bites when a field is actually referenced." \
  "nginx:limited" "$TS?$XT"
case_ D7 "Path-granular READ_TS — permitted path" \
"\`nginx:pathlimited\` holds READ_TS on flowTemperature only." "nginx:pathlimited" "$TS?$XT"
case_ D8 "Path-granular READ_TS — forbidden path" \
"Same subject asking for pressure, where READ_TS is revoked." \
  "nginx:pathlimited" "$TS?namespaces=ts.demo&paths=$PR&$W&step=1m&agg=avg"
case_ D9 "Path-granular READ_TS — mixed request" \
"Both paths at once: the permitted one returns, the forbidden one is reported in withheldByPath." \
  "nginx:pathlimited" "$TS?namespaces=ts.demo&paths=$P,$PR&$W&step=1m&agg=avg"
case_ D10 "Single-Thing read of a revoked Thing" \
"For contrast: the single-Thing endpoint denies outright rather than excluding-and-reporting." \
  "$SUB_DEFAULT" "$TS/ts.demo:ref-x$P?$W&step=1m&agg=avg"
```

### 15.7 `run_ef.sh` — ingest and storage checks (sections E–F)

```bash
#!/bin/bash
OUT="$1"; : > "$OUT"
G="http://localhost:8080/api/2"; A=(-H "x-ditto-pre-authenticated: nginx:ditto")
M="mongodb://localhost:27017/ditto_ts"
hdr(){ printf '\n\n## %s\n\n%s\n' "$1" "$2" >> "$OUT"; }
curlcase(){ local id="$1" t="$2" why="$3" url="$4"
  local b c; b=$(curl -s -m 30 -w $'\n%{http_code}' "${A[@]}" "$url"); c="${b##*$'\n'}"; b="${b%$'\n'*}"
  { printf '### %s — %s\n\n%s\n\n```bash\ncurl -s -H '"'"'x-ditto-pre-authenticated: nginx:ditto'"'"' \\\n  '"'"'%s'"'"'\n```\n\n**HTTP %s**\n\n```json\n' "$id" "$t" "$why" "$url" "$c"
    echo "$b" | python3 -m json.tool 2>/dev/null || echo "$b"; printf '```\n\n'; } >> "$OUT"
  printf '%-5s %-50s %s\n' "$id" "$t" "$c"; }
mongocase(){ local id="$1" t="$2" why="$3" js="$4"
  { printf '### %s — %s\n\n%s\n\n```bash\nmongosh "%s" --quiet --eval '"'"'%s'"'"'\n```\n\n```\n' "$id" "$t" "$why" "$M" "$js"
    mongosh "$M" --quiet --eval "$js"; printf '```\n\n'; } >> "$OUT"
  printf '%-5s %-50s ok\n' "$id" "$t"; }

hdr "E. Ingest semantics" \
"Ingest is **WoT opt-in**: a property is recorded only if its schema carries \`ditto:timeseries\` with \`ingest: ALL\`. It is forward-only — there is no backfill of values written before the annotation existed."

mongocase E1 "Unannotated property produces no points" \
"\`serial\` is declared in the TM but carries no \`ditto:timeseries\` annotation. It was written with \`PUT .../properties/serial\` (HTTP 204) yet produced zero data points, while the annotated sibling has three." \
'print("serial points          : " + db.ts_ts_demo.countDocuments({"meta.path":/serial/}));
print("flowTemperature (ref-a1): " + db.ts_ts_demo.countDocuments({"meta.thingId":"ts.demo:ref-a1","meta.path":/flowTemperature/}));'

mongocase E2 "Stored point shape" \
"\`meta\` carries the identity and the resolved tags; \`timestamp\` is the Mongo timeField; \`revision\` is the Thing revision the value came from." \
'printjson(db.ts_ts_demo.findOne({"meta.thingId":"ts.demo:ref-a1","meta.path":/flowTemperature/},{_id:0}));'

curlcase E3 "Tags and unit read back in result meta" \
"\`unit\` comes from the WoT schema; \`tags\` are the ingest-time values frozen on the point. \`hasMore\`/\`nextCursor\` drive keyset pagination." \
"$G/timeseries/things/ts.demo:ref-a1/features/env/properties/flowTemperature?from=now-1h&to=now&limit=1"

mongocase E4 "Ingest is NOT gated by READ_TS" \
"\`ref-x\` revokes \`READ_TS\`, yet its points are stored — the permission gates **reads**, not writes. This is what makes revocation retroactive: the data exists, it simply stops being readable." \
'print("ref-x stored points: " + db.ts_ts_demo.countDocuments({"meta.thingId":"ts.demo:ref-x"}));'

hdr "F. Storage, retention and operations" \
"Each namespace maps to one native MongoDB **Time Series collection** named \`<collection-prefix><namespace-with-dots-as-underscores>\`."

mongocase F1 "Collection layout and retention" \
"\`timeField\`/\`metaField\`/\`granularity\` come from the adapter config; \`expireAfterSeconds\` is the retention (default 90d)." \
'db.getCollectionInfos({name:/^ts_/}).forEach(c=>{print(c.name);print("   type: "+c.type);
if(c.options.timeseries) print("   timeseries: "+JSON.stringify(c.options.timeseries));
print("   expireAfterSeconds: "+(c.options.expireAfterSeconds===undefined?"(none)":c.options.expireAfterSeconds+"  ("+(c.options.expireAfterSeconds/86400)+"d)"));});'
```

### 15.8 `ws_test.py` — cross-protocol checks (section G)

```python
import asyncio, json, sys
import websockets

URL = "ws://localhost:8080/ws/2"
HDRS = {"x-ditto-pre-authenticated": "nginx:ditto"}

async def send(label, msg, why):
    print(f"\n### {label}")
    print(why)
    print("\n**Sent:**\n```json")
    print(json.dumps(msg, indent=2))
    print("```")
    try:
        async with websockets.connect(URL, additional_headers=HDRS) as ws:
            await ws.send(json.dumps(msg))
            while True:
                raw = await asyncio.wait_for(ws.recv(), timeout=15)
                d = json.loads(raw)
                if d.get("topic","").endswith(":ACK") or "START-SEND" in raw:
                    continue
                print("\n**Received:**\n```json")
                print(json.dumps(d, indent=2)[:2200])
                print("```")
                return
    except asyncio.TimeoutError:
        print("\n**Received:** _(nothing within 15s — no adapter dispatched the topic)_\n")
    except Exception as e:
        print(f"\n**Error:** `{type(e).__name__}: {e}`\n")

Q = {"thingId":"ts.demo:ref-a1",
     "paths":["/features/env/properties/flowTemperature"],
     "from":"2026-08-03T06:00:00Z","to":"2026-08-03T09:00:00Z",
     "step":"PT1M","aggregation":"avg"}

async def main():
    await send("G1 — single-Thing RetrieveTimeseries over WebSocket",
        {"topic":"ts.demo/ref-a1/things/twin/timeseries/retrieve",
         "headers":{"correlation-id":"ws-g1"},
         "path":"/features/env/properties/flowTemperature","value":Q},
        "The single-Thing command IS protocol-adapter wired (`TimeseriesQueryCommandAdapter`, "
        "`TimeseriesQuerySignalMapper`, both mapping-strategies, `AdapterResolverBySignal`). "
        "This establishes that cross-protocol parity is achievable and already in place for this command.")

    await send("G2 — cross-Thing aggregation over WebSocket",
        {"topic":"ts.demo/_/things/twin/timeseries/aggregate",
         "headers":{"correlation-id":"ws-g2"},
         "path":"/",
         "value":{"namespace":"ts.demo",
                  "paths":["/features/env/properties/flowTemperature"],
                  "from":"2026-08-03T06:00:00Z","to":"2026-08-03T09:00:00Z",
                  "step":"PT1M","aggregation":"avg"}},
        "There is no `aggregate` action in `TimeseriesTopicPathBuilder` and no adapter for "
        "`RetrieveAggregatedTimeseries`, so no topic path can express this command. "
        "This is the live demonstration of review finding 1.")

asyncio.run(main())
```
