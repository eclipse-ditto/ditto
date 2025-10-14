---
title: Release notes 3.8.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.2 of Eclipse Ditto, released on 14.10.2025"
permalink: release_notes_382.html
---

This is a bugfix release, no new features since [3.8.1](release_notes_381.html) were added.

## Changelog

Compared to the latest release [3.8.1](release_notes_381.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.2).

#### Fix excessive AskTimeout errors during rolling updates / shard rebalancing

PR [#2241](https://github.com/eclipse-ditto/ditto/pull/2241) fixes a long encountered issue where during rolling updates or shard rebalancing
excessive `AskTimeoutException` errors were logged for commands sent to things during recovery time from the database.

This fix should stabilize restarts and e.g. rolling updates of Ditto clusters significantly.

### Helm chart

#### Make helm templates openshift SCC compliant

PR [#2242](https://github.com/eclipse-ditto/ditto/pull/2242) fixes another issue in the Helm chart to deploy Ditto on OpenShift clusters.  
It fixes to add a service account to the Ditto UI and injects a configured OpenShift security context to the nginx 
containers of Ditto (nginx as reverse proxy, Ditto UI and Swagger-UI).
