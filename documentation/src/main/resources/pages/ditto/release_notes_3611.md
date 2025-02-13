---
title: Release notes 3.6.11
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.11 of Eclipse Ditto, released on 14.02.2025"
permalink: release_notes_3611.html
---

This is a bugfix release, no new features since [3.6.10](release_notes_3610.html) were added.

## Changelog

Compared to the latest release [3.6.10](release_notes_3610.html), the following changes and bugfixes were added.

### Bugfixes
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.11).

#### Fix regression in message response WoT validation failing for non-json responses

PR [#2118](https://github.com/eclipse-ditto/ditto/pull/2118) fixes a bug introduced in Ditto `3.6.10` which caused
that responses to [messages](basic-messages.html) which were not of content-type `application/json` were rejected as 
"not valid" by the WoT validation, even if the WoT validation was disabled.

#### Fix "If-Equal: skip" header was not treating policies/things as equal in certain situations

E.g. `thingId` or `policyId` were not equally treated when comparing old to new thing/policy - which caused the entity
to always be different, even if it did not change at all.  
PR [#2116](https://github.com/eclipse-ditto/ditto/pull/2116) provides a fix.

#### Ditto connections which contained failures in payload mappers did not show that in their "metrics"

Issue [#2115](https://github.com/eclipse-ditto/ditto/issues/2115) reported an inconsistency when handling failures in 
Ditto connections in regard to payload mappings which result in errors.  
Those errors could be seen in the connection "logs", however not in the connection metrics, as the metric category "other"
was missing.  
This was fixed in PR [#2119](https://github.com/eclipse-ditto/ditto/pull/2119) which adds this category and counts correctly
error in payload mappings.

### Helm Chart

#### Fix privilege escalation for pod deletion job

PR [#2113](https://github.com/eclipse-ditto/ditto/pull/2113) provides a fix for a potential privilege escalation issue 
regarding the pod deletion job in the Ditto Helm chart.
