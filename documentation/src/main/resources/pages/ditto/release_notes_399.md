---
title: Release notes 3.9.9
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.9 of Eclipse Ditto, released on 19.11.2026"
permalink: release_notes_399.html
---

This is a **security bugfix release**, no new user-facing API features since
[3.9.8](release_notes_398.html) were added.
It fixes six security vulnerabilities — among them a **critical** authentication bypass in deployments using the
default configuration of the Ditto Helm chart — in the authentication of the gateway, in the authorization of merge
patches, conditions and permission checks, and in the filtering of Thing events and the resolved policy view.
It also contains many bugfixes in the things, gateway, connectivity and things-search services, and new operational
safeguards and performance improvements.

{% include note.html content="**We recommend all users to update to this release, especially when deploying Ditto
with the Helm chart in its default configuration (`global.jwtOnly: false`).** See the *Security fixes* section below
for the exact preconditions and for workarounds if you cannot upgrade immediately, and the *Migration notes* for
requests which are rejected or behave differently after the update." %}

## Changelog

Compared to the latest release [3.9.8](release_notes_398.html), the following security fixes, changes and
bugfixes were added.


### Security fixes

The vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes were
merged from private forks, there are no public pull requests for them; the merge commits of the fixes are linked
directly instead. The linked advisories carry the full details, including the affected code, proofs of concept and
the available workarounds.

| Severity     | CVE                                                                                                                           | Affected component                  |
|--------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| **Critical** | [CVE-2026-120753](#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter) | gateway, Helm chart                 |
| **High**     | [CVE-2026-129699](#security-fix-for-cve-2026-129699--merge-patch-deletions-and-empty-objects-bypass-write-authorization)      | things                              |
| **High**     | [CVE-2026-129700](#security-fix-for-cve-2026-129700--thing-fields-exposed-via-condition-and-live-channel-condition)           | things                              |
| **High**     | [CVE-2026-132039](#security-fix-for-cve-2026-132039--thing-events-disclose-field-values-the-subscriber-may-not-read)          | things, gateway, connectivity       |
| **Low**      | [CVE-2026-129702](#security-fix-for-cve-2026-129702--revokes-ignored-in-multi-permission-and-partial-permission-checks)       | policies, things (policy enforcers) |
| **Low**      | [CVE-2026-129916](#security-fix-for-cve-2026-129916--resolved-policy-view-discloses-non-readable-entry-content)               | policies                            |

#### Security fix for CVE-2026-120753 — Subject impersonation via the `x-ditto-pre-authenticated` query parameter

Advisory [GHSA-chwq-wg27-qvfr](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-chwq-wg27-qvfr) /
CVE [CVE-2026-120753](https://nvd.nist.gov/vuln/detail/CVE-2026-120753) /
Fix commit [`b30297712d`](https://github.com/eclipse-ditto/ditto/commit/b30297712daf359b27d6d05bfdda1b95f713bc6d)

Severity **critical** (CVSS 4.0 score 9.1,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`).

When [pre-authentication](installation-operating.html#pre-authentication) is enabled, the gateway accepted the
pre-authenticated subject not only from the `x-ditto-pre-authenticated` HTTP header — which a trusted reverse proxy
is expected to set — but also from an undocumented `x-ditto-pre-authenticated` **URL query parameter** which any
client can set. A reverse proxy correctly overwriting the header did not prevent this
([CWE-287](https://cwe.mitre.org/data/definitions/287.html),
[CWE-290](https://cwe.mitre.org/data/definitions/290.html)).

In the **default configuration of the Ditto Helm chart** (`global.jwtOnly: false`, which implicitly enables
pre-authentication), the bundled nginx skips HTTP Basic authentication for every request carrying an
`Authorization: Bearer ...` header and forwards it without the pre-authentication header. An **unauthenticated
remote attacker** could therefore send an arbitrary (invalid) Bearer token together with
`?x-ditto-pre-authenticated=<subject>` on the HTTP API or the WebSocket endpoint and was authenticated as the chosen
policy subject, without the JWT ever being validated. The attacker could then read, modify and delete all Things
and Policies granted to that subject.

Affected are Ditto versions **1.1.0 through 3.9.8** with pre-authentication enabled, where at least one request
path reaches the gateway without a non-empty, proxy-set `x-ditto-pre-authenticated` header — in particular **all
installations of the Ditto Helm chart with default settings**. Not affected are deployments with pre-authentication
disabled (the gateway's built-in default), Helm chart installations with `global.jwtOnly: true`, and the
`deployment/docker` Docker Compose setup.

The gateway now reads the pre-authenticated subject **only** from the `x-ditto-pre-authenticated` HTTP header. A
request carrying an invalid Bearer token together with the query parameter is rejected with `400` (`jwt:invalid`).
The Helm chart additionally anchors the nginx `Bearer` detection to the start of the `Authorization` header, and the
[authentication](operating-authentication.html#pre-authentication) and
[security hardening](operating-security-hardening.html) documentation describe that the reverse proxy must set the
header from a verified identity on every path.

If you cannot upgrade immediately, disable pre-authentication in the gateway (`ENABLE_PRE_AUTHENTICATION=false`),
or, when using the Helm chart, configure `global.jwtOnly: true` (keeping
`gateway.config.authentication.enablePreAuthentication: false`).

#### Security fix for CVE-2026-129699 — Merge patch deletions and empty objects bypass WRITE authorization

Advisory [GHSA-5hh9-qp4v-6f56](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-5hh9-qp4v-6f56) /
CVE [CVE-2026-129699](https://nvd.nist.gov/vuln/detail/CVE-2026-129699) /
Fix commit [`069f8d8b85`](https://github.com/eclipse-ditto/ditto/commit/069f8d8b85725793a762d5933ef7f6fff8369dbb)

Severity **high** (CVSS 4.0 score 7.1,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:N/VI:H/VA:L/SC:N/SI:N/SA:N`).

The authorization of [JSON merge patch](httpapi-concepts.html#partial-updates) commands on Things (HTTP `PATCH` /
Ditto Protocol `merge`) did not check the `WRITE` permission for fields which a merge patch
**deletes** (`null` values, including Ditto's regex-based key deletion `{{ ~<regex>~ }}`) or **sets to an empty
object** (`{}`) ([CWE-863](https://cwe.mitre.org/data/definitions/863.html),
[CWE-436](https://cwe.mitre.org/data/definitions/436.html)).

An authenticated subject with `WRITE` permission on **any part** of a Thing could therefore delete any other part of
it — including fields on which the policy explicitly revokes `WRITE` — overwrite any field with an empty object, or
create new fields as empty objects. For example, a subject only allowed to write `thing:/attributes/a` could delete
all attributes, Features and the definition of the Thing with a single request. Subjects without any `WRITE`
permission on the Thing and read access control were not affected.

Affected are Ditto versions **2.0.0 through 3.9.8** (the regex deletion variant since 3.5.5). The things service now
requires `WRITE` permission on every field which a merge patch deletes or sets to an empty object.

There is no configuration-based workaround. For untrusted subjects with partial `WRITE` permission, operators can
reject HTTP `PATCH` requests to the Things API in their reverse proxy; this does not cover `merge` commands sent via
the WebSocket API or connections.

#### Security fix for CVE-2026-129700 — Thing fields exposed via `condition` and `live-channel-condition`

Advisory [GHSA-x88m-jq5c-jpcq](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-x88m-jq5c-jpcq) /
CVE [CVE-2026-129700](https://nvd.nist.gov/vuln/detail/CVE-2026-129700) /
Fix commit [`de0789b935`](https://github.com/eclipse-ditto/ditto/commit/de0789b935549909c1231eeaffc087c2b2039f70)

Severity **high** (CVSS 4.0 score 7.1,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N`).

[Conditions](basic-conditional-requests.html) given via the `condition` and
[`live-channel-condition`](basic-conditional-requests.html#live-channel-condition) headers are evaluated against the
complete, unfiltered Thing, so the requester must have `READ` permission on all fields they reference. This was not
ensured in four cases ([CWE-863](https://cwe.mitre.org/data/definitions/863.html),
[CWE-200](https://cwe.mitre.org/data/definitions/200.html)):

1. when both headers were present on a Thing query command, `READ` was only checked for the `condition` (since 3.0.0),
2. conditions on [WoT Thing Description](basic-wot-integration.html) requests (`Accept: application/td+json`) were
   evaluated without any policy authorization when WoT TD permission filtering is disabled — the only behavior
   before 3.9.0 (since 2.4.0),
3. the `condition` of live messages, claim messages and live events was evaluated without checking `READ` on the
   referenced fields (since 3.1.0),
4. smart channel twin responses (when the `live-channel-condition` did not match) were returned **without filtering
   them by the policy** (since 3.0.0).

The result of an unchecked condition was exposed to the requester (e.g. `412 Precondition Failed` versus a successful
response, or the `live-channel-condition-matched` response header), allowing an authenticated user to recover field
values they may not read by testing predicates one request at a time — depending on the case with `READ` on any part
of the Thing, with `WRITE` on a message resource, or **without any permission** on the Thing. In case 4 the field
values were directly contained in the response.

Affected are Ditto versions **2.4.0 through 3.9.8**. Now, `READ` on the fields of the `live-channel-condition` is
checked independently of the `condition`, `READ` on the fields of the `condition` of live messages and live events is
checked, smart channel twin responses are filtered by the policy, and conditions on WoT Thing Description requests
and on claim messages are rejected with `400 Bad Request` (`header.invalid`).

There is no complete configuration-based workaround. On 3.9.x, keep WoT TD permission filtering enabled (the default).
In a reverse proxy, operators can reject HTTP requests carrying a `live-channel-condition` header, and requests to the
message endpoints (`/inbox`, `/outbox`) carrying a `condition` header; this does not cover the WebSocket API or
connections.

#### Security fix for CVE-2026-132039 — Thing events disclose field values the subscriber may not read

Advisory [GHSA-3fx4-8jrp-7pqm](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-3fx4-8jrp-7pqm) /
CVE [CVE-2026-132039](https://nvd.nist.gov/vuln/detail/CVE-2026-132039) /
Fix commit [`0890c25dfc`](https://github.com/eclipse-ditto/ditto/commit/0890c25dfc16630be97227d0d10d064606a988a3)

Severity **high** (CVSS 4.0 score 7.1,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N`).

Ditto delivered Thing events via WebSocket, [SSE](httpapi-sse.html) and connections containing field values which
the policy does not allow the subscriber to read — and which a retrieve of the same Thing correctly filters out. It
also evaluated subscription filters, placeholders, `_context` and [enrichment](basic-enrichment.html) (`extraFields`)
on behalf of the subscriber on the **unfiltered** event ([CWE-863](https://cwe.mitre.org/data/definitions/863.html)).
This affected:

1. **subscribers with several subjects** (e.g. a user and a group subject extracted from the same JWT, or a
   connection target with several authorization subjects): a `READ` revoke for one subject was not applied when
   another subject of the same authorization context was granted `READ`,
2. **subscribers with partial `READ` access** (partial access events, since 3.9.0): the event payload was filtered,
   but not everything evaluated on behalf of the subscriber, so e.g. subscription filters could select the
   non-readable fields to disclose,
3. **receivers of live commands**: a live command was delivered with its whole payload to every subscriber with
   `READ` on the command's path, even if `READ` was revoked for a part of the payload.

Retrieving and searching Things, and the authorization of modifying commands and live messages, were not affected.

Affected are all Ditto versions **up to 3.9.8**. The things service now attaches, per subject, the paths whose
`READ` is revoked to every Thing event, live event and modifying live command; gateway and connectivity remove the
paths revoked for any subject of the subscriber, and evaluate filters, placeholders, `_context` and extra fields only
on the part of the event the subscriber may read. An event is not delivered if the subscriber may read nothing at its
path, and a live command is not delivered to a subscriber who may not read all of it.

If you cannot upgrade immediately, do not rely on a `READ` revoke for one subject to restrict what another subject
of the same authorization context may read (add the revoke to the policy entry of every subject granted `READ` on
the resource), and do not grant partial `READ` access on Things containing fields which must stay confidential towards
these subjects — or disable partial access events (`THING_EVENT_PARTIAL_ACCESS_EVENTS_ENABLED=false`), after which
partially granted subjects receive no events for these Things.

#### Security fix for CVE-2026-129702 — Revokes ignored in multi-permission and partial permission checks

Advisory [GHSA-73pw-g56w-95h8](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-73pw-g56w-95h8) /
CVE [CVE-2026-129702](https://nvd.nist.gov/vuln/detail/CVE-2026-129702) /
Fix commit [`ca8b34ab5f`](https://github.com/eclipse-ditto/ditto/commit/ca8b34ab5fa306d6e7138fa3ab710d3d4f4f7020)

Severity **low** (CVSS 4.0 score 2.3,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:L/UI:N/VC:L/VI:N/VA:N/SC:L/SI:L/SA:N`).

Ditto's policy enforcers did not let a revoke win over a grant in two kinds of permission checks
([CWE-863](https://cwe.mitre.org/data/definitions/863.html)):

1. **checks of several permissions at once** (default, throughput-optimized "trie-based" enforcer): a more specific
   grant of one permission hid a revoke of another permission. Through the
   [`checkPermissions` API](basic-auth-checkpermissions.html), this could answer `true` for e.g. `["READ", "WRITE"]`
   although `WRITE` is revoked,
2. **partial permission checks**, used to authorize retrieving Things and Policies and to determine the receivers of
   events: a grant on a sub-resource was counted although the same permission was revoked on that very sub-resource
   by another subject of the same authorization context (both enforcers), or for the same subject (memory-optimized
   "tree-based" enforcer).

The content of retrieved Things and Policies was not affected, as it is filtered field by field. Requesters could,
however, learn about the **existence** of Things and Policies and receive metadata (e.g. an empty `{}` with status
`200` instead of `404`, or events with empty payloads) where the policy grants them no readable content, and
applications relying on the `checkPermissions` result for their own authorization decisions could be misled.

Affected are all Ditto versions **up to 3.9.8**. Both enforcers now evaluate grants and revokes per permission and
let a revoke on a sub-resource cancel the grants of all subjects of the authorization context on that sub-resource.
A randomized test verifies both enforcers against a reference evaluation of the policy semantics.

If you cannot upgrade immediately, avoid policies which grant a permission to one subject and revoke the same
permission on the same resource from another subject of the same authorization context, and request only one
permission per `checkPermissions` entry.

#### Security fix for CVE-2026-129916 — Resolved policy view discloses non-readable entry content

Advisory [GHSA-mx63-5cwx-v3pr](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-mx63-5cwx-v3pr) /
CVE [CVE-2026-129916](https://nvd.nist.gov/vuln/detail/CVE-2026-129916) /
Fix commit [`3ca45182df`](https://github.com/eclipse-ditto/ditto/commit/3ca45182df8d1bcdb198e67c228c47e085ea95b8)

Severity **low** (CVSS 4.0 score 2.3,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:L/UI:N/VC:L/VI:N/VA:N/SC:N/SI:N/SA:N`).

The [resolved policy view](basic-policy.html#effective-policy-view) (`GET /api/2/policies/{policyId}?policy-view=resolved`,
also available via the `policy-view` header) merges a policy with the entries it imports and with the entries of
the namespace root policies applying to its namespace. It did not apply the `READ` permissions of the policies the
merged content originates from ([CWE-863](https://cwe.mitre.org/data/definitions/863.html),
[CWE-200](https://cwe.mitre.org/data/definitions/200.html)):

* directly imported and namespace root entries only required `READ` on **some part** of the entry in the source
  policy, and were then returned **completely**,
* entries of transitive imports (`transitiveImports`) and of the imports of namespace root policies were only checked
  against the intermediate policy,
* content copied into an entry via `references` was not checked against the `READ` permissions on the referenced
  entry at all.

The resolved view could therefore contain subjects, resources and granted/revoked permissions which are hidden from
the caller when reading the originating policy directly. It did not allow modifying policies or accessing Things.

Affected are Ditto versions **3.9.0 through 3.9.8**. Every policy contributing to the resolved view is now
restricted to the caller's `READ` view of it before being merged, and directly imported and namespace root entries
are additionally filtered field by field, like a direct read of the source policy.

If you cannot upgrade immediately, reject requests with the `policy-view` query parameter or header set to
`resolved` in a reverse proxy (HTTP API only), or grant callers who may read a policy `READ` on everything that policy
imports or references.


### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.9).

#### Close WebSocket and SSE sessions whose mailbox lags behind

PR [#2630](https://github.com/eclipse-ditto/ditto/pull/2630) adds the optional gateway setting
`ditto.gateway.streaming.max-session-lag` (environment variable `GATEWAY_STREAMING_MAX_SESSION_LAG`, default `0s` =
disabled). A WebSocket or SSE session processing events slower than they arrive could queue millions of messages in
its unbounded mailbox until the gateway ran out of heap — and the resulting GC pauses could make the whole cluster
unreachable. When configured, a session whose mailbox delays messages longer than the configured lag is closed
instead, logged and counted in the metric `streaming_session_lag_terminations`. WebSocket clients receive a
`gateway:service.unavailable` error, SSE clients see the connection close.

#### Optional jitter for the expire-after-write of the policy enforcer cache

PR [#2650](https://github.com/eclipse-ditto/ditto/pull/2650) adds the optional cache configuration
`expire-after-write-jitter` (default `0.0` = disabled), exposed for the policy enforcer cache of the things, policies
and connectivity services as `DITTO_POLICIES_ENFORCER_CACHE_EXPIRE_AFTER_WRITE_JITTER`. Cache entries written at the
same time — e.g. all hot entries after a restart — otherwise expire and are reloaded at the same time every
`expire-after-write`, which was observed to cause daily reload bursts and long full GCs in production. With e.g.
`0.2`, the expiry of each entry is randomly varied by +/-20%, spreading such bursts out within a few cycles.

#### Per-session summary log of SSE sessions

PR [#2648](https://github.com/eclipse-ditto/ditto/pull/2648) logs one INFO summary line when a `/things`
[SSE](httpapi-sse.html) session ends, containing how many events it received, targeted, filtered out, failed to
enrich and delivered, plus the subscribing subjects, `azp`, `Origin` and `User-Agent` as MDC entries. This allows to
find out which clients consume how many events. As the summary contains personal data, operators can suppress it by
setting the log level of `org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseSessionStatistics` to `WARN`.

#### Configure the gateway's connections retrieve limit via environment variable

PR [#2624](https://github.com/eclipse-ditto/ditto/pull/2624) makes the limit of connections returned by
`GET /api/2/connections` (`ditto.gateway.command.connections-retrieve-limit`, default `100`) configurable via the
environment variable `GATEWAY_CONNECTIONS_RETRIEVE_LIMIT`. Before, it could only be raised via a Java system property.

#### Memoize namespace access rule conditions

PR [#2631](https://github.com/eclipse-ditto/ditto/pull/2631) reduces the CPU usage of the
[namespace access control](installation-operating.html#gateway-namespace-access-control) in the gateway, which took about 45% of the gateway's CPU
under production load. The conditions of a rule are now resolved once per validator instead of for every checked
signal (except for conditions using the `time:` placeholder), and the rules are parsed only once.

#### Avoid repeated JSON/CBOR encoding, parsing and copying

PR [#2620](https://github.com/eclipse-ditto/ditto/pull/2620) reduces allocations and CPU usage in the headers and
the cluster serialization: JSON header values are parsed only once, nested JSON values are written directly into the
CBOR output, and parsed fields are taken over instead of copied. `DittoHeaders.newBuilder(JsonObject)` now matches
header keys case-insensitively when validating, so an invalid value of a mixed-case header key is rejected right away.

#### Disable the partial access paths cache by default

PR [#2592](https://github.com/eclipse-ditto/ditto/pull/2592) changes the default of
`THING_EVENT_PARTIAL_ACCESS_EVENTS_CACHE_ENABLED`, introduced in 3.9.3, to `false`. Enabling it trades memory for
CPU and should be considered together with the size of the policy enforcer cache.


### Bugfixes

#### Do not apply HTTP commands rejected for an invalid `timeout` header

Issue [#2643](https://github.com/eclipse-ditto/ditto/issues/2643) / PR
[#2655](https://github.com/eclipse-ditto/ditto/pull/2655) fixes HTTP commands rejected with
`400 header.invalid` because of an invalid `timeout` — e.g. `timeout=0` combined with a required response — being
**executed nevertheless** after the 400 was sent. They are no longer forwarded. See the
[migration notes](#migration-notes) for the impact on fire-and-forget requests to policies, connections and DevOps
commands.

#### Answer fire-and-forget HTTP requests without waiting for the enforcement timeout

Issue [#2642](https://github.com/eclipse-ditto/ditto/issues/2642) / PR
[#2644](https://github.com/eclipse-ditto/ditto/pull/2644) fixes successful fire-and-forget HTTP requests
(`timeout=0`) being answered with `202` only after the `fire-and-forget-enforcement-timeout` (3s) since 3.9.0. The
services now confirm the successful enforcement, so the gateway answers right away; errors are still returned with
their status code.

#### Defer passivation of persistence actors while enforcement is in flight

Issue [#2599](https://github.com/eclipse-ditto/ditto/issues/2599) / PR
[#2600](https://github.com/eclipse-ditto/ditto/pull/2600) fixes "Ditto Internal Error" responses, passivation hangs
and shutdown failures when a Thing, Policy or Connection supervisor was passivated while an enforcement was in
flight. Pending operations now drain before the actors stop, waiting at most the new `shutdown-timeout` of the
supervisors (default `12s`, environment variables `THING_SHUTDOWN_TIMEOUT`, `POLICY_SUPERVISOR_SHUTDOWN_TIMEOUT` and
`CONNECTIVITY_SUPERVISOR_SHUTDOWN_TIMEOUT`). The previous `THING_SHUTDOWN_TIMEOUT` default of `3s` thereby changes
to `12s`.

#### Fail closed on partial access path enrichment errors

Issue [#2605](https://github.com/eclipse-ditto/ditto/issues/2605) / PR
[#2606](https://github.com/eclipse-ditto/ditto/pull/2606) fixes the calculation of partial access paths failing on
`null` JSON objects, and makes the enrichment of Thing events fail closed: if it fails or the policy enforcer is not
available, the event is published without partially granted receivers instead of failing.

#### Resolve `thing:` and `feature:` placeholders in WebSocket and SSE filters

Issue [#2617](https://github.com/eclipse-ditto/ditto/issues/2617) / PR
[#2633](https://github.com/eclipse-ditto/ditto/pull/2633) fixes `thing:` and `feature:`
[placeholders](basic-placeholders.html) in the RQL `filter` of WebSocket subscriptions being accepted but never
resolved: e.g. `eq(thing:name,'x')` never matched. WebSocket and SSE now accept and resolve the same placeholders
`topic:`, `entity:`, `thing:`, `feature:`, `resource:` and `time:`.

#### Compare all values of multi-valued placeholders in RQL filters

Issue [#2618](https://github.com/eclipse-ditto/ditto/issues/2618) / PR
[#2632](https://github.com/eclipse-ditto/ditto/pull/2632) fixes placeholders resolving to several values — e.g.
`feature:id` for an event modifying several Features — only being compared by their first value. Comparisons now
match if **any** value matches (`ne` only if **no** value equals the operand), like for array fields of a Thing.

#### Support `exists()` and `empty()` on the complete `attributes` and `features`

Issue [#2637](https://github.com/eclipse-ditto/ditto/issues/2637) / PR
[#2646](https://github.com/eclipse-ditto/ditto/pull/2646) fixes [RQL](basic-rql.html) `exists(attributes)`,
`empty(attributes)`, `exists(features)` and `empty(features)` being rejected with `Unknown property name`. They can
now be used e.g. to search for Things without any attributes or Features.

#### Speed up namespaced search queries

Issue [#2613](https://github.com/eclipse-ditto/ditto/issues/2613) / PR
[#2614](https://github.com/eclipse-ditto/ditto/pull/2614) fixes searches restricted to `namespaces` getting slower
with every Thing added to other namespaces, especially for the last cursor page. The queries are now additionally
bounded by the Thing ID ranges of the namespaces, so MongoDB no longer walks the Things of other namespaces. The
results are unchanged.

#### Fix the connectivity pre-filter for filters on `extraFields`

Issue [#2611](https://github.com/eclipse-ditto/ditto/issues/2611) / PR
[#2612](https://github.com/eclipse-ditto/ditto/pull/2612) and issue
[#2615](https://github.com/eclipse-ditto/ditto/issues/2615) / PR
[#2616](https://github.com/eclipse-ditto/ditto/pull/2616) fix connection targets dropping signals before the
[enrichment](basic-enrichment.html) although they matched the target's `filter` after it. This happened for filters
on Feature (desired) properties, on fields covered by `extraFields` with wildcards or placeholders, on ancestors of
`extraFields` and on simple fields like `definition`.

#### Apply topic namespaces in the connectivity re-check after enrichment

Issue [#2622](https://github.com/eclipse-ditto/ditto/issues/2622) / PR
[#2634](https://github.com/eclipse-ditto/ditto/pull/2634) fixes a connection target publishing a signal via a topic
subscribed for **another namespace** when the topics for the signal's own namespace were filtered out after the
enrichment with `extraFields`.

#### Reject topic filter values which cannot be URL-decoded

Issue [#2563](https://github.com/eclipse-ditto/ditto/issues/2563) / PR
[#2587](https://github.com/eclipse-ditto/ditto/pull/2587) fixes target topics containing `%25`, `%26` or `%2B` in a
filter value being stored in a form which could not be parsed again, or which was altered when the connection was
loaded. Such values are now encoded correctly, and values with an invalid `%` escape are rejected with `400`.

#### Support `{{ source:address }}` as enforcement input of Kafka and AMQP connections

Issue [#1640](https://github.com/eclipse-ditto/ditto/issues/1640) / PR
[#2601](https://github.com/eclipse-ditto/ditto/pull/2601) allows the `{{ source:address }}` placeholder as
enforcement input of Kafka, AMQP 0.9.1 and AMQP 1.0 [connection sources](basic-connections.html), in addition to
MQTT. It resolves to the source address the consumer reads from.

#### Accept EC client keys in PEM-encoded PKCS #8

Issues [#2177](https://github.com/eclipse-ditto/ditto/issues/2177) and
[#473](https://github.com/eclipse-ditto/ditto/issues/473) / PR
[#2640](https://github.com/eclipse-ditto/ditto/pull/2640) fixes valid EC client keys being rejected as
"bad format" for [client certificate authentication](connectivity-tls-certificates.html) of connections, as only RSA
keys were supported. EC keys are now accepted as well, also for SSH tunnels.

#### Always use the weak comparison for `If-None-Match`

Issue [#2636](https://github.com/eclipse-ditto/ditto/issues/2636) / PR
[#2639](https://github.com/eclipse-ditto/ditto/pull/2639) fixes weak entity tags such as `W/"rev:13"` in the
`If-None-Match` header never matching, as required by RFC 7232. A conditional `GET` now returns `304` and a
modifying request fails with `412` when the weak tag matches the current revision.


### Helm Chart

The Helm chart was updated to version `4.9.0`, bumping the Ditto `appVersion` to `3.9.9`.

As part of the [security fix for CVE-2026-120753](#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter),
the nginx `Bearer` detection is anchored to the start of the `Authorization` header, and `values.yaml` documents that
`global.jwtOnly: false` implicitly enables pre-authentication in the gateway. Installations with the default
`global.jwtOnly: false` should update to this chart version.

It also adds the configuration options of this release:

* `gateway.config.streaming.maxSessionLag` (default `0s`, disabled) to close lagging WebSocket and SSE sessions
  (PR [#2630](https://github.com/eclipse-ditto/ditto/pull/2630)),
* `policiesEnforcer.cache.expireAfterWriteJitter` for `things`, `policies` and `connectivity` (default `0.0`,
  disabled) to spread the reloads of the policy enforcer cache
  (PR [#2650](https://github.com/eclipse-ditto/ditto/pull/2650)).

The full, itemized list of chart changes lives in the chart's own
[CHANGELOG](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).


## Migration notes

There are no migration steps required for the Ditto data model or APIs. However, several security fixes and bugfixes
reject requests which were previously accepted, or deliver less data:

* **`x-ditto-pre-authenticated` query parameter**
  ([CVE-2026-120753](#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter)):
  the pre-authenticated subject is only read from the `x-ditto-pre-authenticated` HTTP header. Reverse proxies
  relying on the undocumented query parameter must set the header instead.
* **Merge patches** ([CVE-2026-129699](#security-fix-for-cve-2026-129699--merge-patch-deletions-and-empty-objects-bypass-write-authorization)):
  merge patches deleting fields (`null`) or setting them to `{}` now require `WRITE` permission on these fields and
  are rejected otherwise. Clients sending such patches with only partial `WRITE` permission must restrict them to
  the fields they may write.
* **Conditions** ([CVE-2026-129700](#security-fix-for-cve-2026-129700--thing-fields-exposed-via-condition-and-live-channel-condition)):
  `condition` and `live-channel-condition` headers on WoT Thing Description requests and a `condition` on claim
  messages are rejected with `400` (`header.invalid`). Conditions of live messages and live events, and
  `live-channel-condition`s combined with a `condition`, now require `READ` on the referenced fields.
* **Thing events and live commands** ([CVE-2026-132039](#security-fix-for-cve-2026-132039--thing-events-disclose-field-values-the-subscriber-may-not-read)):
  subscribers with several subjects or partial `READ` access receive fewer fields or events, and live commands are
  only delivered to subscribers who may read all of their content. The fix requires the things, gateway and
  connectivity services to be updated; it is only fully effective once all of them run 3.9.9.
* **`timeout=0` on HTTP requests** (PR [#2655](https://github.com/eclipse-ditto/ditto/pull/2655)): only Thing
  modifying commands, live commands and messages turn `timeout=0` into `response-required=false` implicitly. For
  other commands — e.g. on Policies, Connections or DevOps piggyback commands — `timeout=0` without
  `response-required=false` was already rejected with `400`, but the command was **applied nevertheless**. It is now
  no longer applied. Clients sending such fire-and-forget requests must add `response-required=false`.
* **Supervisor shutdown timeout** (PR [#2600](https://github.com/eclipse-ditto/ditto/pull/2600)): the default of
  `THING_SHUTDOWN_TIMEOUT` changes from `3s` to `12s`. Keep custom values below the coordinated shutdown phase
  `cluster-sharding-shutdown-region` (15s).
