---
title: Release notes 3.6.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.4 of Eclipse Ditto, released on 03.12.2025"
permalink: release_notes_364.html
---

This is a bugfix release, no new features since [3.6.3](release_notes_363.html) were added.

## Changelog

Compared to the latest release [3.6.3](release_notes_363.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.4).

#### Fixed aggregation metrics config unnecessary force of tags

PR [#2073](https://github.com/eclipse-ditto/ditto/pull/2073) fixes that in the introduced 
[aggregation based metrics](installation-operating.html#operator-defined-custom-aggregation-based-metrics) required to
use tags in order to do grouping. This can be unwanted to group by without adding a tag.

#### Fixed WoT model validation calculating the wrong path in exception

In PR [#2075](https://github.com/eclipse-ditto/ditto/pull/2075) it was fixed that when modifying feature properties
of a Thing, the path included in the validation error message contained a wrong path.

#### Fix missing headers from message after js mapper

Reported bug issue [#2077](https://github.com/eclipse-ditto/ditto/issues/2077), which resulted in headers being lost
in a custom [JavaScript payload mapper](connectivity-mapping.html#javascript-mapper), was fixed in PR 
[#2078](https://github.com/eclipse-ditto/ditto/pull/2078).

#### Fixed creating a thing with PATCH / merge containing null values used the null values for the new Thing

Issue [#2074](https://github.com/eclipse-ditto/ditto/issues/2074) reported a bug where the defined semantics of a 
[PATCH / merge](protocol-specification-things-merge.html) referring to the [JSON merge patch RFC](https://tools.ietf.org/html/rfc7396) 
was not correctly implemented, as `null` values in the Thing were not deleted (as defined in the RFC), but were used
for the created thing JSON. This was fixed in PR [#2082](https://github.com/eclipse-ditto/ditto/pull/2082).
