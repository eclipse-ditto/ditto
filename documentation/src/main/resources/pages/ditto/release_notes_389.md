---
title: Release notes 3.8.9
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.9 of Eclipse Ditto, released on 11.12.2025"
permalink: release_notes_389.html
---

This is a bugfix release, no new features since [3.8.8](release_notes_388.html) were added.

## Changelog

Compared to the latest release [3.8.8](release_notes_388.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.9).

#### Fix ERROR log on "response-required: false" for modifying commands

PR [#2290](https://github.com/eclipse-ditto/ditto/pull/2290) fixes a "false positive" ERROR log entry which was logged
when a modifying twin command was sent with header `response-required: false`.  

The impact was only "cosmetic", as the command was processed correctly, but an unnecessary ERROR log entry was created 
which could trigger false alerts in monitoring systems monitoring Ditto.
