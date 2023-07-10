---
title: Release notes 3.3.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.4 of Eclipse Ditto, released on 11.07.2023"
permalink: release_notes_334.html
---

This is a bugfix release, no new features since [3.3.3](release_notes_333.html) were added.

## Changelog

Compared to the latest release [3.3.3](release_notes_333.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.4).

#### [Fix that CreationRestrictionPreEnforcer did not work](https://github.com/eclipse-ditto/ditto/pull/1682)

The previously added [restriction configuration of creating new entities](installation-operating.html#restricting-entity-creation)
did no longer work with Ditto 3.x - as some changes were done in Ditto 3.x regarding extension loading.

This is now fixed and creating new entities can be configured again.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm), which was enhanced and changed 
a lot for version 3.3.0, contained some configuration bugs which are also addressed with this bugfix release.

#### [Add support for entity creation via Helm configuration](https://github.com/eclipse-ditto/ditto/pull/1684)

In order to make use of the [restriction for creating new entities](installation-operating.html#restricting-entity-creation), 
the Helm chart was enhanced with configuration options for creating policies and things.

#### [Fix default value of 'jwtOnly' being 'true' in Helm chart ](https://github.com/eclipse-ditto/ditto/pull/1686)

Former versions of the Helm chart configured by default `jwtOnly: false` which meant that it was possible to authenticate 
users at Ditto's HTTP or WebSocket API via the nginx `.htpasswd` file approach.

The new Helm chart changed the default to `jwtOnly: true` which broke this very simple approach of authenticating users.

So the default was changed back to allow nginx authentication.
