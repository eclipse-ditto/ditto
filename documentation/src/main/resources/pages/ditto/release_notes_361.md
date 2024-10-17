---
title: Release notes 3.6.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.1 of Eclipse Ditto, released on 17.10.2024"
permalink: release_notes_361.html
---

This is a bugfix release, no new features since [3.6.0](release_notes_360.html) were added.

## Changelog

Compared to the latest release [3.6.0](release_notes_360.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.1).

#### Fix Ditto UI environment creation was no longer possible

Reported issue [#2040](https://github.com/eclipse-ditto/ditto/issues/2040) about not being able to create new environments
in the Ditto UI version 3.6.0 has been fixed via PR [#2041](https://github.com/eclipse-ditto/ditto/pull/2041).

#### Fixed JavaScript payload mapper not being able to serialize arrays as header fields

In PR [#2043](https://github.com/eclipse-ditto/ditto/pull/2043) a bug in the 
[JavaScript payload mapper](connectivity-mapping.html#javascript-mapper) was fixed which caused that arrays in headers 
(e.g. `DittoHeaders`) were not rendered correctly, but instead as e.g. `'org.mozilla.javascript.NativeArray@e90b588'`.

#### Fixed configuration for new WoT Thing model based validation

PR [#2045](https://github.com/eclipse-ditto/ditto/pull/2045) fixes a bug regarding `dynamic-configuration` of the new
[WoT TM based validation](basic-wot-integration.html#configuration-of-thing-model-based-validation), especially in the Helm 
configuration.
