---
title: Release notes 3.8.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.5 of Eclipse Ditto, released on 07.11.2025"
permalink: release_notes_385.html
---

This is a bugfix release, no new features since [3.8.4](release_notes_384.html) were added.

## Changelog

Compared to the latest release [3.8.4](release_notes_384.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.5).

#### Wrongly configured shard region for healthcheck lead to things-search startup error

Ditto [3.8.4](release_notes_384.html) introduced health checks of availability of shard regions before a pod gets "alive".  
There was a misconfiguration with a wrong shard region name for the things-search service, leading to startup errors in
Ditto clusters.  
PR [#2260](https://github.com/eclipse-ditto/ditto/pull/2260) fixes that misconfiguration and is the only change in 3.8.5.

