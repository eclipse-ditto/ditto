---
title: Release notes 3.6.9
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.9 of Eclipse Ditto, released on 16.01.2025"
permalink: release_notes_369.html
---

This is a bugfix release, no new features since [3.6.8](release_notes_368.html) were added.

## Changelog

Compared to the latest release [3.6.8](release_notes_368.html), the following changes and bugfixes were added.

### Bugfixes
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.9).

#### Fix exceptions in WebSocket due to wrongly formatted added "EntityId" header

Ditto release [3.6.5](release_notes_365.html) introduced an improved log messages for errors in WoT validations, adding
the Thing's ID to the log message. Internally, a change was needed to always provide the thing's ID as internal header.  
This added header had however the wrong format, which caused exceptions e.g. in the Ditto WebSocket handling.

This was reported in issue [#2096](https://github.com/eclipse-ditto/ditto/issues/2096) and was fixed via PR 
[#2097](https://github.com/eclipse-ditto/ditto/pull/2097).

As this is a very critical bug, a bugfix release containing just this one fix is provided: `3.6.9`.

