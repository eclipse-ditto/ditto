---
title: Release notes 3.8.16
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.16 of Eclipse Ditto, released on 25.11.2026"
permalink: release_notes_3816.html
---

This is a **security bugfix release** for the 3.8 line, no new features since
[3.8.15](release_notes_3815.html) were added. It contains **only** the fixes for two **critical** security
vulnerabilities — an authentication bypass in deployments using the nginx configurations or the Ingress shipped with
Ditto, and a sandbox escape of the JavaScript payload mapper — but no other changes.

{% include note.html content="**We recommend all users of the 3.8 line to update to this release, especially when
deploying Ditto with the Helm chart in its default configuration (`global.jwtOnly: false`) or with the
`deployment/docker` Docker Compose setup.** See the *Security fixes* section below for the exact preconditions and for
workarounds if you cannot upgrade immediately." %}

## Changelog

### Security fixes

The vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes were
merged from private forks, there are no public pull requests for them; the advisories are linked instead and carry
the full details, including the affected code, proofs of concept and the available workarounds.

| Severity     | CVE                                                                                                                                | Affected component                          | Fixed in 3.8.16 |
|--------------|------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|-----------------|
| **Critical** | [CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header) | gateway, Helm chart, nginx deployment files | yes             |
| **Critical** | [CVE-2026-135203](#security-fix-for-cve-2026-135203--code-execution-via-the-javascript-payload-mapper)                             | connectivity                                | yes             |

#### Security fix for CVE-2026-135150 — Subject impersonation via a client-supplied `x-ditto-pre-authenticated` header

Advisory [GHSA-f779-cv8j-4p59](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-f779-cv8j-4p59) /
CVE [CVE-2026-135150](https://nvd.nist.gov/vuln/detail/CVE-2026-135150)

Severity **critical** (CVSS 4.0 score 9.1,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:N/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`).

This is a bypass of the fix for
[CVE-2026-120753](release_notes_3815.html#security-fix-for-cve-2026-120753--subject-impersonation-via-the-x-ditto-pre-authenticated-query-parameter)
contained in 3.8.15, with the same impact.

The nginx configurations shipped with Ditto (Helm chart, `deployment/docker` Docker Compose setup, Kubernetes and
OpenShift deployment files) contain locations which forward requests to the gateway **without nginx authentication and
without overwriting or clearing the `x-ditto-pre-authenticated` header** — most notably `/api/2/connections`, which is
protected by the gateway's devops authentication instead. nginx selects the location on the *normalized* request path,
but forwards the *raw* path to the gateway, which resolves dot segments (`/..`) differently, as it does not treat an
encoded slash (`%2f`) as a path separator. With a crafted path, a request is routed by nginx through such an
unauthenticated location, but by the gateway to the regular Things and Policies HTTP API, where the client-supplied
`x-ditto-pre-authenticated` header is trusted ([CWE-290](https://cwe.mitre.org/data/definitions/290.html),
[CWE-436](https://cwe.mitre.org/data/definitions/436.html)).

When [pre-authentication](installation-operating.html#pre-authentication) is enabled — the default of the Ditto Helm
chart (`global.jwtOnly: false`) and of the Docker Compose, Kubernetes and OpenShift deployment files — an
**unauthenticated remote attacker** could therefore impersonate arbitrary policy subjects and read, modify and delete
all Things and Policies granted to them.

In addition, the optional ingress-nginx based **Ingress** of the Helm chart (`ingress.enabled: true`, since chart
3.3.0) applies basic auth on the WebSocket endpoint `/ws`, but did not set the `x-ditto-pre-authenticated` header
there, unlike on `/api`. With pre-authentication enabled, **any user with valid basic auth credentials** could open
the WebSocket endpoint with a client-supplied header and act as any other subject.

On the 3.8 line, all versions up to and including 3.8.15 are affected (since 1.1.0) when pre-authentication is enabled
and one of the nginx configurations shipped with Ditto, a configuration derived from it, or the Ingress of the Helm
chart is used. Not affected are deployments with pre-authentication disabled (the gateway's built-in default) and
Helm chart installations with `global.jwtOnly: true` (keeping
`gateway.config.authentication.enablePreAuthentication: false`).

This release fixes the vulnerability in several places:

* all nginx configurations shipped with Ditto clear the `x-ditto-pre-authenticated` header in every location which
  forwards to the gateway without setting it from a verified identity (`/api/2/connections`, `/devops`, `/health`,
  `/status`, `/stats`),
* the gateway rejects requests whose raw path contains dot segments (`.` or `..`, also percent-encoded or delimited
  by a percent-encoded slash `%2f`) with `400 Bad Request`, so a proxy and the gateway can no longer resolve such a
  path to different resources,
* the `ws` Ingress of the Helm chart sets the `x-ditto-pre-authenticated` header from the basic auth user, like the
  `api` Ingress,
* the [documentation](installation-operating.html#pre-authentication) states that the reverse proxy must set or
  clear the header in every location which forwards requests to the gateway.

If you cannot upgrade immediately, disable pre-authentication in the gateway (`ENABLE_PRE_AUTHENTICATION=false`), or,
when using the Helm chart, configure `global.jwtOnly: true` (keeping
`gateway.config.authentication.enablePreAuthentication: false`). Alternatively, add
`proxy_set_header x-ditto-pre-authenticated "";` to **every** nginx location which forwards to the gateway and does
not set the header itself — it has to be added to each location, as a location defining its own `proxy_set_header`
directives does not inherit the server-level ones. With the Ingress of the Helm chart enabled, add a
`nginx.ingress.kubernetes.io/configuration-snippet` annotation with
`proxy_set_header x-ditto-pre-authenticated "nginx:$remote_user";` to `ingress.ws.annotations`.

#### Security fix for CVE-2026-135203 — Code execution via the JavaScript payload mapper

Advisory [GHSA-9mpq-m32x-4q7p](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-9mpq-m32x-4q7p) /
CVE [CVE-2026-135203](https://nvd.nist.gov/vuln/detail/CVE-2026-135203)

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

On the 3.8 line, all versions up to and including 3.8.15 are affected (since 0.3.0-M1, when the JavaScript payload
mapper was introduced). The connectivity service now installs a deny-by-default `ClassShutter` for all JavaScript
mapping executions, so that no Java class or object is visible to mapping scripts — independent of the
`allowUnsafeStandardObjects` setting.

If you cannot upgrade immediately, disable the JavaScript payload mapper by removing
`org.eclipse.ditto.connectivity.service.mapping.javascript.JavaScriptMessageMapperRhino` from
`ditto.connectivity.mapping.message-mapper-provider.extension-config.message-mappers` in the connectivity
configuration (connections using JavaScript payload mapping can then no longer be opened), or restrict access to the
DevOps realm to fully trusted administrators and review the scripts of existing connections.


### Helm Chart

The Helm chart was updated to version `3.8.16`, bumping the Ditto `appVersion` to `3.8.16`.

As part of the
[security fix for CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header),
the bundled nginx clears the `x-ditto-pre-authenticated` header in all locations forwarding to the gateway without
authentication, and the default annotations of the `ws` Ingress (`ingress.ws.annotations`) set the header from the
basic auth user. `values.yaml` documents that the Ingress and any proxy in front of the gateway must set or clear the
header on every path. All installations with the default `global.jwtOnly: false` should update to this chart version.


## Migration notes

There are no migration steps required for the Ditto data model or APIs. However, the security fixes reject requests
and scripts which were previously accepted:

* **Dot segments in request paths**
  ([CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header)):
  the gateway rejects requests whose raw path contains `.` or `..` segments (also percent-encoded, or delimited by
  `%2f`) with `400 Bad Request`. Clients must send normalized paths; path segments consisting only of `.` or `..`
  (e.g. an attribute or Feature property named `..` addressed via the HTTP API) can no longer be used.
* **`x-ditto-pre-authenticated` in reverse proxies**
  ([CVE-2026-135150](#security-fix-for-cve-2026-135150--subject-impersonation-via-a-client-supplied-x-ditto-pre-authenticated-header)):
  operators using their own nginx configuration derived from the ones shipped with Ditto, or their own ingress
  configuration, must set or clear the header in every location forwarding to the gateway. When overriding the
  `nginx.ingress.kubernetes.io/configuration-snippet` annotation of `ingress.ws.annotations` in the Helm chart, keep
  the `proxy_set_header x-ditto-pre-authenticated "nginx:$remote_user";` line.
* **JavaScript payload mapping**
  ([CVE-2026-135203](#security-fix-for-cve-2026-135203--code-execution-via-the-javascript-payload-mapper)):
  mapping scripts can no longer access any Java class or object, also with `allowUnsafeStandardObjects` enabled
  (`CONNECTIVITY_MESSAGE_MAPPING_JS_ALLOW_UNSAFE_STANDARD_OBJECTS=true`), where Java packages such as `Packages.java`
  can still be referenced, but no class can be loaded from them. Scripts relying on Java access in this debugging mode
  must be rewritten in plain JavaScript.
