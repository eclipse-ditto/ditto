---
title: Release notes 1.1.2
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.2 of Eclipse Ditto, released on 01.07.2020"
permalink: release_notes_112.html
---

This is a bugfix release, no new features since [1.1.1](release_notes_111.html) were added.

## Changelog

Compared to the latest release [1.1.1](release_notes_111.html), the following changes and bugfixes were added.

### Changes

#### [Publish minor and micro version tags to Docker Hub](https://github.com/eclipse-ditto/ditto/pull/693)

Starting with Ditto 1.1.2, the Docker images built and pushed to Docker Hub are:
* full version (e.g. `1.1.2`)
* minor version (e.g. `1.1`)
* major version (e.g. `1`)
* `latest`

### Bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.1.2), including the fixed bugs.


#### [Fix NullPointerException when disabling validation of certificates in connections](https://github.com/eclipse-ditto/ditto/pull/688)

Fixed [Mqtt 3 connection without using certificate validation](https://github.com/eclipse-ditto/ditto/issues/679).

#### [Connection creation timeout](https://github.com/eclipse-ditto/ditto/pull/692)

There was an issue where sometimes the creation of a connection entity failed with a timeout just because the establishing of the actual connection took too much time.

#### [Minor improvements to throughput/performance](https://github.com/eclipse-ditto/ditto/pull/689)

Some minor overall simplifications and performance improvements. 
