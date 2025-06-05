---
title: Release notes 3.7.6
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.6 of Eclipse Ditto, released on 05.06.2025"
permalink: release_notes_376.html
---

This is a bugfix release, no new features since [3.7.5](release_notes_375.html) were added.

## Changelog

Compared to the latest release [3.7.5](release_notes_375.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.6).

#### Fix negative from/to behavior for historical streaming API

PR [#2178](https://github.com/eclipse-ditto/ditto/pull/2178) fixes the behavior of the historical streaming API
when using negative values for `from` and `to` parameters.  
Providing negative values misbehaved, as the `from` was interpreted relative to the `to` parameter, wich it does not
when providing positive values.  
Now, both parameters are interpreted relative to the latest revision when negative values are provided.

#### Fix "Thing Definition Migrated" event signal enrichment merging bug

PR [#2181](https://github.com/eclipse-ditto/ditto/pull/2181) fixes a bug in the enrichment (`extra` fields) of "Thing Definition Migrated" events.  
The bug caused that configured `extraFields` when subscribing for events, e.g. in a connection were wrongly merged with the
actual migration event, which resulted in the `extra` field of the event to be potentially empty or only partially filled.


### Helm Chart

#### Allow snippets for ingress-nginx controller configuration

PR [#2174](https://github.com/eclipse-ditto/ditto/pull/2174) re-adds by accident removed `allow-snippet-annotations` option in
the last Ditto release.
