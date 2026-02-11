---
title: Release notes 3.8.12
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.12 of Eclipse Ditto, released on 11.02.2026"
permalink: release_notes_3812.html
---

This is a bugfix release, no new features since [3.8.11](release_notes_3811.html) were added.

## Changelog

Compared to the latest release [3.8.11](release_notes_3811.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.12).

#### Fix background sync always detecting policy inconsistency for Things with policy imports

PR [#2317](https://github.com/eclipse-ditto/ditto/pull/2317) fixes an issue where background sync always detected a
policy inconsistency for Things whose Policies have imports. The MongoDB projection in `sudoStreamMetadata()` was
missing the referenced policies field, causing the indexed metadata to always have an empty set of referenced policies.
This triggered continuous unnecessary search index updates.

#### Fix aggregate metrics using wrong scrapeInterval and showing 0ms duration

PR [#2334](https://github.com/eclipse-ditto/ditto/pull/2334) fixes two bugs in operator aggregate metrics:
* `OperatorAggregateMetricsProviderActor` used the maximum configured `scrapeInterval` for all metric timers instead of
  each metric's own interval. Now each metric's individual `scrapeInterval` is respected, falling back to the global
  default.
* `AggregateThingsMetricsActor` measured aggregation duration at stream materialization time instead of at stream
  completion time, causing the logged duration to always be ~0ms.

#### Fix Policy activation failure when other expired subjects exist

PR [#2335](https://github.com/eclipse-ditto/ditto/pull/2335) fixes a bug where `activateTokenIntegration` requests
failed when the Policy contained other subjects that were already expired but still within their grace period before
deletion. Now only the requested subject is checked for being expired, instead of checking the entire Policy.

### Helm Chart

#### Expose Pekko HTTP host pool max connection lifetime via Helm values

PR [#2336](https://github.com/eclipse-ditto/ditto/pull/2336) adds `connectivity.config.connections.httpPush.maxConnectionLifetime`
to Helm values (default: `"Inf"`) and wires it to the `PEKKO_HTTP_HOSTPOOL_MAX_CONNECTION_LIFETIME` environment variable
in the connectivity deployment template.
