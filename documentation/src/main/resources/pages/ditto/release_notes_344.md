---
title: Release notes 3.4.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.4 of Eclipse Ditto, released on 02.01.2024"
permalink: release_notes_344.html
---

This is a bugfix release, no new features since [3.4.3](release_notes_343.html) were added.

## Changelog

Compared to the latest release [3.4.3](release_notes_343.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.4.4).

#### Fetching a thing at a non-existing historical revision (e.g. in future) results in status 503

Bug [#1844](https://github.com/eclipse-ditto/ditto/issues/1844) caused that a request for a non-existing 
`at-historical-revision` resulted in an HTTP status `503` and caused the actor to be blocked for 15 seconds.  
This was fixed in PR [#1845](https://github.com/eclipse-ditto/ditto/pull/1845).

#### Fixed that "condition" query param could not be provided as form field

PR [#1848](https://github.com/eclipse-ditto/ditto/pull/1848) fixes that - when using the `POST /search/things` endpoint
the `condition` query parameter could not be passed as form field - which again leads to potentially too large HTTP urls
when providing a very long query/condition RQL.

