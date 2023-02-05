---
title: Release notes 1.2.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 1.2.1 of Eclipse Ditto, released on 08.09.2020"
permalink: release_notes_121.html
---

This is a bugfix release, no new features since [1.2.0](release_notes_120.html) were added.

## Changelog

Compared to the latest release [1.2.0](release_notes_120.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.2.1), including the fixed bugs.


#### [Ditto JSON is not OSGi compatible due to missing imports](https://github.com/eclipse-ditto/ditto/issues/790)

The OSGi bundle `ditto-json` was not compatible to be run in OSGi environments as imports of 3rd party libraries
were used which were not defined in the `Import-Package` of the bundle.

The 3rd party libraries (`jackson-core` and `jackson-dataformat-cbor`) were declared on "provided" scope previously 
which made problems in the OSGi environment.

Instead, the bugfix splitted up the CBOR serialization into a separate module `ditto-json-cbor` in order to prevent the
jackson dependencies to be required at all in `ditto-json` (and as a result also in the `ditto-client`).
