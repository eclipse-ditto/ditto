---
title: Release notes 1.1.1
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.1 of Eclipse Ditto, released on 11.05.2020"
permalink: release_notes_111.html
---

This is a bugfix release needed for the Ditto Java SDK, no new features since [1.1.0](release_notes_110.html) were added.

## Changelog

Compared to the latest release [1.1.0](release_notes_110.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.1.1), including the fixed bugs.


#### [Allow to respond to live messages via ditto client](https://github.com/eclipse-ditto/ditto-clients/pull/60)

Don't force response-required to be false when calling send() without response consumer.

#### [Header mapping](https://github.com/eclipse-ditto/ditto/pull/671)

Remove filtering of unknown headers for adaptables. We need those headers for header mapping in connectivity.
