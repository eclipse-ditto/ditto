---
title: Release notes 3.5.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.2 of Eclipse Ditto, released on 05.02.2024"
permalink: release_notes_352.html
---

This is a bugfix release, no new features since [3.5.1](release_notes_351.html) were added.

## Changelog

Compared to the latest release [3.5.1](release_notes_351.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.2).

#### Fix wrongly done invalidation of non-existing cached policy entry in search

A [bugfix for Ditto 3.5.0](https://github.com/eclipse-ditto/ditto/pull/1872) introduced another side effect regarding
policy imports. When an imported policy was deleted and added again with the same id, the affected things were not updated
in the search index.
PR [#1889](https://github.com/eclipse-ditto/ditto/pull/1889) provides a fix for this.

#### Fix retrieving JSON arrays via fields selector was broken

Reported bug [#1888](https://github.com/eclipse-ditto/ditto/issues/1888) caused when selecting a JSON array via the
`fields` selector, the array's content was not returned.  
PR [#1890](https://github.com/eclipse-ditto/ditto/pull/1890) fixes that.

#### Several small fixes and improvements in the Ditto UI

PR [#1891](https://github.com/eclipse-ditto/ditto/pull/1891) provides some fixes around the Ditto UI, e.g.:
* send message with timeout `0` and no payload showed incorrect error in the response field
* added category column on connection logs, so you can distinguish source and target log entries
* connections js editors had wrong read-only behavior
* connection log details ace editor now with word wrap, otherwise it is hard to read long message in one line
* Bugfix: filter on incoming messages does not work on new incoming messages

Additional fixes for the UI contained in this release:
* fix policyId search slot submitting form
* fix that sending a payload `0` as message payload did not work
* add form to authentication popup, submitting via enter
* open authentication popup when backend responds with error needing authentication


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Fix hook-scripts configmap missing in Helm pre-upgrade Hooks

The newly introduced [calculation of pod deletion cost via a Helm hook](https://github.com/eclipse-ditto/ditto/pull/1871)
was missing setting up the configmap containing the script properly.  
This was fixed in [#1880](https://github.com/eclipse-ditto/ditto/pull/1880).  
PR [#1886](https://github.com/eclipse-ditto/ditto/pull/1886) in addition fixes a wrong name variable used for the configmap.
