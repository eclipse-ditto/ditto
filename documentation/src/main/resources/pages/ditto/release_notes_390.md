---
title: Release notes 3.9.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.0 of Eclipse Ditto, released on 13.05.2026"
permalink: release_notes_390.html
---

The Ditto team is happy to announce the availability of **Eclipse Ditto 3.9.0**.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.9.0 focuses on the following areas:

* **Namespace-scoped policy entries** to limit a policy entry's scope to a configured set of Thing namespaces
* **Namespace root policies** which are transparently merged into all policies of a configured namespace, e.g. for governance, audit or break-glass access
* **Limiting** which **namespaces** are accessible **at the gateway level** via configurable, placeholder-based rules (e.g. derived from JWT claims)
* **Entry-level references in policies and policy imports** to additively merge subjects, resources and namespaces from other entries — both within the same policy and across imported policies — with `transitiveImports` for selective multi-level resolution and `allowedAdditions` to control what may be merged in
* **Resolved policy view** API option returning the merged effective policy after imports and namespace-root resolution
* **Partial change notifications** based on Policy READ permissions, so subscribers only receive the fields they are allowed to read
* **`checkPermissions` API for all protocols** — previously only HTTP — making permission checks available via WebSocket, AMQP and MQTT
* **WoT Discovery** "Thing Directory" endpoint exposing Ditto's Thing collection following the W3C WoT Discovery specification
* **Dynamically scoping a WoT Thing Description** to the requesting user's policy permissions, removing properties/actions/events the user cannot access
* **Encryption key rotation** for connectivity service secrets, including DevOps-triggered re-encryption of stored credentials
* **X509 client-certificate authentication** to MongoDB, with a configurable CA root certificate for the TLS connection
* **`empty()` RQL filter** to match absent or empty fields in search and event filters
* **`fn:format()` placeholder pipeline function** for correlated field extraction from JSON arrays
* **Slow search query logging** with configurable threshold to identify expensive queries
* **Configurable custom MongoDB search indexes** for tuning Ditto search to specific workloads
* **Per-namespace activity-check configuration** to vary entity passivation timeouts per namespace
* **Live entities Prometheus metric** per namespace and entity type
* **Per-metric MongoDB index hint** for operator-defined custom metrics
* **OpenID Connect prerequisite-conditions** for early JWT rejection (e.g. audience validation)
* **Placeholder replacement in `migrateDefinition`** migration payloads
* **Local/relative `tm:ref` references** in WoT ThingModel resolution
* **`ditto:deprecationNotice`** WoT extension term to mark deprecated properties, actions and events
* **WoT context prefix validation** ensuring custom JSON-LD prefixes are declared in `@context`
* **"Time Travel" mode** in the Explorer UI to inspect a Thing's state at any past revision or timestamp, alongside live and historical event browsing
* **Resizable panels** in the Explorer UI

The following non-functional work is also included:

* **Building and running Ditto with Java 25**
* Updating dependencies to their latest versions
* **Optimizing the `MongoReadJournal` aggregation pipelines** and the `ThingEventEnricher` → `TreeBasedPolicyEnforcer` hot path
* **JFR-guided CPU optimisations** in the things, things-search, gateway and connectivity services — addressing dispatcher misconfiguration, hot-path allocations and Netty leak-detection overhead
* **Stackless 4xx exceptions** (feature-toggled): `DittoRuntimeException` subclasses with HTTP status `< 500` no longer capture a stack trace by default, since they signal flow control rather than bugs
* **Configurable SSE publisher backpressure buffer size** to suppress noisy backpressure WARN logs from slow SSE consumers
* **Comprehensive JavaDoc** for the public WoT model interfaces
* **Helm chart bumped to `4.0.0`** — the bundled `ingress-nginx` controller was **removed** so users can plug in their own ingress controller, and the chart now follows its **own semantic version**, decoupled from Ditto's `appVersion`
* **Adding global `extraVolumes` / `extraVolumeMounts`** support to all Helm services
* **Additional Helm values** for redacted log headers and other operator-tunable settings

The following notable fixes are included:

