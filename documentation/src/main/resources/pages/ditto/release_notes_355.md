---
title: Release notes 3.5.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.5 of Eclipse Ditto, released on 14.05.2024"
permalink: release_notes_355.html
---

This is a bugfix release, no new features since [3.5.4](release_notes_354.html) were added.

## Changelog

Compared to the latest release [3.5.4](release_notes_354.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.5).

#### Fix that not all placeholders were supported in connection target filtering

Issue [#1931](https://github.com/eclipse-ditto/ditto/issues/1931) found out that some placeholders can not be used in RQL
`filter` expression of a connection target, e.g. `feature:id`.  
This was fixed in PR [#1932](https://github.com/eclipse-ditto/ditto/pull/1932).

#### Fix that removing fields in a merge update with a regex does not work in several cases

Issue [#1939](https://github.com/eclipse-ditto/ditto/issues/1939) revealed a bug in the functionality of
[removing fields in a merge update based on a regex](httpapi-concepts.html#removing-fields-in-a-merge-update-with-a-regex), so that
only very special fields could be removed and only when not being used via the HTTP API.  
PR [#1941](https://github.com/eclipse-ditto/ditto/pull/1941) fixed that.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Make nginx timeout configurable in helm chart

In PR [#1934](https://github.com/eclipse-ditto/ditto/pull/1934) a configuration option to configure the nginx timeout
was provided to the Ditto Helm chart, resolving issue [#1928](https://github.com/eclipse-ditto/ditto/issues/1928).
