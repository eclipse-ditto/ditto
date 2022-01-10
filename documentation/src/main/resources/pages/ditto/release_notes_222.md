---
title: Release notes 2.2.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.2.2 of Eclipse Ditto, released on 05.01.2022"
permalink: release_notes_222.html
---

This is a bugfix release, no new features since [2.2.1](release_notes_221.html) were added.

## Changelog

Compared to the latest release [2.2.1](release_notes_221.html), the following bugfixes were added.

### Bugfixes

#### Update logback to 1.2.10 due to "possibility of vulnerability"

The used logback version was updated to version 1.2.10 to mitigate a [known CVE](https://cve.report/CVE-2021-42550)
with logback versions smaller than 1.2.9.