* **Surfacing enforcement and validation errors for fire-and-forget commands** instead of silently swallowing them, with a dedicated timeout to avoid request stalls
* Fixing **`checkPermissions` ignoring permissions inherited from imported policies**
* Fixing **partial-access SSE event filtering** for subscribers with multiple authorization subjects
* Fixing **MongoDB aggregation pipeline performance regression** affecting `connections_journal` reads
* Fixing **Kafka consumer crash loop** triggered by messages with blank header values
* Fixing a **Fluency thread leak** in the connection logger publisher
* Fixing **MQTT 5 enforcement validation** rejecting valid header placeholders in the `input` field
* **Redacting sensitive header values** in `DittoHeaders.toString()` to prevent accidental log leaks
* Fixing **subscription handling for multiple topics combined with extra fields** in connectivity outbound mapping
* Converting transient **enforcement `AskTimeoutException` to HTTP 503** instead of 500 during rolling restarts, so clients see a retryable error
* Fixing **`ssl-config` not being picked up** for self-signed certificates against the OpenID Connect issuer
* Closing a **shadowing vulnerability in namespace-policies** by routing namespace-policy entries through rewritten labels


### New features

#### Namespace-scoped policy entries

Issue [#2325](https://github.com/eclipse-ditto/ditto/issues/2325) / PR [#2368](https://github.com/eclipse-ditto/ditto/pull/2368)
adds an optional `namespaces` field to policy entries. A scoped entry only applies to Things whose namespace
matches at least one configured pattern. If `namespaces` is omitted or empty, the entry remains globally
applicable, preserving backward compatibility.

Supported matching semantics include exact matches (`com.acme`), nested wildcards (`com.acme.*`), and
prefix matches — letting a single policy serve multiple namespaces with differentiated permissions.

The documentation of the feature can be found [here](basic-policy.html#namespaces).

#### Namespace root policies

Issue [#1638](https://github.com/eclipse-ditto/ditto/issues/1638) / PR [#2367](https://github.com/eclipse-ditto/ditto/pull/2367)
adds support for configuring a set of "namespace root policies" which Ditto transparently merges into every
policy of the given namespace. This enables operator-controlled cross-tenant concerns — such as audit/governance
READ access, compliance monitoring, break-glass SRE accounts, or forced minimum logging — without requiring
each policy author to opt in.

Wildcard-based namespace mappings are supported and several namespace root policies may contribute to the
same namespace.

The documentation of the feature can be found [here](basic-policy.html#namespace-root-policies).

#### Limiting which namespaces are accessible at the gateway level

Issue [#2304](https://github.com/eclipse-ditto/ditto/issues/2304) / PR [#2348](https://github.com/eclipse-ditto/ditto/pull/2348)
allows the Ditto administrator to configure namespace-access rules at the gateway level (HTTP / WebSocket).
Namespaces accessible to a request can be matched against placeholders, e.g. a JWT claim, so that a request
authenticated with a given token is restricted to the namespaces it owns.

The documentation of the feature can be found [here](installation-operating.html#gateway-namespace-access-control).

#### Entry-level references for policies and policy imports

Issue [#2221](https://github.com/eclipse-ditto/ditto/issues/2221) / PRs
[#2347](https://github.com/eclipse-ditto/ditto/pull/2347), [#2403](https://github.com/eclipse-ditto/ditto/pull/2403)
and [#2424](https://github.com/eclipse-ditto/ditto/pull/2424) introduce a unified `references` array on
policy entries. Each reference is either:

* an **import reference** (`{"import": "<policyId>", "entry": "<label>"}`) — pulling in subjects, resources and
  namespaces from an entry of an imported policy, or
* a **local reference** (`{"entry": "<label>"}`) — referring to another entry within the same policy.

References merge additively. The referenced entry can declare an `allowedAdditions` filter (`subjects`,
`resources`, `namespaces`) that controls which kinds of own additions the referencing entry is allowed to
contribute — enabling secure, template-based authorization where the imported policy author controls what
extension is permitted.

The documentation of the feature can be found [here](basic-policy.html#policy-imports).

#### Transitive policy imports

Issue [#2420](https://github.com/eclipse-ditto/ditto/issues/2420) / PR [#2422](https://github.com/eclipse-ditto/ditto/pull/2422)
adds an optional `transitiveImports` field to policy imports. By default, imports are single-level: if
policy A imports from B and B imports from C, A does not see C's entries. Listing entry labels in
`transitiveImports` opts those imports in to be resolved transitively — particularly useful for
template-based policy hierarchies where intermediate policies enrich the template via `references`.

The documentation of the feature can be found [here](basic-policy.html#transitive-import-resolution).

#### Resolved policy view

Issue [#2354](https://github.com/eclipse-ditto/ditto/issues/2354) / PR [#2429](https://github.com/eclipse-ditto/ditto/pull/2429)
adds a `policy-view` request hint to `GET /api/2/policies/{id}`. With `policy-view=resolved`, the response
contains the merged effective policy — combining the policy's own entries, declared imports, and configured
namespace-root policies, with all `references` resolved. The default `policy-view=original` continues to
return the policy as stored.

This makes it straightforward to debug effective permissions when policies use imports, references, or
namespace-root policies.

The documentation of the feature can be found [here](basic-policy.html#effective-policy-view).

#### Partial read access events

Issue [#96](https://github.com/eclipse-ditto/ditto/issues/96) / PR [#2287](https://github.com/eclipse-ditto/ditto/pull/2287)
enables emitting partial change notifications based on Policy READ permissions. A subscriber that is only
allowed to read parts of a Thing now receives change events containing exactly those readable fields, instead
of the event being suppressed entirely.

#### `checkPermissions` API available for all protocols

PR [#2356](https://github.com/eclipse-ditto/ditto/pull/2356) makes the `checkPermissions` API
protocol-agnostic. Previously only reachable via the HTTP gateway, the API can now be used from devices and
backend services communicating over WebSocket, AMQP or MQTT.

#### WoT Discovery Thing Directory endpoint

Issue [#2142](https://github.com/eclipse-ditto/ditto/issues/2142) / PR [#2298](https://github.com/eclipse-ditto/ditto/pull/2298)
adds a "well-known" WoT Discovery Thing Directory endpoint that describes Ditto's Thing collection, following
the W3C WoT Discovery specification. The endpoint supports pagination via `limit` and `offset` URI variables
and can be configured to be available either to authenticated users only or publicly.

#### Dynamically scoping the WoT Thing Description by user permissions

Issue [#2144](https://github.com/eclipse-ditto/ditto/issues/2144) / PR [#2409](https://github.com/eclipse-ditto/ditto/pull/2409)
filters the generated WoT Thing Description in the enforcement layer based on the requesting user's policy
permissions:

* Properties the user cannot READ are removed from the TD
* Properties the user cannot WRITE are marked `readOnly`
* Actions the user cannot invoke are removed
* Events the user cannot subscribe to are removed
* Sub-model links to inaccessible features are removed

The documentation of the feature can be found [here](basic-wot-integration.html).

#### Local/relative `tm:ref` references in ThingModel resolution

Issue [#1648](https://github.com/eclipse-ditto/ditto/issues/1648) / PR [#2328](https://github.com/eclipse-ditto/ditto/pull/2328)
extends `tm:ref` resolution to support local references within the same ThingModel file (e.g.
`#/properties/genericTemperature`), as defined in the W3C WoT specification — instead of requiring an
external URL for every reference.

The documentation of the feature can be found [here](basic-wot-integration.html).

#### `ditto:deprecationNotice` for WoT models

Issue [#2320](https://github.com/eclipse-ditto/ditto/issues/2320) / PR [#2327](https://github.com/eclipse-ditto/ditto/pull/2327)
adds a new `ditto:deprecationNotice` term to the Ditto WoT Extension Ontology, allowing properties, actions
and events to be marked as deprecated. The notice is a structured object containing a `deprecated` flag,
an optional `supersededBy` pointer to the replacement and an optional SemVer `removalVersion`.

The documentation of the feature can be found [here](basic-wot-integration.html#the-deprecationnotice-term).

#### WoT context prefix validation

PR [#2305](https://github.com/eclipse-ditto/ditto/pull/2305) adds validation that all JSON-LD context
prefixes used in WoT ThingModels and ThingDescriptions are properly declared in `@context`. Custom prefixes
such as `ditto:category` or `om2:kilowatt` must have their prefix (`ditto`, `om2`) declared; standard WoT
prefixes (`td`, `tm`, `jsonschema`, `wotsec`, `hctl`, `htv`, `schema`, `rdfs`, `rdf`, `xsd`, `dct`) are
allowed without explicit definition. Validation is enabled by default and can be disabled via configuration.

#### Encryption key rotation for connection secrets

Issue [#2340](https://github.com/eclipse-ditto/ditto/issues/2340) / PR [#2350](https://github.com/eclipse-ditto/ditto/pull/2350)
implements rotation of the AES-256-GCM key used to encrypt sensitive connection data such as credentials and
URIs. A dual-key configuration (`symmetrical-key` plus `old-symmetrical-key`) provides automatic fallback
during decryption, and a DevOps piggyback command triggers re-encryption of stored connection secrets — all
without service interruption.

The documentation of the feature can be found [here](installation-operating.html#encryption-key-rotation).

#### X509 authentication for MongoDB connection

PR [#2445](https://github.com/eclipse-ditto/ditto/pull/2445) adds support for using X509 client-certificate
authentication when Ditto connects to MongoDB, and additionally allows configuring the CA root certificate
used in the TLS connection to MongoDB.

#### `empty()` RQL filter

Issue [#2377](https://github.com/eclipse-ditto/ditto/issues/2377) / PR [#2397](https://github.com/eclipse-ditto/ditto/pull/2397)
introduces a new RQL filter function `empty(<field>)` that matches Things where a field is absent or empty.
This complements existing comparison operators and is supported anywhere RQL is used (search, event filters,
etc.).

The documentation of the feature can be found [here](basic-rql.html#empty).

#### `fn:format()` placeholder pipeline function

Issue [#2358](https://github.com/eclipse-ditto/ditto/issues/2358) / PR [#2364](https://github.com/eclipse-ditto/ditto/pull/2364)
adds a new `fn:format()` pipeline function that processes each JSON object in an array individually,
keeping field extractions correlated within each object — solving the Cartesian-product problem when
multiple placeholders independently resolve fields from the same array of objects. Mustache-inspired section
syntax (`{#array}...{/array}`, `{.}`) is supported for nested arrays.

The documentation of the feature can be found [here](basic-placeholders.html#correlated-field-extraction-with-fnformat).

#### Slow search query logging

Issue [#2053](https://github.com/eclipse-ditto/ditto/issues/2053) / PR [#2308](https://github.com/eclipse-ditto/ditto/pull/2308)
adds configurable slow-query logging to the search service. When enabled (default: on, threshold 1s),
queries exceeding the threshold are logged with their duration, namespaces, RQL filter and the corresponding
MongoDB BSON filter — making it straightforward to find queries that need optimization or additional indexes.

#### Configurable custom MongoDB search indexes

PR [#2302](https://github.com/eclipse-ditto/ditto/pull/2302) adds support for declaring additional MongoDB
indexes on the search collection via HOCON configuration. Compound indexes with multiple fields and per-field
ASC/DESC direction are supported, and index lifecycle is managed by the existing `activated-index-names`
mechanism. Helm chart values support is also included.

The documentation of the feature can be found [here](installation-operating.html#configuring-additional-search-indexes).

#### Per-namespace activity-check configuration

Issue [#2280](https://github.com/eclipse-ditto/ditto/issues/2280) / PR [#2309](https://github.com/eclipse-ditto/ditto/pull/2309)
makes the `activity-check.inactive-interval` and `deleted-interval` configurable per namespace, allowing
operators to use different entity-passivation timeouts depending on namespace patterns — e.g. keeping
high-traffic namespaces in memory longer than rarely-touched ones.

#### Live entities Prometheus metric per namespace

PR [#2314](https://github.com/eclipse-ditto/ditto/pull/2314) introduces a new Prometheus gauge metric
`live_entities` that tracks the count of active entities per namespace, with tags for `type` (thing, policy,
search-updater) and `namespace`. A periodic timer refreshes the metric at a configurable interval (default:
30s).

#### Per-metric index hint for custom metrics

Issue [#2329](https://github.com/eclipse-ditto/ditto/issues/2329) / PR [#2333](https://github.com/eclipse-ditto/ditto/pull/2333)
adds an optional `index-hint` configuration for both count-based and aggregation-based custom metrics,
allowing operators to specify per-metric MongoDB index hints instead of relying solely on global hints.
The hint supports both the index name and an explicit index key specification.

#### OpenID Connect prerequisite-conditions for JWT validation

Issue [#2277](https://github.com/eclipse-ditto/ditto/issues/2277) / PR [#2323](https://github.com/eclipse-ditto/ditto/pull/2323)
adds a `prerequisite-conditions` configuration option to OpenID Connect issuer configuration. Conditions
are evaluated against the JWT before any policy/access checks, so tokens that don't meet the criteria
(e.g. matching audience) are rejected early with a 401.

The documentation of the feature can be found [here](installation-operating.html#openid-connect).

#### Placeholder replacement in `migrateDefinition`

Issue [#2319](https://github.com/eclipse-ditto/ditto/issues/2319) / PR [#2321](https://github.com/eclipse-ditto/ditto/pull/2321)
adds support for `{{ thing-json:<json-path> }}` placeholder replacement inside the `migrationPayload` of the
`migrateDefinition` API, so migrations can refer to fields of the existing Thing being migrated.

The documentation of the feature can be found [here](httpapi-concepts.html).

#### `deleteField` mapping in the Normalized payload mapper

PR [#2307](https://github.com/eclipse-ditto/ditto/pull/2307) extends the Normalized payload mapper with an
opt-in `includeDeletedFields` option. When enabled, partial delete events (e.g. `AttributeDeleted`,
`FeatureDeleted`) and merge-patch nulls are surfaced via a dedicated `_deletedFields` field — letting
downstream consumers distinguish "field never existed" from "field was explicitly deleted".

The documentation of the feature can be found [here](connectivity-mapping.html#normalized-mapper).

#### Swagger / OpenAPI: OpenID Connect security scheme

PR [#2330](https://github.com/eclipse-ditto/ditto/pull/2330) adds an `openIdConnect` security scheme to the
bundled OpenAPI specification, so the Swagger UI can authenticate against an OpenID Connect issuer when
trying out API calls.

#### History exploration in the Explorer UI

PR [#2407](https://github.com/eclipse-ditto/ditto/pull/2407) adds time-travel capabilities to the Explorer UI:

* a Time Travel Mode in the Thing Details tab with a revision slider and timestamp picker, so any past
  state of a Thing can be inspected
* a dual-mode "Thing Updates" section supporting both live (SSE) and historical event browsing

Visual indicators (warning banner, amber border, badges) clearly signal when historical data is being viewed.

#### Resizable panels in the Explorer UI

Issue [#2374](https://github.com/eclipse-ditto/ditto/issues/2374) / PR [#2375](https://github.com/eclipse-ditto/ditto/pull/2375)
makes the Explorer UI panes horizontally resizable via draggable splitters, with sizes persisted across
sessions.

#### UI support for Ditto 3.9.0 policy and search features

Issue [#2405](https://github.com/eclipse-ditto/ditto/issues/2405) / PR [#2434](https://github.com/eclipse-ditto/ditto/pull/2434)
adds first-class UI support to the Explorer for the new policy concepts in this release: editing
`transitiveImports` per import, the new `namespaces` editor on policy entries, the `references` editor with
two-dropdown picker (local vs. imported entries), and the `allowedAdditions` tri-state control. The search
tab gets autocomplete entries and tooltip hints for the new `empty()` RQL function.


### Changes

#### Building and running Ditto with Java 25

PR [#2313](https://github.com/eclipse-ditto/ditto/pull/2313) updates Ditto's build and runtime to Java 25.

#### Optimizing the MongoReadJournal pipelines

PR [#2355](https://github.com/eclipse-ditto/ditto/pull/2355) optimizes the MongoDB aggregation pipelines
used by `MongoReadJournal` and bumps related dependency versions.

#### Optimizing the `ThingEventEnricher` → `TreeBasedPolicyEnforcer` hot path

PR [#2344](https://github.com/eclipse-ditto/ditto/pull/2344) reduces overhead in the high-traffic
`ThingEventEnricher` → `TreeBasedPolicyEnforcer` code path involved in event enrichment.

#### Service CPU optimisations from JFR profiling

PR [#2440](https://github.com/eclipse-ditto/ditto/pull/2440) addresses a series of CPU hotspots identified
by Java Flight Recorder captures on the things, things-search, gateway and connectivity services. Highlights
include an O(k) forward index in `PolicyEnforcerCache.deregisterImportMappings` instead of O(N) full-map
scans, a null-PolicyId short-circuit in `AbstractEnforcerActor.loadPolicyEnforcer`, static `CBORFactory`
reuse in `JacksonSerializationContext`, parsing long-then-downcast in `DefaultDittoJsonHandler` to avoid
~113 `NumberFormatException`/s on 64-bit pub/sub hashes, a bulk-copy fast path in
`JavaStringToEscapedJsonString`, an O(H) `validateValueTypes` overload that iterates the (typically small)
header map instead of all known header definitions, and adding the missing `thread-pool-executor` block
(with `allow-core-timeout = off`) to several Pekko dispatchers so idle core threads are no longer killed
every 60 s. Netty's leak detection is disabled by default in the Helm chart values to avoid the per-sampled-buffer
`Throwable` capture.

#### Stackless flow-control exceptions

PR [#2446](https://github.com/eclipse-ditto/ditto/pull/2446) makes `DittoRuntimeException` subclasses with
HTTP status `< 500` (e.g. `ThingNotAccessibleException`, `PolicyNotAccessibleException`,
`WotThingModelPayloadValidationException`, `MessageSendNotAllowedException`) omit their stack trace and
suppressed-exception list by default. JFR profiling showed `fillInStackTrace()` and `StackTraceElement[]`
allocation dominating the exception path on gateway pods. Exceptions with status `>= 500` always keep their
stack trace because they signal real bugs or infrastructure problems. The behaviour is governed by the
feature toggle `ditto.devops.feature.stackless-flow-control-exceptions-enabled` (default `true`); flipping
it off restores the legacy stack-capturing behaviour.

#### Configurable SSE publisher backpressure buffer size

PR [#2447](https://github.com/eclipse-ditto/ditto/pull/2447) makes the source-queue buffer of the SSE
publisher in `ThingsSseRouteBuilder` configurable, with the default raised from `10` to `100`. The previous
hard-coded value of 10 caused frequent Pekko `Backpressuring because buffer is full` WARN logs whenever a
client consumed server-sent events slower than upstream production. The new HOCON setting is
`ditto.gateway.streaming.sse.publisher.backpressure-buffer-size`, also exposed via the Helm chart value
`gateway.config.sse.publisher.backpressureBufferSize`.

#### Comprehensive JavaDoc on the public WoT model API

PR [#2324](https://github.com/eclipse-ditto/ditto/pull/2324) adds method-level JavaDoc to the public
interfaces in the `wot/model` module, with anchored links to the relevant sections of the W3C WoT Thing
Description 1.1 specification.

#### Improving the WoT context prefix validation error message

PR [#2393](https://github.com/eclipse-ditto/ditto/pull/2393) includes the model URL in WoT context prefix
validation error messages, making it easier to identify which ThingModel triggered a failure when many models
are in use.

#### Reducing log noise from `ConnectionPersistenceActor` recovery

PR [#2390](https://github.com/eclipse-ditto/ditto/pull/2390) silences spurious "Unknown message" warnings
that were emitted by `ConnectionPersistenceActor` during connection recovery, especially during rolling
deployments.

#### Updating dependencies to their latest versions

PRs [#2398](https://github.com/eclipse-ditto/ditto/pull/2398) and [#2339](https://github.com/eclipse-ditto/ditto/pull/2339)
update used dependencies (including Rhino) to their latest versions.

#### Stabilizing flaky unit tests

PRs [#2341](https://github.com/eclipse-ditto/ditto/pull/2341), [#2342](https://github.com/eclipse-ditto/ditto/pull/2342),
[#2343](https://github.com/eclipse-ditto/ditto/pull/2343) and [#2345](https://github.com/eclipse-ditto/ditto/pull/2345)
fix flaky unit tests in `MqttClientActorTest`, `AmqpClientActorTest`, `DittoPublicKeyProviderTest`,
`PubSubFactoryTest` and `CustomSSLContextTest`, including replacing static mocking with constructor injection.


### Bugfixes

#### Closing a shadowing vulnerability in namespace-policies

Issue [#2431](https://github.com/eclipse-ditto/ditto/issues/2431) / PR [#2433](https://github.com/eclipse-ditto/ditto/pull/2433)
fixes a vulnerability where a tenant in a bound namespace could silently shadow a namespace-policy entry by
choosing a colliding label. Namespace-policy entries are now merged under rewritten labels of the form
`nsimported-<sourcePolicyId>-<originalLabel>`, mirroring the existing `imported-` rewriting for declared
imports. Local labels starting with `nsimported-` are rejected by the validator with HTTP 400
`policies:label.invalid`. This also lets multiple namespace-roots that share a label compose additively.

#### Validating `allowedImportAdditions` for alias-based subject modifications

PR [#2419](https://github.com/eclipse-ditto/ditto/pull/2419) closes a gap where `ModifySubject` /
`ModifySubjects` via imports aliases bypassed the `allowedImportAdditions` validation that was already
enforced for direct `ModifyPolicyImport`. Subject modifications via aliases now go through the same check.

#### Fix `checkPermissions` ignoring permissions from imported policies

PR [#2414](https://github.com/eclipse-ditto/ditto/pull/2414) fixes that `/api/2/checkPermissions` returned
`false` for policies that derive permissions entirely from imported policies, even when the actual operations
would have been authorized.

#### Surface enforcement and validation errors for fire-and-forget commands

Issue [#2392](https://github.com/eclipse-ditto/ditto/issues/2392) / PR [#2396](https://github.com/eclipse-ditto/ditto/pull/2396)
addresses that fire-and-forget commands (timeout=0, no ack requests) previously returned HTTP 202
immediately, silently swallowing policy-enforcement and WoT validation errors. The gateway now waits for the
enforcement/validation result and returns the error if one arrives.

#### Fix fire-and-forget commands returning 503 instead of 202

PR [#2412](https://github.com/eclipse-ditto/ditto/pull/2412) fixes that the fire-and-forget code path could
return HTTP 503 when the receive timeout fired, instead of the expected HTTP 202 Accepted, due to checking
the unmodified original command's `response-required` instead of the rewritten one.

#### Fix fire-and-forget commands delayed by full request timeout

PR [#2413](https://github.com/eclipse-ditto/ditto/pull/2413) introduces a dedicated
`fire-and-forget-enforcement-timeout` so fire-and-forget commands no longer wait the full 60 second request
timeout for the (never-arriving) success response before returning HTTP 202.

#### Fix Fluency thread leak in connection logger publisher

PR [#2418](https://github.com/eclipse-ditto/ditto/pull/2418) fixes a thread leak in the Fluency-based
connection logger publisher which could exhaust the connectivity service over time.

#### Fix aggregation metrics retaining stale values

PR [#2408](https://github.com/eclipse-ditto/ditto/pull/2408) fixes that Prometheus gauges for aggregation
metrics retained their last value even after the corresponding `$group` bucket vanished from the MongoDB
result, causing stale values to be reported indefinitely.

#### Fix partial access filtering for subscribers with multiple authorization subjects

PR [#2404](https://github.com/eclipse-ditto/ditto/pull/2404) fixes that a subscriber with multiple
authorization subjects — some with partial READ access and some with unrestricted access — was treated as
having only partial access, stripping fields like `thingId`, `_revision`, `_modified` and `_created` from
SSE events. The unrestricted-access path is now correctly preferred.

#### Fix Kafka consumer crash loop on messages with blank header values

PR [#2401](https://github.com/eclipse-ditto/ditto/pull/2401) fixes that blank tracing header values (e.g.
empty `correlation-id`) caused an `IllegalArgumentException` during span creation, leading to a Kafka
consumer crash loop on the same poison message under QoS 1. The fix skips blank header values defensively.

#### Fix MQTT 5 enforcement validation rejecting header placeholders

Issue [#2388](https://github.com/eclipse-ditto/ditto/issues/2388) / PR [#2389](https://github.com/eclipse-ditto/ditto/pull/2389)
fixes that `AbstractMqttValidator` incorrectly rejected header placeholders (e.g. `{{ header:device }}`) in
the enforcement `input` field for MQTT 5 connections, even though the runtime fully supported them.

#### Fix MongoDB aggregation pipeline performance regression

PR [#2385](https://github.com/eclipse-ditto/ditto/pull/2385) removes redundant `$project` stages between
`$match` and `$sort` in `MongoReadJournal` aggregation pipelines, which had prevented MongoDB from combining
`$match + $sort + $limit` into an efficient index-backed top-K scan and caused queries on the
`connections_journal` collection to take hundreds of seconds.

#### Fix subscription handling for multiple topics with extra fields

PR [#2425](https://github.com/eclipse-ditto/ditto/pull/2425) fixes incorrect handling of connectivity target
subscriptions that combine multiple topics with `extraFields`. Previously, with several topics and at least
one carrying `extraFields`, only one of the topics actually delivered messages and the choice was
non-deterministic. The `OutboundMappingProcessorActor` flow now retrieves extra fields for all topics in a
single request and applies the filter against the already-resolved fields.

#### Use string subject IDs in `PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT`

PR [#2384](https://github.com/eclipse-ditto/ditto/pull/2384) fixes that `ThingEventEnricher` produced
non-string subject IDs in `PRE_DEFINED_EXTRA_FIELDS_READ_GRANT_OBJECT`.

#### Redact sensitive header values in `DittoHeaders.toString()`

PR [#2362](https://github.com/eclipse-ditto/ditto/pull/2362) prevents sensitive header values such as bearer
tokens from leaking into logs whenever objects containing `DittoHeaders` are logged. The set of redacted
keys defaults to `authorization` and is configurable via `ditto.headers.redacted-in-log`.

#### Convert enforcement `AskTimeoutException` to HTTP 503

Issue [#2439](https://github.com/eclipse-ditto/ditto/issues/2439) / PR [#2441](https://github.com/eclipse-ditto/ditto/pull/2441)
fixes that an `AskTimeoutException` from an enforcer child during rolling restarts was wrapped as
`DittoInternalErrorException` (HTTP 500), which misled clients into treating a transient timeout as a
permanent error. A new `EnforcementTimeoutException` (HTTP 503) is returned instead, signalling that the
operation can be retried. The fix also unwraps `CompletionException` wrappers from async chains before
checking the exception type.

#### Fix `ssl-config` not being picked up for the OpenID Connect HTTP client

Issue [#2443](https://github.com/eclipse-ditto/ditto/issues/2443) / PR [#2444](https://github.com/eclipse-ditto/ditto/pull/2444)
restores `ssl-config` handling for the OpenID Connect HTTP client, which had broken after an upstream
Pekko HTTP change in which `defaultClientHttpsContext()` stopped applying `ssl-config`. Ditto now uses
`createDefaultClientHttpsContext()` so self-signed certificates configured via `ssl-config` against the
OpenID Connect issuer are honoured again.


### Helm Chart

The Helm chart bundled with this release moves to **chart version `4.0.0`**, a breaking major bump driven
by the removal of the bundled `ingress-nginx` controller (see PR
[#2386](https://github.com/eclipse-ditto/ditto/pull/2386) below). Operators who previously relied on the
chart's built-in ingress configuration need to provide their own ingress controller before upgrading.

From this release on, the Helm chart **`version`** is **decoupled from Ditto's `appVersion`** and follows
its own semantic versioning. Chart-only changes (Helm template fixes, value additions, breaking value
changes) will increment the chart version independently — chart `version` and `appVersion` are no longer
kept in lockstep.

In addition to the configuration options for the new features of this release, the chart includes:

* PR [#2386](https://github.com/eclipse-ditto/ditto/pull/2386) **removes the bundled `ingress-nginx`
  controller** from the Helm chart. As `ingress-nginx` is deprecated as of 31.03.2026, the choice of ingress
  controller is now left to the operator. All `ingress-nginx`-specific annotations and the
  `nginx-ingress-auth.yaml` / `nginx-ingress.yaml` templates have been removed. See the
  [blog post](2026-04-01-ingress-controller-agnostic-helm-chart.html) for migration guidance.
* PR [#2387](https://github.com/eclipse-ditto/ditto/pull/2387) adds **global `extraVolumes` and
  `extraVolumeMounts`** options that are merged with per-service settings on every Ditto service.
* PR [#2399](https://github.com/eclipse-ditto/ditto/pull/2399) exposes additional Helm values, including
  `redacted-headers-in-logs`.
* PR [#2442](https://github.com/eclipse-ditto/ditto/pull/2442) exposes the OpenID Connect **`issuers`**
  option in the Helm chart values, so OIDC issuers can be configured directly from `values.yaml`.


## Migration notes

No mandatory migration steps are required for the Ditto services themselves.

The bundled **Helm chart bumps to `4.0.0`**: Helm chart `version` and `appVersion` are no longer kept in
sync, and the chart now follows its own independent semantic versioning. Chart `4.0.0` ships with Ditto
`appVersion: 3.9.0`.

If you currently use a Helm chart deployment relying on the bundled `ingress-nginx` controller, the
controller has been removed and you need to provide your own ingress controller before upgrading. See
PR [#2386](https://github.com/eclipse-ditto/ditto/pull/2386) and the corresponding
[blog post](2026-04-01-ingress-controller-agnostic-helm-chart.html) for migration guidance.
