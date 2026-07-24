---
title: Release notes 3.9.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.5 of Eclipse Ditto, released on 24.07.2026"
permalink: release_notes_395.html
---

This is a bugfix release, no new user-facing API features since [3.9.4](release_notes_394.html) were added.
It ships a further reduction of the CPU spent on `DittoHeaders` handling, together with fixes for
cluster serialization, rolling-update stability and connectivity error reporting.

## Changelog

Compared to the latest release [3.9.4](release_notes_394.html), the following changes and bugfixes were added.

### Changes

#### Memoize JSON header-value parsing and skip re-validation of trusted headers

PR [#2494](https://github.com/eclipse-ditto/ditto/pull/2494) reduces the CPU spent on parsing and
validating `DittoHeaders`. Every signal carries headers, and `DittoHeaders` stored each value as a `String`,
re-parsing the JSON-typed values (e.g. `authorization-context`, `read-subjects`, `expected-response-types`)
from text every time they were validated, serialized or accessed. A 10-minute JFR profile of the `things`
service in production attributed roughly **11% of CPU** to this redundant work.

Two independent optimizations remove it: the parsed `JsonValue` is now memoized lazily on each `Header`
(the same benign-race caching pattern as `String.hashCode()`), so a value is parsed at most once per distinct
header no matter how often the instance is serialized or read; and cluster-internal deserialization now
rebuilds already-validated headers without re-running value-type validation on each hop, via the new
`DittoHeaders.newFromTrustedJson(JsonObject)`. External/untrusted ingress continues to validate as before,
and there is no behavior change. A JMH benchmark measured accessors becoming ~4–7× faster, `toJson()` ~6×
faster and a cached parse ~310× faster.

The new public API `DittoHeaders.newFromTrustedJson(JsonObject)` is tagged `@since 3.9.5`.


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.5).

#### Fix blank resource_type metric tag crashing cluster serialization

PR [#2499](https://github.com/eclipse-ditto/ditto/pull/2499) fixes an `IllegalArgumentException`
("The value must not be blank.") thrown on the Pekko Artery encoder thread when serializing health signals
(`RetrieveHealth`, `RetrieveHealthResponse`, `ResetHealthEvents`, `ResetHealthEventsResponse`). Since the
`<serializer>_serializer_messages` counter started being tagged with the signal's `resource_type`, these
signals — which intentionally have a blank resource type — caused the tagged counter to be rejected, and
because the counter is built lazily the failure recurred for every such message. The serializer now falls
back to the existing `other` bucket when the resource type is `null` or blank, so the metric tag is always
valid.

#### Fix ClusterSingletonManagerIsStuck causing status-route bind restarts on rolling updates

PR [#2496](https://github.com/eclipse-ditto/ditto/pull/2496) fixes service pods restarting 1–3 times on
every rolling update with `Bind failed ... Address already in use` on the HTTP status route port `8080`.
During a rolling update the shared Pekko cluster churns, and gossiping the previous oldest node's removal
could take longer than the default hand-over retry budget, so the new oldest threw
`ClusterSingletonManagerIsStuck`. `DittoRootActor`'s decider escalated that, restarting the root actor,
which re-bound the still-held status port and terminated the actor system.

`DittoRootActor` now restarts the stuck singleton child (the recovery Pekko intends) instead of escalating,
`bindHttpStatusRoute` tolerates a `BindException` by logging a warning and keeping the system alive, and
`pekko.cluster.singleton.min-number-of-hand-over-retries` was raised from 15 to 30 (env
`PEKKO_CLUSTER_SINGLETON_MIN_NUMBER_OF_HAND_OVER_RETRIES`, exposed as
`global.cluster.singletonMinNumberOfHandOverRetries` in the Helm chart) so the new oldest waits for the
previous oldest's removal to be gossiped and then takes over cleanly.

#### Include the resolved IP in blocked-host connection error messages

Issue [#681](https://github.com/eclipse-ditto/ditto/issues/681) / PR [#2493](https://github.com/eclipse-ditto/ditto/pull/2493)
improves the error message when a connection host is rejected because it resolves to a blocked address
(loopback, site-local, multicast, or a blocked subnet). Previously a publicly valid domain that internally
resolved to a local address (e.g. via a Docker network alias) reported only that the host was blocked,
without any hint as to why. The message now names the resolved IP that triggered the block — for example
`the hostname resolved to a site local address (192.168.1.5)` — and points to the allowed-hostnames
configuration as a remediation path.


### Helm Chart

The Helm chart was updated to version `4.5.0`, bumping the Ditto `appVersion` to `3.9.5`. It exposes the new
`global.cluster.singletonMinNumberOfHandOverRetries` setting (wired into all five service deployments) from
PR [#2496](https://github.com/eclipse-ditto/ditto/pull/2496), and no longer generates the gateway devops /
status passwords when `oauth2` (JWT) devops authentication is used — avoiding a `Secret` that churned on every
render and restarted the gateway on each ArgoCD sync (PR [#2500](https://github.com/eclipse-ditto/ditto/pull/2500)).
The full, itemized list of chart changes lives in the chart's own
[CHANGELOG](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).


## Migration notes

No known migration steps are required for this release.
