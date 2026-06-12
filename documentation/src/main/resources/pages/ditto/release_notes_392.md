---
title: Release notes 3.9.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.2 of Eclipse Ditto, released on 12.06.2026"
permalink: release_notes_392.html
---

This is a bugfix release, no new features since [3.9.1](release_notes_391.html) were added.

## Changelog

Compared to the latest release [3.9.1](release_notes_391.html), the following changes and bugfixes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.2).

#### Defer CBOR/string pre-encoding in `SoftReferencedFieldMap` until needed

PR [#2461](https://github.com/eclipse-ditto/ditto/pull/2461) eliminates eager CBOR (or string fallback) serialization
in the `ImmutableJsonObject` constructor chain. JFR profiling of production `things-service` pods on 3.9.1 showed the
combined `SoftReferencedFieldMap.<init>` / `createCborRepresentation` / `JacksonSerializationContext` allocation and
flush samples accounting for ~6–10% of CPU. Intermediate `JsonObject` instances produced by transformation and
projection pipelines paid this cost without ever benefiting, because the cached bytes were discarded along with the
object.

Encoding is now deferred to the first call that actually needs it (`toString` / `writeValue` /
`upperBoundForStringSize`), and the field-map reference is only softened once a recoverable representation exists.
JMH benchmarks show 2.6×–2.9× speedups on build-without-serialize paths and a 1.5× speedup on build-then-access
paths, with no public API change and no behavior change in the size-validation contract.

#### Remove regex from `ImmutableJsonPointer` escape/parse hot path

PR [#2460](https://github.com/eclipse-ditto/ditto/pull/2460) replaces `Pattern`/`Matcher` usage in
`ImmutableJsonPointer.escapeTilde`, `decodeTilde`, and the consecutive-slash check with manual single-pass
character scans that fast-return the input string unchanged when no tilde is present — the dominant production case.

JFR profiling of production `things-service` pods on 3.9.1 had identified `escapeTilde` as the single hottest Ditto
method at ~3.8% of CPU. JMH benchmarks show 1.19×–2.57× speedups across realistic scenarios, with behavior that is
character-for-character identical to the prior implementation.

### Bugfixes

#### Fix partial-access filter stripping `thingId` and audit fields from SSE/WebSocket events

PR [#2465](https://github.com/eclipse-ditto/ditto/pull/2465) fixes a regression introduced with the 3.9.0
partial-events feature where the emitted Thing JSON was filtered by exact path match against the subscriber's policy
grants. Top-level entity fields that were not a listed grant were stripped, including `thingId`. A subscriber with
READ on `thing:/attributes` therefore received SSE events without any way to identify which Thing the change applied
to — a behavior regression versus 3.8.x. The same code path also stripped identity and audit fields from WebSocket
payloads of full-Thing events (`ThingCreated` / `ThingModified` / `ThingMerged`) and from user-requested
`extraFields`.

The fix preserves `thingId`, `_namespace`, `_revision`, `_modified`, and `_created` whenever the subscriber holds at
least partial READ access on the Thing, applied consistently across the SSE filter, the adaptable payload and extras
filters, and the `RetrieveThing` response allowlist. `policyId` and `_metadata` are intentionally excluded from the
allowlist and remain per-path policy-enforced.


## Migration notes

No known migration steps are required for this release.
