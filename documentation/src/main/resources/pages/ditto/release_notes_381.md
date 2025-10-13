---
title: Release notes 3.8.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.1 of Eclipse Ditto, released on 13.10.2025"
permalink: release_notes_381.html
---

This is a bugfix release, no new features since [3.8.0](release_notes_380.html) were added.

## Changelog

Compared to the latest release [3.8.0](release_notes_380.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.1).

#### Fix new option "merge empty objects after patch condition filtering" only worked on JsonObjects

PR [#2237](https://github.com/eclipse-ditto/ditto/pull/2237) fixes an issue introduced with the new option `MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING`
of the in 3.8.0 added [path specific conditions of thing merge commands](basic-conditional-requests.html#configuration-for-path-specific-conditions)
where it was assumed that the value to be merged is always a `JsonObject` and an exception was raised if it was not.
