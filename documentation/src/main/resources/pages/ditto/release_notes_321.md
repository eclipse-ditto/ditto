---
title: Release notes 3.2.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.2.1 of Eclipse Ditto, released on 01.04.2023"
permalink: release_notes_321.html
---

This is a bugfix release, no new features since [3.2.0](release_notes_320.html) were added.

## Changelog

Compared to the latest release [3.2.0](release_notes_320.html), the following changes and bugfixes were added.

### Changes

#### [UI - New Operations tab](https://github.com/eclipse-ditto/ditto/pull/1600)

The Ditto UI was enhanced with a new "Operation" tab where the log levels of the Ditto installation can be adjusted
on-the-fly without restarting anything.  
This functionality was already existing in the "devops" API and now has been exposed to the Ditto UI.


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.2.1).

#### [Don't use WARN log level when no policy is rolled back](https://github.com/eclipse-ditto/ditto/pull/1604)

Added in Ditto 3.2.0, a policy is rolled back when it was implicitly created as part of a thing creation which failed
for whatever reasons.  
The `WARN` log message is however logged too many times, even if no policy was created and will be rolled back.  
This was fixed.

#### [Change notification filtering doesn't work for desired properties changes](https://github.com/eclipse-ditto/ditto/issues/1599)

Desired properties were not mapped correctly when converting "thing events" to "things" and therefore filtering on 
desired properties in e.g. connections, filtering on events, did not work.  
This has been fixed.

#### [Fix reading configured WoT TD "json template" from system property](https://github.com/eclipse-ditto/ditto/pull/1601)

The system property to define a WoT TD template at property path `ditto.things.wot.to-thing-description.json-template`
was not correctly rendered as Json Object if configured.  
This has been fixed and can now be configured.

#### [Enforcer actor ack time out handled in atomic thing create context](https://github.com/eclipse-ditto/ditto/pull/1598)

Under heavy load it could happen that the policy caches in Thing "Enforcement" was misbehaving or were not correctly 
invalidated which could lead to that creation of new things with implicit policy creation failed.  
This has been fixed.
