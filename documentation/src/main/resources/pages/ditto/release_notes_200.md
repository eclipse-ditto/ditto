---
title: Release notes 2.0.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.0.0 of Eclipse Ditto, released on ??.??.2021"
permalink: release_notes_200.html
---

Even though Ditto **2.0.0** is API and mostly [binary compatible](https://github.com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 1.x versions, there are some breaking changes regarding the binary compatibility.

## Changelog

Compared to the latest release [1.5.1](release_notes_151.html), the following changes, new features and
bugfixes were added.

### Changes

### New features

### Bugfixes

### Breaking Changes

#### Ditto Client

The synchronous instantiation of the Ditto Client has been removed from its Factory class `DittoClients`.
To get a `DittoClient` instantiate a `DisconnectedDittoClient` first and call `connect()` on it.
This call returns a `CompletionStage` which finally resolves to a connected `DittoClient`.

