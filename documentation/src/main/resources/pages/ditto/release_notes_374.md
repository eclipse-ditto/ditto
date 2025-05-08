---
title: Release notes 3.7.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.4 of Eclipse Ditto, released on 08.05.2025"
permalink: release_notes_374.html
---

This is a bugfix release, no new features since [3.7.3](release_notes_373.html) were added.

## Changelog

Compared to the latest release [3.7.3](release_notes_373.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.4).

#### Asynchronous execution fixes at several places 

PR [#2165](https://github.com/eclipse-ditto/ditto/pull/2165) fixes multiple found issues where e.g. asynchronous execution 
did not run on the intended "dispatcher" / "executor", but instead on a common thread pool. This was especially the case for:
* Policy enforcement (in things and policies services)
* WoT integration (validation, skeleton generation, etc.) in the things service

This was fixed, so CPU heavy tasks should be better isolated and their dispatcher better configurable to the needs of
a specific Ditto deployment.

Some minor tweaks fixing small hotspots found during profiling were also done as part of this PR.
