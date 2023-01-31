---
title: Release notes 2.4.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.4.1 of Eclipse Ditto, released on 25.08.2022"
permalink: release_notes_241.html
---

This is a bugfix release, no new features since [2.4.0](release_notes_240.html) were added.

## Changelog

Compared to the latest release [2.4.0](release_notes_240.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.4.1), including the fixed bugs.

#### [In case of ThingDeleteModel always consider model as not outdated](https://github.com/eclipse-ditto/ditto/pull/1368)

The search index could run into consistency problem which this fix improves.  
The final fix for having a fully consistent search index however will only be available in Ditto `3.0.0` where the 
index was completely rebuilt.

#### [Enable self signed certificates for kafka](https://github.com/eclipse-ditto/ditto/pull/1456)

Fix that self-signed certificates for Kafka connections could not be used.
