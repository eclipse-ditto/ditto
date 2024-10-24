---
title: Release notes 3.5.9
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.9 of Eclipse Ditto, released on 14.06.2024"
permalink: release_notes_359.html
---

This is a bugfix release, no new features since [3.5.8](release_notes_358.html) were added.

## Changelog

Compared to the latest release [3.5.8](release_notes_358.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.9).

#### Fix performance regression issue when running against MongoDB 6 - part 3

In Ditto [3.5.7](release_notes_357.html) and [3.5.8](release_notes_358.html) a MongoDB `aggregation` query done by Ditto 
as part of background deletion was aimed to be optimized.  
This fix provided in PRs [#1956](https://github.com/eclipse-ditto/ditto/pull/1956) and [#1961](https://github.com/eclipse-ditto/ditto/pull/1961) however were not completely sufficient to fix the performance
and reduce the disk read IOPS.

PR [#1964](https://github.com/eclipse-ditto/ditto/pull/1964) now in total adds 3 new indexes to the "snapshot" collections.  
Those however are not configured by default, they must be configured as documented in the newly added 
[MongoDB tuning](installation-operating.html#mongodb-tuning) section: 
[Background aggregation queries](installation-operating.html#background-aggregation-queries).

If you encounter performance issues and many disk read IOPS done in MongoDB version 6 (and maybe above), please check
those settings.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Make logstash buffer sizes configurable

PR [#1963](https://github.com/eclipse-ditto/ditto/pull/1963) make the logstash buffer sizes used in `logback.xml` files
of the Ditto services configurable via environment variables.  
The configuration were also exposed to the Ditto Helm chart in that PR.
