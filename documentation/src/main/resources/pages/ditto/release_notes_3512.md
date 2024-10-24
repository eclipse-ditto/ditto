---
title: Release notes 3.5.12
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.12 of Eclipse Ditto, released on 13.09.2024"
permalink: release_notes_3512.html
---

This is a bugfix release, no new features since [3.5.11](release_notes_3511.html) were added.

## Changelog

Compared to the latest release [3.5.11](release_notes_3511.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.12).

#### Fixing stuck search Background sync

The latest MongoDB Java driver (which Ditto updated to in 3.5.10 and also in 3.5.11) contains a bug, 
[JAVA-5600](https://jira.mongodb.org/browse/JAVA-5600), which leads to an exception not being thrown in case of trying
to create an already existing connection.  
Ditto was relying on that exception being thrown when starting the search background sync. When it is not thrown and
the reactive stream does not emit any success result either, the background sync process will be stuck and will never 
run.  

This was the case for Ditto 3.5.10 and 3.5.11 and is resolved in this version via PR 
[#2017](https://github.com/eclipse-ditto/ditto/pull/2017).
