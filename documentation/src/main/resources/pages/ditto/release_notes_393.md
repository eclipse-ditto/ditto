---
title: Release notes 3.9.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.3 of Eclipse Ditto, released on 06.07.2026"
permalink: release_notes_393.html
---

This is a bugfix release, no new features since [3.9.2](release_notes_392.html) were added.

## Changelog

Compared to the latest release [3.9.2](release_notes_392.html), the following changes and bugfixes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.3).

#### Cache partial-access-paths enrichment per Thing

PR [#2481](https://github.com/eclipse-ditto/ditto/pull/2481) improves the performance of the partial-access-paths event
enrichment introduced with the 3.9.0 partial-events feature. The accessible JSON paths per subject were previously
recomputed for every `ThingEvent` of a Thing whose policy grants *restricted* READ to some subject. As the result only
depends on the policy revision and the Thing structure (not on field values), it is now cached per Thing and reused
across value-only updates. The cache can be disabled via `event.partial-access-events.cache.enabled` (default `true`,
env `THING_EVENT_PARTIAL_ACCESS_EVENTS_CACHE_ENABLED`).

#### Cache namespace-filtered policy enforcers

PR [#2480](https://github.com/eclipse-ditto/ditto/pull/2480) improves the performance of policy enforcement for policies
that use the namespace-scoped entries introduced with [#2325](https://github.com/eclipse-ditto/ditto/issues/2325) in
3.9.0. The namespace-filtered enforcer, previously rebuilt on every enforced signal, is now cached per namespace and
reused. The cache size is operator-configurable via
`ditto.policies-enforcer-cache.namespace-filtered-enforcer-max-size` (default `100`, env
`DITTO_POLICIES_ENFORCER_NAMESPACE_FILTERED_MAX_SIZE`), exposed through the Helm values and the policies, things and
connectivity deployment templates.

#### Dependency updates

PR [#2475](https://github.com/eclipse-ditto/ditto/pull/2475) updates several third-party dependencies to their latest
compatible versions.

### Bugfixes

#### Fix strict segment matching in HTTP gateway routes

PR [#2479](https://github.com/eclipse-ditto/ditto/pull/2479) fixes a route matching issue where a path segment was
matched as a prefix rather than requiring an exact match. For example, a request to `/devops/loggingA/...` matched the
`logging` route as a prefix, the remainder was passed along, and — because the next handler strips a leading slash — the
request was ultimately handled as if it targeted `/devops/logging`. Segment matching is now strict, so only exact segment
names are matched.


### Helm Chart

The accompanying Helm chart was released as version `4.3.0`. It exposes the new
`namespaceFilteredMaxSize` option (default `100`) on the policy-enforcer cache of the `policies`, `things`
and `connectivity` services (PR [#2480](https://github.com/eclipse-ditto/ditto/pull/2480)) and includes a
fix to the Swagger UI deployment so the OpenID Connect login works (PR
[#2478](https://github.com/eclipse-ditto/ditto/pull/2478)).


## Migration notes

No known migration steps are required for this release.
