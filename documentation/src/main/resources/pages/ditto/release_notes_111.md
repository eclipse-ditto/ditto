---
title: Release notes 1.1.1
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.1 of Eclipse Ditto, released on 11.05.2020"
permalink: release_notes_111.html
---

This is a bugifx release needed for the Ditto Java SDK.

## Bugfixes

### [Allow to respond to live messages via ditto client](https://github.com/eclipse/ditto-clients/pull/60)

Don't force response-required to be false when calling send() without response consumer.

### [Header mapping](https://github.com/eclipse/ditto/pull/671)

Remove filtering of unknown headers for adaptables. We need those headers for header mapping in connectivity.
