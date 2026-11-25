---
title: Release notes 3.9.10
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.10 of Eclipse Ditto, released on 25.11.2026"
permalink: release_notes_3910.html
---

This is a **security bugfix release**, no new features since [3.9.9](release_notes_399.html) were added.
It fixes two **critical** security vulnerabilities — an authentication bypass in deployments using the nginx
configurations or the Ingress shipped with Ditto, and a sandbox escape of the JavaScript payload mapper.
It also brings back the lower heap usage of values parsed from CBOR, which regressed in 3.9.9, and contains a few
bugfixes in Ditto's JSON library and the connectivity service.

{% include note.html content="**We recommend all users to update to this release, especially when deploying Ditto
with the Helm chart in its default configuration (`global.jwtOnly: false`) or with the `deployment/docker` Docker
Compose setup.** See the *Security fixes* section below for the exact preconditions and for workarounds if you cannot
upgrade immediately, and the *Migration notes* for requests and Helm configurations which are rejected after the
update." %}

## Changelog

Compared to the latest release [3.9.9](release_notes_399.html), the following security fixes, changes and
bugfixes were added.


### Security fixes

The vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes were
merged from private forks, there are no public pull requests for them; the merge commits of the fixes are linked
directly instead. The linked advisories carry the full details, including the affected code, proofs of concept and
the available workarounds.

| Severity     | CVE                                                                                                                                | Affected component                          |
|--------------|------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| **Critical** | [CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header) | gateway, Helm chart, nginx deployment files |
| **Critical** | [CVE-2026-135203](#security-fix-for-cve-2026-135203--code-execution-via-the-javascript-payload-mapper)                             | connectivity                                |

#### Security fix for CVE-2026-135150 — Subject impersonation via a client-supplied `x-ditto-pre-authenticated` header

Advisory [GHSA-f779-cv8j-4p59](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-f779-cv8j-4p59) /
CVE [CVE-2026-135150](https://nvd.nist.gov/vuln/detail/CVE-2026-135150) /
Fix commit [`9dce5303bb`](https://github.com/eclipse-ditto/ditto/commit/9dce5303bb00e85cb848c24130183048ccd03ace)

Severity **critical** (CVSS 4.0 score 9.1,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`).

This is a bypass of the fix for
[CVE-2026-120753](release_notes_399.html#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter)
contained in 3.9.9, with the same impact.

The nginx configurations shipped with Ditto (Helm chart, `deployment/docker` Docker Compose setup, Kubernetes and
OpenShift deployment files) contain locations which forward requests to the gateway **without nginx authentication and
without overwriting or clearing the `x-ditto-pre-authenticated` header** — most notably `/api/2/connections`, which is
protected by the gateway's devops authentication instead. nginx selects the location on the *normalized* request path,
but forwards the *raw* path to the gateway, which resolves dot segments (`/..`) differently, as it does not treat an
encoded slash (`%2f`) as a path separator. With a crafted path, a request is routed by nginx through such an
unauthenticated location, but by the gateway to the regular Things and Policies HTTP API, where the client-supplied
`x-ditto-pre-authenticated` header is trusted ([CWE-290](https://cwe.mitre.org/data/definitions/290.html),
[CWE-436](https://cwe.mitre.org/data/definitions/436.html)).

When [pre-authentication](operating-authentication.html#pre-authentication) is enabled — the default of the Ditto Helm
chart (`global.jwtOnly: false`) and of the Docker Compose, Kubernetes and OpenShift deployment files — an
**unauthenticated remote attacker** could therefore impersonate arbitrary policy subjects and read, modify and delete
all Things and Policies granted to them.

In addition, the optional **Ingress** of the Helm chart (`ingress.enabled: true`, since chart 4.0.0) routes `/api`,
`/ws` and `/devops` directly to the gateway, bypassing the bundled nginx. With pre-authentication enabled, nothing
set or removed the `x-ditto-pre-authenticated` header there, unless the operator configured the ingress controller to
do so, so **no path manipulation was needed at all**.

Affected are Ditto versions **1.1.0 through 3.9.9** (Helm chart up to 4.9.0) with pre-authentication enabled, when
one of the nginx configurations shipped with Ditto, a configuration derived from it, or the Ingress of the Helm chart
is used. Not affected are deployments with pre-authentication disabled (the gateway's built-in default) and Helm chart
installations with `global.jwtOnly: true` (keeping `gateway.config.authentication.enablePreAuthentication: false`),
also with the Ingress enabled.

This release fixes the vulnerability in several places:

* all nginx configurations shipped with Ditto clear the `x-ditto-pre-authenticated` header in every location which
  forwards to the gateway without setting it from a verified identity (`/api/2/connections`, `/devops`, `/health`,
  `/status`, `/stats`),
* the gateway rejects requests whose raw path contains dot segments (`.` or `..`, also percent-encoded or delimited
  by a percent-encoded slash `%2f`) with `400 Bad Request`, so a proxy and the gateway can no longer resolve such a
  path to different resources,
* rendering the Helm chart fails if an Ingress routing to the gateway is enabled while pre-authentication is enabled,
  unless the new `ingress.preAuthenticationHeaderSanitized: true` confirms that the ingress controller sets or removes
  the header on every path (see the [migration notes](#migration-notes)),
* the [authentication](operating-authentication.html#pre-authentication),
  [installation](installation-operating.html#pre-authentication) and
  [security hardening](operating-security-hardening.html) documentation state that the reverse proxy or ingress
  controller must set or clear the header in every location which forwards requests to the gateway.

If you cannot upgrade immediately, disable pre-authentication in the gateway (`ENABLE_PRE_AUTHENTICATION=false`), or,
when using the Helm chart, configure `global.jwtOnly: true` (keeping
`gateway.config.authentication.enablePreAuthentication: false`). Alternatively, add
`proxy_set_header x-ditto-pre-authenticated "";` to **every** nginx location which forwards to the gateway and does
not set the header itself — it has to be added to each location, as a location defining its own `proxy_set_header`
directives does not inherit the server-level ones — and configure the ingress controller of an enabled Ingress to set
the header from a verified identity or remove it on every path.

#### Security fix for CVE-2026-135203 — Code execution via the JavaScript payload mapper

Advisory [GHSA-9mpq-m32x-4q7p](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-9mpq-m32x-4q7p) /
CVE [CVE-2026-135203](https://nvd.nist.gov/vuln/detail/CVE-2026-135203) /
Fix commit [`3dfb14cbfe`](https://github.com/eclipse-ditto/ditto/commit/3dfb14cbfea00f356552c949b35d4c63d770d9ae)

Severity **critical** (CVSS 4.0 score 9.4,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:H/UI:N/VC:H/VI:H/VA:H/SC:H/SI:H/SA:H`).

The [JavaScript payload mapper](connectivity-mapping.html#javascript-mapping-engine) of connections runs the
`incomingScript` and `outgoingScript` of a connection in a sandboxed Rhino JavaScript engine, which is documented to
give scripts no access to Java packages or classes. This restriction did not hold: the engine was configured without
a `ClassShutter`, so when a script caught an error raised by the engine itself, the caught JavaScript error exposed
the underlying Java exception object. Through it, a script could use Java reflection to load and invoke **arbitrary
Java classes and methods** in the connectivity service ([CWE-693](https://cwe.mitre.org/data/definitions/693.html),
[CWE-94](https://cwe.mitre.org/data/definitions/94.html)).

A user who may create or modify connections — in the default configuration, a user of the DevOps realm — could
therefore execute arbitrary code in the connectivity service, with access to its configuration (including database
credentials and the credentials of all connections), to the Pekko cluster of the other Ditto services and to the
network of the deployment. Deployments which delegate the management of connections or mapping scripts to less
trusted users are affected the most.

Affected are Ditto versions **0.3.0-M1 through 3.9.9**, since the JavaScript payload mapper was introduced. The
connectivity service now installs a deny-by-default `ClassShutter` for all JavaScript mapping executions, so that no
Java class or object is visible to mapping scripts — independent of the `allowUnsafeStandardObjects` setting.

If you cannot upgrade immediately, disable the JavaScript payload mapper by removing
`org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperRhino` from
`ditto.connectivity.mapping.message-mapper-provider.extension-config.message-mappers` in the connectivity
configuration (connections using JavaScript payload mapping can then no longer be opened), or restrict access to the
DevOps realm to fully trusted administrators and review the scripts of existing connections.


### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.10).

#### Keep values parsed from CBOR releasable and compact

PR [#2664](https://github.com/eclipse-ditto/ditto/pull/2664) fixes a heap and CPU regression of 3.9.9: since 3.9.9,
JSON values parsed from CBOR (e.g. Things received from other Ditto services and cached) held their fields strongly,
so the GC could no longer release them under heap pressure. In a things-search production instance this led to
~45% more CPU usage at unchanged load, spent in G1 garbage collection. Values parsed from CBOR now reference a slice
of a single copy of the received message and can be released by the GC again, without the per-level byte copies
3.9.9 removed. Parsed signals are forwarded without encoding them again, and the fields of JSON objects and the values
of JSON arrays are held in more compact structures. A cached Thing whose fields are kept now retains less heap than
with 3.9.8 and 3.9.9, and once its fields are released about as little as with 3.9.8.

#### Compare JSON objects structurally instead of rendering them

PR [#2665](https://github.com/eclipse-ditto/ditto/pull/2665) makes the comparison of two different JSON objects
compare their fields structurally, instead of rendering both objects to JSON strings and keeping these strings.
This happened e.g. on every update of a Thing replacing a JSON object, such as `ModifyFeatureProperties`: in a
benchmark of Thing updates with 10% `ModifyFeatureProperties`, the updates need ~40% less CPU time and less than half
of the allocations, and the Thing states retain about a third of the heap afterwards.


### Bugfixes

#### Reply with a policy error to rejected inbound policy commands

Issue [#2657](https://github.com/eclipse-ditto/ditto/issues/2657) / PR
[#2659](https://github.com/eclipse-ditto/ditto/pull/2659) fixes the error response for policy commands received via a
connection and rejected because of invalid headers (e.g. `timeout=0` together with `response-required=true`): they
were answered with a Thing error response, with the Policy ID interpreted as Thing ID. They are now answered with a
Policy error response.

#### Give JSON object wrappers the hash code of the wrapped JSON

Issue [#2668](https://github.com/eclipse-ditto/ditto/issues/2668) / PR
[#2669](https://github.com/eclipse-ditto/ditto/pull/2669) fixes JSON objects containing `Attributes`,
`FeatureProperties` or `Metadata` being equal to JSON objects containing the same plain JSON, but having a different
hash code, which broke their use in hash-based collections such as `HashMap` keys and `Set`s.

#### Return an empty `Optional` for the leaf of the empty JSON pointer

Issue [#2656](https://github.com/eclipse-ditto/ditto/issues/2656) / PR
[#2667](https://github.com/eclipse-ditto/ditto/pull/2667) fixes `JsonPointer.getLeaf()` throwing an
`IndexOutOfBoundsException` for the empty pointer; it now returns an empty `Optional`, like `get()` for any level out
of bounds.

#### Reject JSON numbers which do not fit in a double

PR [#2670](https://github.com/eclipse-ditto/ditto/pull/2670) fixes decimal numbers outside the range of a double, e.g.
`1e400`, being accepted as `Infinity`, which was written as invalid JSON to responses, events and the persisted Thing.
Such numbers are now rejected with `400` (`json.invalid`), like integers outside the `long` range already were.
Values stored before are loaded as before.


### Helm Chart

The Helm chart was updated to version `4.10.0`, bumping the Ditto `appVersion` to `3.9.10`.

As part of the
[security fix for CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header),
the bundled nginx clears the `x-ditto-pre-authenticated` header in all locations forwarding to the gateway without
authentication, and rendering the chart fails if an Ingress routing to the gateway is enabled while
pre-authentication is enabled, unless the new `ingress.preAuthenticationHeaderSanitized` is set to `true`. Although
this requires user action on upgrade for such installations, it is intentionally released as a minor chart version,
as an exception for this security fix.

The Docker images of this release are based on the JDK `25.0.5.1`, which fixes the JDK vulnerability
[CVE-2026-114938](https://nvd.nist.gov/vuln/detail/CVE-2026-114938) ("Java4Shell"). The `-XX:-UseBASE64Intrinsics`
JVM option added to the default `global.jvmOptions` with chart 4.9.0 as mitigation is therefore no longer needed for
the Ditto images, but kept for now; it only makes Base64 encoding and decoding slower.

The full, itemized list of chart changes lives in the chart's own
[CHANGELOG](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).


## Migration notes

There are no migration steps required for the Ditto data model or APIs. However, the security fixes and bugfixes
reject requests, scripts and Helm configurations which were previously accepted:

* **Helm chart with Ingress and pre-authentication**
  ([CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header)):
  rendering the chart fails if `ingress.enabled` is `true` while pre-authentication is enabled (`global.jwtOnly: false`
  or `gateway.config.authentication.enablePreAuthentication: true`). Either set `global.jwtOnly: true` (recommended),
  or make sure the ingress controller sets the `x-ditto-pre-authenticated` header from a verified identity or removes
  it on every path routed to the gateway, and confirm that with `ingress.preAuthenticationHeaderSanitized: true`.
* **Dot segments in request paths**
  ([CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header)):
  the gateway rejects requests whose raw path contains `.` or `..` segments (also percent-encoded, or delimited by
  `%2f`) with `400 Bad Request`. Clients must send normalized paths; path segments consisting only of `.` or `..`
  (e.g. an attribute or Feature property named `..` addressed via the HTTP API) can no longer be used.
* **`x-ditto-pre-authenticated` in reverse proxies**
  ([CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header)):
  operators using their own nginx configuration derived from the ones shipped with Ditto, or their own ingress
  configuration, must set or clear the header in every location forwarding to the gateway.
* **JavaScript payload mapping**
  ([CVE-2026-135203](#security-fix-for-cve-2026-135203--code-execution-via-the-javascript-payload-mapper)):
  mapping scripts can no longer access any Java class or object, also with `allowUnsafeStandardObjects` enabled
  (`CONNECTIVITY_MESSAGE_MAPPING_JS_ALLOW_UNSAFE_STANDARD_OBJECTS=true`), where Java packages such as `Packages.java`
  can still be referenced, but no class can be loaded from them. Scripts relying on Java access in this debugging mode
  must be rewritten in plain JavaScript.
* **JSON numbers outside the double range** (PR [#2670](https://github.com/eclipse-ditto/ditto/pull/2670)): JSON
  containing decimal numbers such as `1e400` is rejected with `400` (`json.invalid`).
