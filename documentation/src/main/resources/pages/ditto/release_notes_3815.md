---
title: Release notes 3.8.15
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.15 of Eclipse Ditto, released on 19.11.2026"
permalink: release_notes_3815.html
---

This is a **security bugfix release** for the 3.8 line, no new features since
[3.8.14](release_notes_3814.html) were added. It contains **only** the fixes for those security vulnerabilities
also fixed in [3.9.9](release_notes_399.html) which affect the 3.8 line — among them a **critical** authentication
bypass in deployments using the default configuration of the Ditto Helm chart — but no other changes.

{% include note.html content="**We recommend all users of the 3.8 line to update to this release, especially when
deploying Ditto with the Helm chart in its default configuration (`global.jwtOnly: false`).** See the *Security
fixes* section below for the exact preconditions and for workarounds if you cannot upgrade immediately. One further
vulnerability affecting the 3.8 line, CVE-2026-132039, is only fixed in the 3.9 line — users who can upgrade to the
3.9 line should prefer 3.9.9 or later." %}

## Changelog

### Security fixes

The vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes were
merged from private forks, there are no public pull requests for them; the advisories are linked instead and carry
the full details, including the affected code, proofs of concept and the available workarounds.

| Severity     | CVE                                                                                                                           | Affected component                  | Fixed in 3.8.15 |
|--------------|-------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|-----------------|
| **Critical** | [CVE-2026-120753](#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter) | gateway, Helm chart                 | yes             |
| **High**     | [CVE-2026-129699](#security-fix-for-cve-2026-129699--merge-patch-deletions-and-empty-objects-bypass-write-authorization)      | things                              | yes             |
| **High**     | [CVE-2026-129700](#security-fix-for-cve-2026-129700--thing-fields-exposed-via-condition-and-live-channel-condition)           | things                              | yes             |
| **Low**      | [CVE-2026-129702](#security-fix-for-cve-2026-129702--revokes-ignored-in-multi-permission-and-partial-permission-checks)       | policies, things (policy enforcers) | yes             |
| **High**     | [CVE-2026-132039](#cve-2026-132039--thing-events-disclose-field-values-the-subscriber-may-not-read-not-fixed-in-38)           | things, gateway, connectivity       | **no**, 3.9.9   |

#### Security fix for CVE-2026-120753 — Subject impersonation via the `x-ditto-pre-authenticated` query parameter

Advisory [GHSA-chwq-wg27-qvfr](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-chwq-wg27-qvfr) /
CVE [CVE-2026-120753](https://nvd.nist.gov/vuln/detail/CVE-2026-120753)

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

On the 3.8 line, all versions up to and including 3.8.14 are affected (the issue exists since 1.1.0) when
pre-authentication is enabled and at least one request path reaches the gateway without a non-empty, proxy-set
`x-ditto-pre-authenticated` header — in particular **all installations of the Ditto Helm chart with default
settings**. Not affected are deployments with pre-authentication disabled (the gateway's built-in default), Helm
chart installations with `global.jwtOnly: true`, and the `deployment/docker` Docker Compose setup.

The gateway now reads the pre-authenticated subject **only** from the `x-ditto-pre-authenticated` HTTP header. A
request carrying an invalid Bearer token together with the query parameter is rejected with `400` (`jwt:invalid`).
The Helm chart additionally anchors the nginx `Bearer` detection to the start of the `Authorization` header.

If you cannot upgrade immediately, disable pre-authentication in the gateway (`ENABLE_PRE_AUTHENTICATION=false`),
or, when using the Helm chart, configure `global.jwtOnly: true` (keeping
`gateway.config.authentication.enablePreAuthentication: false`).

#### Security fix for CVE-2026-129699 — Merge patch deletions and empty objects bypass WRITE authorization

Advisory [GHSA-5hh9-qp4v-6f56](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-5hh9-qp4v-6f56) /
CVE [CVE-2026-129699](https://nvd.nist.gov/vuln/detail/CVE-2026-129699)

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

On the 3.8 line, all versions up to and including 3.8.14 are affected (since 2.0.0, the regex deletion variant since
3.5.5). The things service now requires `WRITE` permission on every field which a merge patch deletes or sets to an
empty object.

There is no configuration-based workaround. For untrusted subjects with partial `WRITE` permission, operators can
reject HTTP `PATCH` requests to the Things API in their reverse proxy; this does not cover `merge` commands sent via
the WebSocket API or connections.

#### Security fix for CVE-2026-129700 — Thing fields exposed via `condition` and `live-channel-condition`

Advisory [GHSA-x88m-jq5c-jpcq](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-x88m-jq5c-jpcq) /
CVE [CVE-2026-129700](https://nvd.nist.gov/vuln/detail/CVE-2026-129700)

Severity **high** (CVSS 4.0 score 7.1,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N`).

[Conditions](basic-conditional-requests.html) given via the `condition` and
[`live-channel-condition`](basic-conditional-requests.html#live-channel-condition) headers are evaluated against the
complete, unfiltered Thing, so the requester must have `READ` permission on all fields they reference. This was not
ensured in four cases ([CWE-863](https://cwe.mitre.org/data/definitions/863.html),
[CWE-200](https://cwe.mitre.org/data/definitions/200.html)):

1. when both headers were present on a Thing query command, `READ` was only checked for the `condition` (since 3.0.0),
2. conditions on [WoT Thing Description](basic-wot-integration.html) requests (`Accept: application/td+json`) were
   evaluated without any policy authorization, as the 3.8 line treats Thing Descriptions as public information
   (since 2.4.0),
3. the `condition` of live messages, claim messages and live events was evaluated without checking `READ` on the
   referenced fields (since 3.1.0),
4. smart channel twin responses (when the `live-channel-condition` did not match) were returned **without filtering
   them by the policy** (since 3.0.0).

The result of an unchecked condition was exposed to the requester (e.g. `412 Precondition Failed` versus a successful
response, or the `live-channel-condition-matched` response header), allowing an authenticated user to recover field
values they may not read by testing predicates one request at a time. In case 4 the field values were directly
contained in the response. On the 3.8 line, all versions up to and including 3.8.14 are affected **in the default
configuration**: via case 2, **any authenticated user knowing the ID of a Thing can read all of its fields, without
any permission on it**.

Now, `READ` on the fields of the `live-channel-condition` is checked independently of the `condition`, `READ` on the
fields of the `condition` of live messages and live events is checked, smart channel twin responses are filtered by
the policy, and conditions on WoT Thing Description requests and on claim messages are rejected with
`400 Bad Request` (`header.invalid`).

There is no complete configuration-based workaround. Disabling the WoT integration (environment variable
`DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=false`, Helm value `global.featureFlags.wotIntegrationEnabled: false`)
prevents case 2, at the cost of the WoT features. In a reverse proxy, operators can reject HTTP requests carrying a
`live-channel-condition` header, and requests to the message endpoints (`/inbox`, `/outbox`) carrying a `condition`
header; this does not cover the WebSocket API or connections.

#### Security fix for CVE-2026-129702 — Revokes ignored in multi-permission and partial permission checks

Advisory [GHSA-73pw-g56w-95h8](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-73pw-g56w-95h8) /
CVE [CVE-2026-129702](https://nvd.nist.gov/vuln/detail/CVE-2026-129702)

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

On the 3.8 line, all versions up to and including 3.8.14 are affected. Both enforcers now evaluate grants and
revokes per permission and let a revoke on a sub-resource cancel the grants of all subjects of the authorization
context on that sub-resource.

If you cannot upgrade immediately, avoid policies which grant a permission to one subject and revoke the same
permission on the same resource from another subject of the same authorization context, and request only one
permission per `checkPermissions` entry.

#### CVE-2026-132039 — Thing events disclose field values the subscriber may not read (not fixed in 3.8)

Advisory [GHSA-3fx4-8jrp-7pqm](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-3fx4-8jrp-7pqm) /
CVE [CVE-2026-132039](https://nvd.nist.gov/vuln/detail/CVE-2026-132039)

Severity **high** (CVSS 4.0 score 7.1,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:H/VI:N/VA:N/SC:N/SI:N/SA:N`).

This vulnerability also affects the 3.8 line, but is **only fixed in [3.9.9](release_notes_399.html)** — the fix
builds on the per-subscriber event filtering introduced with 3.9.0 and is not part of 3.8.15.

On the 3.8 line, Ditto delivers Thing events via WebSocket, [SSE](httpapi-sse.html) and connections containing field
values which the policy does not allow the subscriber to read ([CWE-863](https://cwe.mitre.org/data/definitions/863.html))
to:

1. **subscribers with several subjects** (e.g. a user and a group subject extracted from the same JWT, or a
   connection target with several authorization subjects): a `READ` revoke for one subject is not applied when
   another subject of the same authorization context is granted `READ`,
2. **receivers of live commands**: a live command is delivered with its whole payload to every subscriber with
   `READ` on the command's path, even if `READ` is revoked for a part of the payload.

Retrieving and searching Things, and the authorization of modifying commands and live messages, are not affected.

Users who rely on such policies should upgrade to 3.9.9 or later. Otherwise, do not rely on a `READ` revoke for one
subject to restrict what another subject of the same authorization context may read — add the revoke to the policy
entry of every subject granted `READ` on the resource — and do not send live commands containing fields whose `READ`
is revoked for subjects subscribed to live commands of the Thing.


### Helm Chart

The Helm chart was updated to version `3.8.15`, bumping the Ditto `appVersion` to `3.8.15`.

As part of the [security fix for CVE-2026-120753](#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter),
the nginx `Bearer` detection is anchored to the start of the `Authorization` header, and `values.yaml` documents that
`global.jwtOnly: false` implicitly enables pre-authentication in the gateway. Installations with the default
`global.jwtOnly: false` should update to this chart version. There are no other chart changes.


## Migration notes

There are no migration steps required for the Ditto data model or APIs. However, the security fixes reject requests
which were previously accepted:

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
