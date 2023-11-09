---
title: Release notes 3.4.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.1 of Eclipse Ditto, released on 09.11.2023"
permalink: release_notes_341.html
---

This is a bugfix release, no new features since [3.4.0](release_notes_340.html) were added.

## Changelog

Compared to the latest release [3.4.0](release_notes_340.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.4.1).

#### Fix update headers of ConnectionClosed event

Bug [#1780](https://github.com/eclipse-ditto/ditto/issues/1780), trying to retrieve a closed connection by tag with 
`SudoRetrieveConnectionIdsByTag` fails because the "connectionClosed" event persisted in the "connection_journal" 
does not contain tags and leads to skipping them,
was fixed in [#1781](https://github.com/eclipse-ditto/ditto/pull/1781).

#### Fix that ThingFieldSelector did not allow specific paths of "_metadata"

[#1782](https://github.com/eclipse-ditto/ditto/pull/1782) fixes that only `"_metadata"` was able to select in a 
`ThingFieldSelector`, not being able to define a specific path or even wildcards.  
Now, e.g. also `"_metadata/features/*/properties"` can be used.

#### Fix preserving DittoHeaders when encountering a JsonParseException

[#1792](https://github.com/eclipse-ditto/ditto/pull/1792) fixes that `DittoHeaders` were not preserved on JSON parsing
errors, mainly in the gateway, but also at different other places.

#### Fix that Hono connection can't be created for Hono tenant with '.' in name

Bug [#1748](https://github.com/eclipse-ditto/ditto/issues/1748), causing that it was not possible to create a
[hono](connectivity-protocol-bindings-hono.html) connection type with a `.` in the Hono tenant name, was fixed by 
[#1788](https://github.com/eclipse-ditto/ditto/pull/1788).

#### Fix that JsonObjectBuilder.remove() removes too much in certain cases

[#1798](https://github.com/eclipse-ditto/ditto/pull/1798) fixes a bug in ditto-json library `JsonObjectBuilder.remove(..)` 
which deleted too much and unexpected things in a JsonObjectBuilder.  
That resulted in the things event journal missing the `value` for certain `MergeThing` commands where only a scalar JSON
value like a number or a string was patched.

#### Fix that multiple sort options in combinations with cursor fails

Bug [#1797](https://github.com/eclipse-ditto/ditto/issues/1797), causing a search query containing more than one sort 
property is not handled properly, was fixed by [#1799](https://github.com/eclipse-ditto/ditto/pull/1799).


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Move x-ditto-pre-authenticated and X-Forwarded-User to ingress.api.annotations

Addressing issue [#1778](https://github.com/eclipse-ditto/ditto/issues/1778),  [#1787](https://github.com/eclipse-ditto/ditto/pull/1787)
provides that `X-Forwarded-User` and `x-ditto-pre-authenticated` headers can be configured via Ditto's Helm `values.yaml`.
