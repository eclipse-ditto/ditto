---
title: Release notes 3.9.6
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.6 of Eclipse Ditto, released on 28.07.2026"
permalink: release_notes_396.html
---

This is a bugfix release, no new user-facing API features since [3.9.5](release_notes_395.html) were added.
It continues the reduction of the CPU spent on `DittoHeaders` handling started in 3.9.5, this time closing
the gap that appeared when distributed tracing is enabled and on event-journal recovery.

## Changelog

Compared to the latest release [3.9.5](release_notes_395.html), the following changes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.6).

#### Avoid re-validating already-validated DittoHeaders on traced hops and journal recovery

PR [#2507](https://github.com/eclipse-ditto/ditto/pull/2507) closes two remaining hot paths that rebuilt
`DittoHeaders` through the *validating* constructor even though the source headers had already been validated,
undoing the header-value memoization and trusted-deserialization optimizations introduced in
[3.9.5](release_notes_395.html).

The first path is triggered by distributed tracing. Injecting span context via
`DittoHeaders.of(startedSpan.propagateContext(dittoHeaders))` only takes the `DittoHeaders.of(Map)` fast-path
when its argument is already a `DittoHeaders`. With an active span, `StartedSpan.propagateContext(Map)` returns
a plain `HashMap` (the base headers plus the injected W3C trace context), so `DittoHeaders.of(...)` rebuilt
through the validating constructor and re-parsed and re-validated every JSON-typed header value on each hop —
a shape repeated at roughly 20 call sites and hence especially costly at high trace sampling rates. A new
trusted `StartedSpan.propagateContext(DittoHeaders)` overload now injects only the trace-context delta onto the
already-validated headers via `toBuilder()` (the skip-validation constructor), so only the small delta is
validated and the memoized parsed values are preserved. Because overload resolution is by static type this is
source-compatible: only `DittoHeaders` arguments switch behavior, while callers passing a raw `Map`
(e.g. transport / HTTP ingress headers) keep the existing validating `Map` overload.

The second path is event-journal recovery: `AbstractMongoEventAdapter.fromJournal` and
`AbstractPersistenceActor.mapJournalEntryToEvent` rebuilt the persisted headers via
`DittoHeaders.newBuilder(obj).build()`, re-parsing every JSON-typed header value even though those headers were
validated when the event was originally persisted. Both now deserialize the persisted headers as trusted via
`DittoHeaders.newFromTrustedJson(...)`.

There is no behavior change: external / untrusted ingress continues to validate as before.


### Helm Chart

The Helm chart was updated to version `4.6.0`, bumping the Ditto `appVersion` to `3.9.6`. There are no
chart-facing configuration changes in this release; the chart version only tracks the new application version.
The full, itemized list of chart changes lives in the chart's own
[CHANGELOG](https://github.com/eclipse-ditto/ditto/blob/master/deployment/helm/ditto/CHANGELOG.md).


## Migration notes

No known migration steps are required for this release.
