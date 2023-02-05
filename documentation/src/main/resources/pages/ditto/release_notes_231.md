---
title: Release notes 2.3.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.3.1 of Eclipse Ditto, released on 31.01.2022"
permalink: release_notes_231.html
---

This is a bugfix release, no new features since [2.3.0](release_notes_230.html) were added.

## Changelog

Compared to the latest release [2.3.0](release_notes_230.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.3.1), including the fixed bugs.

#### [Fix that placeholder `time:now` could not be used in connection header mapping](https://github.com/eclipse-ditto/ditto/pull/1292)

The in version 2.3.0 newly added placeholder `time:now` could not be used in connectivity's header mapping, 
this is now fixed.

#### [Enable use of entity placeholders for MQTT and HTTP connections](https://github.com/eclipse-ditto/ditto/pull/1293)

The `entity:id` placeholder could not be used in MQTT and HTTP connections, this is now fixed.

#### [Reduce the likelihood of search index inconsistency due to reordering of patch updates](https://github.com/eclipse-ditto/ditto/pull/1296)

This fix speeds up the consistency of search entries which could get inconsistent during rolling updates for very active 
things. 

#### [Fixed that JSON `null` in "correlation-id" of Ditto Protocol headers were parsed as JSON String "null"](https://github.com/eclipse-ditto/ditto/pull/1295)

When parsing messages as Ditto Protocol, a `"correlation-id"` header field being `null` was translated to the string 
literal `"null"` - this has been fixed by treating the "correlation-id" as not being present instead.
