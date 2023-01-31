---
title: Release notes 1.1.5
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.5 of Eclipse Ditto, released on 25.08.2020"
permalink: release_notes_115.html
---

This is a bugfix release, no new features since [1.1.3](release_notes_113.html) were added.

Unfortunately Ditto release attempt 1.1.4 had technical difficulties on the build server which lead to that
we had to skip 1.1.4 and release 1.1.5 instead (being the same as 1.1.4 would have been).

## Changelog

Compared to the latest release [1.1.3](release_notes_113.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.1.5), including the fixed bugs.


#### [Compile model modules with Java 8](https://github.com/eclipse-ditto/ditto/pull/769)

The 2 modules in the Ditto model were accidentally compiled with Java 11 as source/target which caused 
that e.g. the Ditto client could not be used any longer with Java 8:
* `rql-parser`
* `thingssearch-parser`
