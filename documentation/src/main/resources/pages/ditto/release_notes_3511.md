---
title: Release notes 3.5.11
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.11 of Eclipse Ditto, released on 10.09.2024"
permalink: release_notes_3511.html
---

This is a bugfix release, no new features since [3.5.10](release_notes_3510.html) were added.

## Changelog

Compared to the latest release [3.5.10](release_notes_3510.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.11).

#### Updating MongoDB Java driver to latest bugfix

The latest MongoDB Java driver (which Ditto updated to in 3.5.10) contained a bug, 
[JAVA-5516](https://jira.mongodb.org/browse/JAVA-5516), which lead to termination cursors obtained via the reactive API.

This leads e.g. to the Ditto [Background cleanup](installation-operating.html#managing-background-cleanup) and
[Background sync](installation-operating.html#managing-background-synchronization) not working in Ditto 3.5.10.

Updating to the MongoDB driver version [5.1.4](https://github.com/mongodb/mongo-java-driver/releases/tag/r5.1.4) 
(containing the fix done in [5.1.3](https://github.com/mongodb/mongo-java-driver/releases/tag/r5.1.3) as well), resolves
seen issues about no longer working background sync and cleanup.

#### Fixes around Docker base image

Additionally, some fixes were done in the Docker image of Ditto, as the baseimage added users for uid `1000` and gid `1000`, 
which were before used by Ditto.

#### Various security related Helm improvements

Various configurations were adjusted in order to e.g. not run containers with "root" permissions.
