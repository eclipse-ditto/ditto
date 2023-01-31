---
title: Release notes 2.4.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.4.2 of Eclipse Ditto, released on 07.09.2022"
permalink: release_notes_242.html
---

This is a bugfix release, no new features since [2.4.1](release_notes_241.html) were added.

## Changelog

Compared to the latest release [2.4.1](release_notes_241.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.4.2), including the fixed bugs.

#### [Skip hostname verification check with self signed in kafka](https://github.com/eclipse-ditto/ditto/pull/1475)

Another required fix in order to connect to a Kafka with a self signed certificate.
