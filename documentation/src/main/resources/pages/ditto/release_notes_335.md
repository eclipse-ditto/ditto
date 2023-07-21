---
title: Release notes 3.3.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.5 of Eclipse Ditto, released on 21.07.2023"
permalink: release_notes_335.html
---

This is a bugfix release, no new features since [3.3.4](release_notes_334.html) were added.

## Changelog

Compared to the latest release [3.3.4](release_notes_334.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.5).

#### [Fix filtering of live message with empty resolved extraFields](https://github.com/eclipse-ditto/ditto/pull/1694)

When selected `extraFields` were not present when enriching a "live message" in a connection, the filtering dropped
the message.  
This is now fixed and the message is sent also when the `extraFields` are not existing.

#### [Fixed persisting inline "_policy" in ThingMerged events](https://github.com/eclipse-ditto/ditto/pull/1695)

Since 3.3.0 it is possible to create a thing with a MergeThing (PATCH Thing) command - and it also is possible to 
provide an inline policy to be created for the "create thing" case.

This `"_policy"` field however was persisted in the Things event journal - which is definitely not wanted.

#### [Use correct HTTP status code for "if-equal": skip on equality](https://github.com/eclipse-ditto/ditto/pull/1695)

When modifying a thing and setting the (in Ditto 3.3.0 added) header `if-equal: skip` the HTTP status code if the 
outcome would be equal to the provided value is: `304 (Not Modified)`.  
This status code is however only allowed to be returned for "safe" methods like GET and HEAD.  
Instead now (same as for the If-Match headers) a `412 (Precondition failed)` is used.

### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm), which was enhanced and changed 
a lot for version 3.3.0, contained some configuration bugs which are also addressed with this bugfix release.

#### [Fix that in Helm config jwtOnly=false will enable pre-authentication in Ditto](https://github.com/eclipse-ditto/ditto/pull/1697)

By default, the Ditto Helm chart now enabled authentication with nginx when `jwtOnly` is configured to be `false`, 
which is the default.

