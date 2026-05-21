---
title: Release notes 3.9.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.9.1 of Eclipse Ditto, released on 20.05.2026"
permalink: release_notes_391.html
---

This is a bugfix release, no new features since [3.9.0](release_notes_390.html) were added.

## Changelog

Compared to the latest release [3.9.0](release_notes_390.html), the following changes and bugfixes were added.

### Changes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.9.1).

#### Optimize partial-access-paths event enrichment hot path

PR [#2453](https://github.com/eclipse-ditto/ditto/pull/2453) significantly reduces the CPU cost and per-event allocation
of the partial-access-paths enrichment introduced in 3.9.0.

JMH benchmarks show 2.5×–12.7× speedups across realistic scenarios and per-operation allocation drops of 69–87%. There
is no behavior change — the `ditto-partial-access-paths` header format and contents remain identical.

#### Add `policy-lockout-prevention` feature toggle for `PoliciesValidator`

PR [#2456](https://github.com/eclipse-ditto/ditto/pull/2456) introduces the
`ditto.devops.feature.policy-lockout-prevention-enabled` feature toggle (default `true`, preserving the existing
behavior) which controls whether `PoliciesValidator` rejects create/modify operations whose resulting policy lacks a
permanent subject with `WRITE` on `policy:/`.

With namespace-scoped root policies (introduced in 3.9.0), the implicitly imported global policy may already supply
that permission — as a result there are cases where it makes sense to globally disable policy lockout prevention.

### Bugfixes

#### Fix activity-check reschedule interval for non-deleted persistent actors

PR [#2454](https://github.com/eclipse-ditto/ditto/pull/2454) fixes `AbstractPersistenceActor.checkForActivity` to
reschedule the next activity check based on whether the entity exists as deleted, matching the initial scheduling done
in `becomeCreatedHandler` (inactive) versus `becomeDeletedHandler` (deleted). Previously, the next check was always
rescheduled at `deletedInterval` (default 5 minutes), regardless of whether the entity was deleted. For an active
entity this meant that after the *first* `inactiveInterval` (default 2 hours) check passed, every subsequent check
fired every 5 minutes and the actor was shut down as soon as no command arrived within a 5 minute window — far earlier
than the configured `inactive-interval` would suggest. The fix affects all subclasses of `AbstractPersistenceActor`
(Thing, Policy, Connection); behavior for deleted entities is unchanged.

#### Fix resolved policy view dropping imports when source-side READ comes via namespace-root

PR [#2458](https://github.com/eclipse-ditto/ditto/pull/2458) fixes a regression in the `?policy-view=resolved`
response where every entry contributed by a declared import was silently stripped when the caller's READ on the
imported policy came solely from an operator-configured namespace-root policy. `PolicyCommandEnforcement` built each
source enforcer with `withResolvedImports` — without merging namespace-root entries — so the source-side READ filter
evaluated `policy:/entries/<label>` against the source policy's own subjects only. A caller whose access to the
source came only via the namespace-root (e.g. a global devops admin) lost every `imported-<srcId>-…` entry from the
resolved view, while imports that happened to contain a directly-authenticating subject still worked. Source
enforcers now use `withResolvedImportsAndNamespacePolicies`, mirroring the importing-policy evaluation; the existing
security tests still pass — a caller with no legitimate path to the source still loses the entries.

### Helm Chart

The following changes were included via the `helm-chart-4.1.0` milestone (see the complete list of
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3Ahelm-chart-4.1.0)).

#### Allow configuring OIDC `issuers` via Helm values

PR [#2442](https://github.com/eclipse-ditto/ditto/pull/2442) adds support for configuring the `issuers` option of
OpenID Connect issuers directly in the Helm chart values.

#### Fix service account annotations and labels

PR [#2455](https://github.com/eclipse-ditto/ditto/pull/2455) fixes the service account template so that annotations
render with the correct indent (multiple annotations previously failed to render) and moves labels to the appropriate
key.

#### Wire `policy-lockout-prevention` toggle into Helm values

The new `ditto.devops.feature.policy-lockout-prevention-enabled` feature toggle (see
[PR #2456](https://github.com/eclipse-ditto/ditto/pull/2456) under Changes) is exposed in the Helm values and wired
into the policies deployment via the corresponding environment variable.


## Migration notes

No known migration steps are required for this release.
