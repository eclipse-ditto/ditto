---
title: Release notes 3.8.13
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.13 of Eclipse Ditto, released on 01.09.2026"
permalink: release_notes_3813.html
---

This is a **security bugfix release** for the 3.8 line, no new features since
[3.8.12](release_notes_3812.html) were added. It contains **only** the fixes for the two security
vulnerabilities also fixed in 3.9.7 — a JSON template injection in the connectivity
service's `ImplicitThingCreation` payload mapper and a Server-Side Request Forgery when fetching WoT
ThingModels in the things service — and no other changes.

{% include note.html content="**We recommend all users of the 3.8 line to update to this release.** Both
vulnerabilities are reachable by authenticated parties in default configurations — see the *Security fixes*
section below for the exact preconditions and for workarounds if you cannot upgrade immediately. Users who can
upgrade to the 3.9 line should prefer 3.9.7 or later." %}

## Changelog

### Security fixes

Both vulnerabilities were reported and fixed privately, and are disclosed through the
[GitHub Security Advisory](https://github.com/eclipse-ditto/ditto/security/advisories) process. As the fixes
were merged from private forks, there are no public pull requests for them; the advisories are linked instead
and carry the full details, including the affected code, the fix and the available workarounds.

#### Security fix for CVE-2026-82958 — JSON template injection in the `ImplicitThingCreation` mapper

Advisory [GHSA-cgfq-3fv9-5c44](https://github.com/eclipse-ditto/ditto/security/advisories/GHSA-cgfq-3fv9-5c44) /
CVE [CVE-2026-82958](https://nvd.nist.gov/vuln/detail/CVE-2026-82958)

Severity **high** (CVSS 4.0 score 7.6,
`CVSS:4.0/AV:N/AC:L/AT:P/PR:L/UI:N/VC:H/VI:H/VA:N/SC:N/SI:N/SA:N`).

The connectivity service's `ImplicitThingCreationMessageMapper` built its `CreateThing` command by
substituting placeholder values (e.g. `{%raw%}{{ header:device_id }}{%endraw%}`) resolved from inbound message
headers into the configured JSON *thing template* as raw, un-escaped strings, and only then parsed the result
as JSON. The placeholder engine performs no JSON escaping and is unaware of the surrounding JSON string
context, so a resolved value containing a `"` character could break out of its string and inject additional
JSON structure — most importantly an inline `_policy` object, which overrides the administrator-configured
`policyId` ([CWE-1336](https://cwe.mitre.org/data/definitions/1336.html)). `_copyPolicyFrom` and `policyId`
could be injected the same way.

An attacker able to publish on such a connection could therefore assign an arbitrary access-control policy to
the newly created digital twin — granting themselves full read/write access and potentially revoking the
legitimate owner's access — without any administrator interaction.

On the 3.8 line, all versions up to and including 3.8.12 are affected, but only deployments that use the
(non-default) `ImplicitThingCreation` payload mapper **and** whose thing template reflects a header value the
publishing device can control (e.g. an MQTT 5 user property, an AMQP 1.0 application property or a Kafka
record header). The policy-override outcome additionally requires the connection's authorization subjects to
be permitted to create policy entities, which is the default. Deployments whose template only reflects values
asserted by trusted middleware (e.g. an Eclipse Hono provided `device_id` derived from device
authentication), or that do not use this mapper at all, are not exploitable through a device.

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
CVE [CVE-2026-84175](https://nvd.nist.gov/vuln/detail/CVE-2026-84175)

Severity **medium** (CVSS 4.0 score 5.3,
`CVSS:4.0/AV:N/AC:L/AT:N/PR:L/UI:N/VC:L/VI:N/VA:L/SC:L/SI:N/SA:N`).

When a Thing or Feature `definition` contains an `http(s)://` URL, the things service fetches the referenced
[WoT ThingModel](basic-wot-integration.html) from that URL. Affected versions performed **no validation of the
target host**: there was no allow-list, no blocking of loopback / link-local / site-local / unique-local
addresses, and HTTP redirects were followed without re-validating the redirect target and without a hop limit
([CWE-918](https://cwe.mitre.org/data/definitions/918.html)).

Because the fetch originates inside the deployment's network, any authenticated user who may create a Thing —
or who holds `WRITE` permission on an existing Thing — could make the things service issue arbitrary HTTP
`GET` requests to internal endpoints, such as cloud instance-metadata services (`169.254.169.254`,
`fd00:ec2::254`), the Kubernetes API server or internal admin interfaces. The error returned to the caller
differs depending on the target's behaviour (HTTP 421 `wot:tm.notfound` vs. HTTP 400 `wot:tm.invalid`), which
could be used as an oracle to enumerate internal services, and where an internal target returned a JSON
object parseable as a ThingModel, parts of it could be incorporated into the created Thing and returned to the
caller. A single API call could be amplified into many internal requests, because `tm:extends`, `tm:ref` and
`tm:submodel` links of a fetched ThingModel are resolved and fetched as well. Secondarily, redirects were
followed recursively without a hop limit ([CWE-674](https://cwe.mitre.org/data/definitions/674.html)), so a
server redirecting to itself could occupy a WoT dispatcher thread for the duration of the 10 second fetch
timeout.

The affected endpoints are `POST /api/2/things`, `PUT /api/2/things/{thingId}`,
`PUT /api/2/things/{thingId}/definition`, `PUT /api/2/things/{thingId}/features/{featureId}/definition` and
`POST /api/2/things/{thingId}/migrateDefinition`. All 3.8.x versions up to and including 3.8.12
are affected in the **default configuration**, because the WoT integration feature toggle is enabled by
default since [3.0.0](release_notes_300.html).

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
Operators are advised to combine this check with network-level egress controls — see
[Restricting WoT ThingModel fetching](installation-operating.html#restricting-wot-thingmodel-fetching) in the
operating documentation.

If you cannot upgrade immediately, apply network-level egress controls (e.g. Kubernetes `NetworkPolicies` or
firewall rules) preventing the things service from reaching internal address ranges and the cloud
instance-metadata endpoint — recommended as defense in depth even after upgrading — restrict entity creation
via [`ditto.entity-creation`](installation-operating.html#restricting-entity-creation) including its
`thing-definitions` allow-list (this covers the creation path only, **not** `migrateDefinition` or the
definition-modification endpoints), grant `WRITE` permission on Things narrowly, or disable the WoT integration entirely via
`DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=false` where it is not used.

### Helm Chart

The Helm chart was updated to version `3.8.13`, bumping the Ditto `appVersion` to `3.8.13`.

It exposes the new WoT ThingModel host validation of the SSRF fix under `things.config.wot.hostValidation`
(`enabled`, `allowedHostnames`, `blockedHostnames`, `blockedSubnets`, `blockedHostRegex`, `maxRedirects`).
There are no other chart changes.

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
