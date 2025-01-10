---
title: Release notes 3.6.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.5 of Eclipse Ditto, released on 10.01.2025"
permalink: release_notes_365.html
---

This is a bugfix release, no new features since [3.6.4](release_notes_364.html) were added.

## Changelog

Compared to the latest release [3.6.4](release_notes_364.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.5).

#### Fix generated default for WoT array properties contained one generated "neutral element"

PR [#2086](https://github.com/eclipse-ditto/ditto/pull/2086) fixes that during the WoT 
[Thing Skeleton generation](basic-wot-integration.html#thing-skeleton-generation-upon-thing-creation) for `array` data
types a single "neutral element" (e.g. an empty object) was generated as the default value. 
This was not intended and is now fixed.

#### Add missing thingId for warning log message in WoT validation

PR [#2087](https://github.com/eclipse-ditto/ditto/pull/2087) provides additionally the `thingId` of the thing which was 
not valid according to its WoT model in the log message. This helps to identify the thing which caused the validation error.

#### Fix Gateway trace not keeping correct parent "hierarchy" when receiving existing "traceparent" header

When distributed tracing was configured in Ditto and external systems provided a `traceparent` header to Ditto, the
first span created in Ditto was not correctly assigned as a child of the span which was created by the external system.  
This has been fixed.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration
bugs and enhancements which are also addressed with this bugfix release.

#### Fixed configuration bug in WoT validation-context and log-warning-instead-of-failing-api-calls settings

PR [#2085](https://github.com/eclipse-ditto/ditto/pull/2085) fixed a bug in the Helm configuration which caused
the WoT validation configuration not to be applied correctly.
