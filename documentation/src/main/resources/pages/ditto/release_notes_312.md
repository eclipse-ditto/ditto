---
title: Release notes 3.1.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.1.2 of Eclipse Ditto, released on 08.02.2023"
permalink: release_notes_312.html
---

This is a bugfix release, no new features since [3.1.1](release_notes_311.html) were added.

## Changelog

Compared to the latest release [3.1.1](release_notes_311.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.1.2).

#### [Outgoing mqtt connections fail to publish messages when tracing is enabled](https://github.com/eclipse-ditto/ditto/issues/1563)

When [tracing](installation-operating.html#tracing) was enabled for a Ditto installation, outbound MQTT messages could
not be published due to an exception caused by an empty user property.  
This was fixed.

#### [Fixed that a missing (deleted) referenced policy of a policy import caused logging ERRORs](https://github.com/eclipse-ditto/ditto/pull/1571)

When a policy referenced in a [policy import](basic-policy.html#policy-imports) was deleted, this caused logging `ERROR`s
in the [search](architecture-services-things-search.html) service.  
This was fixed.
