---
title: Release notes 3.4.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.3 of Eclipse Ditto, released on 05.12.2023"
permalink: release_notes_343.html
---

This is a bugfix release, no new features since [3.4.2](release_notes_341.html) were added.

## Changelog

Compared to the latest release [3.4.2](release_notes_341.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.4.3).

#### Update logback 1.2 to mitigate CVE

PR [#1835](https://github.com/eclipse-ditto/ditto/pull/1835) updates logback to patch version `1.2.13` in order to
mitigate the logback CVE [CVE-2023-6481](https://www.cve.org/cverecord?id=CVE-2023-6481).


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Removed X-Original-URI from NGINX ingress

Bug [#1833](https://github.com/eclipse-ditto/ditto/issues/1833) about a regression in the Ingress-Nginx configuration
of the Ditto helm chart, was resolved via [#1834](https://github.com/eclipse-ditto/ditto/pull/1834).
