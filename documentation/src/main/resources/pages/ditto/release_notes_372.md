---
title: Release notes 3.7.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.2 of Eclipse Ditto, released on 31.03.2025"
permalink: release_notes_372.html
---

This is a bugfix release, no new features since [3.7.1](release_notes_371.html) were added.

## Changelog

Compared to the latest release [3.7.1](release_notes_371.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.2).

#### Fix SSO in Ditto UI not presenting loging page when session expired

PR [#2149](https://github.com/eclipse-ditto/ditto/pull/2149) fixes that the Ditto UI was not invalidating the SSO session
when the JWT was expired.

#### Fix WoT primitive values validation

In issue [#2148](https://github.com/eclipse-ditto/ditto/issues/2148) a bug was reported that primitive values were 
validated wrongly by Ditto as part of the WoT model based validation when they were part of an `object`, but updated
alone with a partial update.  
PR [#2152](https://github.com/eclipse-ditto/ditto/pull/2152) provides the fix to this issue.

#### Update nginx ingress controller version to mitigate security vulnerability

The configured nginx-ingress version was updated to mitigate [CVE-2025-1974](https://kubernetes.io/blog/2025/03/24/ingress-nginx-cve-2025-1974/).
