---
title: Release notes 3.9.7
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.7 of Eclipse Ditto, released on 01.09.2026"
permalink: release_notes_397.html
---

This is a **security bugfix release**, no new user-facing API features since
[3.9.6](release_notes_396.html) were added.
It fixes two security vulnerabilities — a JSON template injection in the connectivity service's
`ImplicitThingCreation` payload mapper and a Server-Side Request Forgery when fetching WoT ThingModels in the
things service — and continues the CPU-reduction work of the previous patch releases, this time on the
policy-enforcer tree walk, trace-context propagation and the read-subject header.

{% include note.html content="**We recommend all users to update to this release.** Both vulnerabilities are
reachable by authenticated parties in default configurations — see the *Security fixes* section below for the
exact preconditions and for workarounds if you cannot upgrade immediately." %}

## Changelog

Compared to the latest release [3.9.6](release_notes_396.html), the following security fixes, changes and
bugfixes were added.


### Security fixes

Both vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes
were merged from private forks, there are no public pull requests for them; the fix commits are linked
directly instead. The linked advisories carry the full details, including the affected code, the fix and the
available workarounds.

#### Security fix for CVE-2026-82958 — JSON template injection in the `ImplicitThingCreation` mapper

Advisory [GHSA-cgfq-3fv9-5c44](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-cgfq-3fv9-5c44) /
CVE [CVE-2026-82958](https://nvd.nist.gov/vuln/detail/CVE-2026-82958) /
Fix commit [`73bf966e87`](https://github.com/eclipse-ditto/ditto/commit/73bf966e87217d6db8753875de87964e04dd4300)

Severity **high** (CVSS 4.0 score 7.6,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:L/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`).

The connectivity service's `ImplicitThingCreationMessageMapper` built its `CreateThing` command by
substituting placeholder values (e.g. `{%raw%}{{ header:device_id }}{%endraw%}`) resolved from inbound message headers into the
configured JSON *thing template* as raw, un-escaped strings, and only then parsed the result as JSON. The
placeholder engine performs no JSON escaping and is unaware of the surrounding JSON string context, so a
resolved value containing a `"` character could break out of its string and inject additional JSON structure —
most importantly an inline `_policy` object, which overrides the administrator-configured `policyId`
([CWE-1336](https://cwe.mitre.org/data/definitions/1336.html)). `_copyPolicyFrom` and `policyId` could be
injected the same way.

An attacker able to publish on such a connection could therefore assign an arbitrary access-control policy to
the newly created digital twin — granting themselves full read/write access and potentially revoking the
legitimate owner's access — without any administrator interaction.

Affected are Ditto versions **1.3.0 through 3.9.6**, but only deployments that use the (non-default)
`ImplicitThingCreation` payload mapper **and** whose thing template reflects a header value the publishing
device can control (e.g. an MQTT 5 user property, an AMQP 1.0 application property or a Kafka record header).
The policy-override outcome additionally requires the connection's authorization subjects to be permitted to
create policy entities, which is the default. Deployments whose template only reflects values asserted by
trusted middleware (e.g. an Eclipse Hono provided `device_id` derived from device authentication), or that do
not use this mapper at all, are not exploitable through a device.

The mapper now resolves placeholders per JSON key and per JSON leaf value *through the JSON model* — which
escapes them — instead of substituting them into the raw template string before parsing. A resolved value is
confined to the single key or string value it originated from and can no longer introduce additional JSON
fields such as `_policy`.

If you cannot upgrade immediately, either of the following mitigates the issue:

* **Restrict entity creation for the connection** (recommended, blocks the policy hijack): configure
  [`ditto.entity-creation`](installation-operating.html#restricting-entity-creation) so the connection's
  authorization subjects may create only `thing` entities, not `policy` entities. The inline `_policy` is
  applied via a separate `CreatePolicy` command, which is then rejected with `EntityNotCreatableException`,
  failing the injected creation. Reference an existing, admin-owned policy via `policyId` in the template.
  Note that entity creation is unrestricted by default.
* **Do not reflect device-controllable headers** into the
  [ImplicitThingCreation](connectivity-mapping.html#implicitthingcreation-mapper) thing template.

#### Security fix for CVE-2026-84175 — Server-Side Request Forgery via WoT ThingModel `definition` URL

Advisory [GHSA-7f3j-xpvm-wwmg](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-7f3j-xpvm-wwmg) /
CVE [CVE-2026-84175](https://nvd.nist.gov/vuln/detail/CVE-2026-84175) /
Fix commit [`a0e704cc24`](https://github.com/eclipse-ditto/ditto/commit/a0e704cc24052d8df6d5034c71fea33e97b084c6)

Severity **medium** (CVSS 4.0 score 5.3,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:L/VI:N/VA:L/SC:L/SI:N/SA:N`).

When a Thing or Feature `definition` contains an `http(s)://` URL, the things service fetches the referenced
[WoT ThingModel](basic-wot-integration.html) from that URL. Affected versions performed **no validation of the
target host**: there was no allow-list, no blocking of loopback / link-local / site-local / unique-local
addresses, and HTTP redirects were followed without re-validating the redirect target and without a hop limit
([CWE-918](https://cwe.mitre.org/data/definitions/918.html)).

Because the fetch originates inside the deployment's network, any authenticated user who may create a Thing —
or who holds `WRITE` permission on an existing Thing — could make the things service issue arbitrary HTTP `GET`
requests to internal endpoints, such as cloud instance-metadata services (`169.254.169.254`, `fd00:ec2::254`),
the Kubernetes API server or internal admin interfaces. The error returned to the caller differs depending on
the target's behaviour (HTTP 421 `wot:tm.notfound` vs. HTTP 400 `wot:tm.invalid`), which could be used as an
oracle to enumerate internal services, and where an internal target returned a JSON object parseable as a
ThingModel, parts of it could be incorporated into the created Thing and returned to the caller. A single API
call could be amplified into many internal requests, because `tm:extends`, `tm:ref` and `tm:submodel` links of
a fetched ThingModel are resolved and fetched as well. Secondarily, redirects were followed recursively
without a hop limit ([CWE-674](https://cwe.mitre.org/data/definitions/674.html)), so a server redirecting to
itself could occupy a WoT dispatcher thread for the duration of the 10 second fetch timeout.

The affected endpoints are `POST /api/2/things`, `PUT /api/2/things/{thingId}`,
`PUT /api/2/things/{thingId}/definition`, `PUT /api/2/things/{thingId}/features/{featureId}/definition` and
`POST /api/2/things/{thingId}/migrateDefinition`. Versions **3.0.0 and later** are affected in the **default
configuration**, because the WoT integration feature toggle is enabled by default since
[3.0.0](release_notes_300.html); versions 2.4.0 to 2.5.x contain the same code but are only affected where an
operator explicitly set `DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=true`.

The fix validates the target host of **every** WoT ThingModel fetch:

* Hosts resolving to loopback, link-local (covering `169.254.169.254`), site-local, IPv6 unique-local
  `fc00::/7` (covering `fd00:ec2::254`), multicast and wildcard addresses are blocked by default, as is the
  carrier-grade NAT range `100.64.0.0/10` (RFC 6598), which no address-class check covers.
* Every redirect target is re-validated before it is followed, and the number of redirects is capped
  (default `5`), which also resolves the uncontrolled-recursion issue.
* `tm:extends`, `tm:ref` and `tm:submodel` links are resolved through the same download path and validated
  identically, and only `http` and `https` targets are fetched — including for redirect targets.

The validation is **enabled by default** and configured under `ditto.things.wot.http.security`, with
corresponding environment variables and Helm values:

```hocon
ditto.things.wot.http.security {
  enabled = true
  allowed-hostnames = ""              # comma separated, always allowed, overrides the blocked address checks
  blocked-hostnames = ""              # comma separated, on top of the always-blocked address classes
  blocked-subnets = "100.64.0.0/10"   # comma separated, CIDR notation
  blocked-host-regex = ""             # empty string disables the check
  max-redirects = 5
}
```

{% include important.html content="Deployments that intentionally serve ThingModels from an internal host must
add that host to the `allowed-hostnames` allow-list — otherwise those fetches now fail. See the
[Migration notes](#migration-notes) below." %}

**Residual limitation:** host validation resolves the URL's hostname to decide whether to allow the fetch,
while the subsequent HTTP request resolves the hostname again independently. For hostnames whose DNS is
controlled by an attacker, the two lookups can in principle return different addresses (DNS rebinding), which
a hostname-based check cannot fully prevent. Targets given as literal IP addresses are always validated.
Operators are advised to combine this check with network-level egress controls — see the new
[Outbound HTTP requests (SSRF)](operating-security-hardening.html#10-outbound-http-requests-ssrf) section of the security hardening guide and
[Restricting WoT ThingModel fetching](installation-operating.html#restricting-wot-thingmodel-fetching) in the
operating documentation.

If you cannot upgrade immediately, apply network-level egress controls (e.g. Kubernetes `NetworkPolicies` or
firewall rules) preventing the things service from reaching internal address ranges and the cloud
instance-metadata endpoint — recommended as defense in depth even after upgrading — restrict entity creation
via [`ditto.entity-creation`](installation-operating.html#restricting-entity-creation) including its
`thing-definitions` allow-list (this covers the creation path only, **not** `migrateDefinition` or the
definition-modification endpoints), grant `WRITE` permission on Things narrowly, or disable the WoT
integration entirely via `DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=false` where it is not used.


### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.7).

#### Memoize authorization verdicts in the policy enforcer

PR [#2514](https://github.com/eclipse-ditto/ditto/pull/2514) memoizes the per-command policy-tree walk in
`TreeBasedPolicyEnforcer`, so recurring authorization decisions become a hash lookup instead of a full tree
traversal. A production JFR profile attributed roughly 10–12% of things-service CPU — and ~5.6% of overall
Ditto microservice fleet CPU — to `visitTree`: every authorization check (`hasUnrestrictedPermissions`,
`hasPartialPermissions`, `getSubjectsWithPermission`, `getSubjectsWithPartialPermission`) walked the entire
policy tree, although the `(resource, subjects, permissions)` tuples recur heavily across signals.

`TreeBasedPolicyEnforcer` is effectively immutable after `createInstance` and a policy change produces a brand
new instance, so the memo lifetime equals the instance lifetime and needs no invalidation. Verdicts are
identical to the non-memoized path. The memo is placed on the enforcer itself rather than on the
`PolicyEnforcer` wrapper, so things, policies and gateway all benefit. It is bounded by the new
`authorization-memo-cache-max-size` (default `10000`, Helm: `authorizationMemoMaxSize`) under the
`ditto.policies-enforcer-cache` config of the `policies`, `things` and `connectivity` services; setting it to
`0` disables the memoization entirely, in which case no maps are allocated at all.

#### Memoize read-subject parsing and `ditto-read-subjects` rendering

PR [#2527](https://github.com/eclipse-ditto/ditto/pull/2527) removes two per-signal costs around the
`ditto-read-subjects` header that scale with the number of READ-granted subjects of a policy.

`DittoHeaders#getReadGrantedSubjects()` / `#getReadRevokedSubjects()` allocated N `AuthorizationSubject`
instances plus a `HashSet` on *every* call, although the result is a pure function of the immutable header
value — on the hot path once per publish and then again on every subscriber node per received message, per
connection target and per streaming session. The parsed subject set is now memoized lazily on the `Header`
instance (the same benign-race pattern as `String#hashCode()`), and since `Header` instances are shared by
reference across header copies, the memo survives `DittoHeaders.newBuilder(existing).build()`.

`ThingCommandEnforcement#addEffectedReadSubjectsToThingSignal` additionally merged the READ classifications
into a fresh `HashSet` and re-rendered the subject IDs into a header string for every emitted `ThingEvent`.
The rendered `JsonArray` is now memoized per resource key on the `PolicyEnforcer`, alongside the existing
read-classification memo and invalidated the same way, and handed to the builder through the new
`DittoHeadersBuilder#readGrantedSubjects(JsonArray)` without re-rendering or re-parsing it.

The rendered header value is set-equal to the previous one; only the element order changes (sorted and
de-duplicated instead of `HashSet` iteration order), which is harmless since every consumer parses the header
into a `Set`. The `Set` returned by the accessors is now unmodifiable and may be a shared instance.

#### Optimize trace-context propagation to avoid a per-hop full header-map copy

PR [#2513](https://github.com/eclipse-ditto/ditto/pull/2513) removes two O(N) costs paid on effectively every
signal and cluster hop when distributed tracing is enabled: `KamonHttpContextPropagation` copied *all*
incoming headers into a new `HashMap` only to add the ~2 W3C trace-context entries it actually writes, and the
majority of call sites then re-wrapped the result through the *validating* `DittoHeaders` constructor,
re-parsing every JSON-typed header value. A production JFR profile attributed roughly 7% of overall
microservice JVM-user CPU to this, and it was the single biggest hotspot in the gateway (~14.5%).

`propagateContextToNewMap(Context)` now writes only the propagated trace-context entries into a fresh small
map, `StartedKamonSpan` overrides `propagateContext(DittoHeaders)` to apply only that delta onto the
already-validated headers via `toBuilder()`, and 20 call sites were migrated to the trusted overload. Raw
`Map<String, String>` callers (Kafka / AMQP / RabbitMQ consumers, HTTP ingress) intentionally stay on the
validating `Map` overload. The same context is propagated and the same trace-context headers are written.

#### Compute search index-length pointer bytes without allocating

PR [#2515](https://github.com/eclipse-ditto/ditto/pull/2515) replaces
`IndexLengthRestrictionEnforcer.jsonPointerBytes(...)`'s `jsonPointer.toString().getBytes(UTF_8).length` — run
per string/number leaf of every indexed Thing on the search-write path, allocating a throwaway pointer
`String` and a throwaway `byte[]` per call — with a direct, allocation-free scan over the pointer's
`JsonKey`s. The computed count is byte-for-byte identical, including RFC 6901 `~` → `~0` escaping and the
single replacement byte the UTF-8 encoder emits for an unpaired surrogate; an equivalence test plus a
randomized property check pin this.

#### Make operator metrics MongoDB read preference and concern configurable

Issue [#2163](https://github.com/eclipse-ditto/ditto/issues/2163) / PR
[#2510](https://github.com/eclipse-ditto/ditto/pull/2510) makes the MongoDB `readPreference` and `readConcern`
used by the operator metrics configurable **independently** from the read settings used for normal searches
(`query.persistence`). Two optional config blocks are added under `ditto.things-search.operator-metrics`:
`custom-metrics-persistence` (for the count based `custom-metrics`) and
`custom-aggregation-metrics-persistence` (for the `$group` based `custom-aggregation-metrics`). Both fall back
to the general `query.persistence` config when absent, so existing deployments are unaffected.

This is useful when searches are configured with an expensive `readConcern` (e.g. `linearizable`) for strong
consistency, while the periodic operator metric queries should rather run cheaply (`readConcern = local`)
and/or be offloaded to a secondary node (`readPreference = secondaryPreferred`) without weakening the
user-facing search guarantees. To support this, `SudoCountThings` gains optional `readPreference` /
`readConcern` fields (tagged `@since 3.9.7`, mirroring the existing `indexHint`), applied per query.

#### Harden host validation of connection targets

PR [#2538](https://github.com/eclipse-ditto/ditto/pull/2538) tightens the outbound host validation applied to
connection targets (`DefaultHostValidator`) in the connectivity service, aligning it with the new WoT host
validation described above: the allow-list and the blocked-host regex now match **case-insensitively**
(hostnames are case-insensitive per RFC 4343, so a rule such as `.*\.internal` previously missed a differently
cased host), **link-local addresses** (`169.254.0.0/16`, `fe80::/10`) are blocked — covering the cloud
instance-metadata endpoint `169.254.169.254`, previously only blocked when an operator happened to configure
the corresponding subnet — and **IPv6 unique-local addresses** (`fc00::/7`, RFC 4193) are blocked, which
`InetAddress#isSiteLocalAddress` does not cover as it only matches the deprecated `fec0::/10` prefix.

The default configuration is deliberately left unchanged: connectivity ships `blocked-hostnames = ""`, which
short-circuits validation entirely, so these checks only take effect for deployments that have connection host
validation enabled.

#### Escape the NUL separators in the policy enforcer's log dedup key

PR [#2528](https://github.com/eclipse-ditto/ditto/pull/2528) replaces two raw NUL (U+0000) bytes in
`PolicyEnforcer.java` — used as separators in the `missingReferenceLogger` dedup key — with the equivalent
unicode escapes. The raw bytes made the whole source file look *binary* to tools scanning for NUL, so `grep`
silently reported no matches for the file. Since unicode escapes are resolved by the Java lexer, the string
literal still contains exactly one NUL character and the compiled class files are byte-for-byte identical.

#### Dependency updates

PR [#2526](https://github.com/eclipse-ditto/ditto/pull/2526) updates the RabbitMQ `amqp-client` dependency
from `5.32.0` to `5.33.1`.


### Bugfixes

#### Fix devops-managed WoT validation dynamic config sections never reaching the validator

PR [#2530](https://github.com/eclipse-ditto/ditto/pull/2530) fixes dynamic
[WoT validation config](basic-wot-validation-config.html) sections written through the devops API being
persisted, replicated and returned by every read endpoint, yet never honoured by the validator — while
sections from the static config file kept working. On a production-like cluster the validator ran WoT
validation config revision 503 for over a week while the entity had already advanced to 530, so every dynamic
section added after revision 503 was silently ignored and Things violating their Thing Model were rejected
with `wot:payload.validation.error` despite a matching scope configured with
`log-warning-instead-of-failing-api-calls: true`.

The config is replicated through an `ORSet` in `WotValidationConfigDData` whose update function returned a
freshly built set, ignoring the value it was handed. Since the replicator *merges* that result into the
locally stored value and `ORSet` merge resolves per writing node, such a write only superseded the element
written by its own node — elements written by other nodes, in particular by pods that had since left the
cluster, stayed in the set forever. `ThingsRootActor` then picked whichever element came first in iteration
order, and once that was an outdated one, every node stayed on the outdated config. The update functions are
now derived from the set they are handed (which also repairs an already corrupted set on the next write, with
no manual cleanup or cluster restart needed), the config with the *highest revision* is selected, and the
config is published from the persistence actor's `onEntityModified()` hook — after the event was persisted and
applied — instead of from five individual command strategies.

The PR additionally fixes the `_created` and `_modified` timestamps of the WoT validation config never
advancing while `_revision` kept increasing: `ImmutableWotValidationConfig.of(…)` forwarded the two timestamps
positionally to a constructor declaring them in the opposite order (a compensating swap in
`WotValidationConfigUtils.mergeWithEntity()` hid this for the merged view while every other call site got them
wrong), and the event strategies for merged and deleted dynamic config sections bumped the revision but copied
both timestamps over unchanged.

#### Fix partial read access dropping events with an object payload at a non-root path

Issue [#2531](https://github.com/eclipse-ditto/ditto/issues/2531) / PR
[#2532](https://github.com/eclipse-ditto/ditto/pull/2532) fixes the partial-access filter in
`AdaptablePartialAccessFilter` silently dropping events whose payload is a JSON object located at a non-root
path — e.g. a merge or modify event on a Feature's `properties` — for subjects holding
[partial read access](basic-policy.html#grant-and-revoke-some-permission). Such events were filtered away entirely instead of being
reduced to the readable subset, so affected subscribers never saw the change.


### Helm Chart

The Helm chart was updated to version `4.7.0`, bumping the Ditto `appVersion` to `3.9.7`.

It exposes the new WoT ThingModel host validation of the SSRF fix under `things.config.wot.hostValidation`
(`enabled`, `allowedHostnames`, `blockedHostnames`, `blockedSubnets`, `blockedHostRegex`, `maxRedirects`), the
new `authorizationMemoMaxSize` under the policy-enforcer `cache` config of the `policies`, `things` and
`connectivity` services, and the optional `customMetricsPersistence` / `customAggregationMetricsPersistence`
blocks of the things-search operator metrics. It further contains the Swagger UI OIDC mount fix that was
listed for chart `4.3.0` but had never been cherry-picked to that release branch, and bumps the bundled
`nginx` and `swagger-ui` images.

The full, itemized list of chart changes lives in the chart's own
[CHANGELOG](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).


## Migration notes

There are no migration steps required for the Ditto data model or APIs.

However, the fix for [CVE-2026-84175](#security-fix-for-cve-2026-84175--server-side-request-forgery-via-wot-thingmodel-definition-url)
changes the default behaviour of WoT ThingModel fetching: **hosts resolving to loopback, link-local,
site-local, IPv6 unique-local, multicast and wildcard addresses, plus the carrier-grade NAT range
`100.64.0.0/10`, are blocked by default.** Deployments that intentionally serve ThingModels from such an
internal host — e.g. a model registry running inside the same cluster and addressed by a cluster-internal
name — must add that host to the allow-list before upgrading:

```hocon
ditto.things.wot.http.security.allowed-hostnames = "my-model-registry.internal,10.0.0.5"
```

via the environment variable `THINGS_WOT_THING_MODEL_HTTP_SECURITY_ALLOWED_HOSTNAMES` or the Helm value
`things.config.wot.hostValidation.allowedHostnames`. Fetching a ThingModel from a blocked host fails with
HTTP 421 `wot:tm.notfound`.

Additionally, deployments using the [ImplicitThingCreation](connectivity-mapping.html#implicitthingcreation-mapper)
payload mapper should be aware that placeholder values resolved into the thing template are now JSON-escaped
and confined to the key or value they originate from. Templates that (intentionally) relied on a resolved
header value injecting JSON structure will no longer work — this was the vulnerability fixed by
[CVE-2026-82958](#security-fix-for-cve-2026-82958--json-template-injection-in-the-implicitthingcreation-mapper).
