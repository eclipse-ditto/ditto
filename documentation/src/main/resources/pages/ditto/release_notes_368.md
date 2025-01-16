---
title: Release notes 3.6.8
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.8 of Eclipse Ditto, released on 16.01.2025"
permalink: release_notes_368.html
---

This is a bugfix release, no new features since [3.6.7](release_notes_367.html) were added.

## Changelog

Compared to the latest release [3.6.7](release_notes_367.html), the following changes and bugfixes were added.

### Bugfixes
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.8).

#### Fix wrongly done validation of removal of desired feature properties

PR [#2093](https://github.com/eclipse-ditto/ditto/pull/2093) fixes that the WoT validation prevented desired properties
to be removed from a Thing if they were defined as "required" in the Thing Model. This does not make sense for desired 
properties, as they are all optional by convention.

#### Fix spans for consuming messages to not be the "parent" span for following spans

PR [#2094](https://github.com/eclipse-ditto/ditto/pull/2094) fixes a parent-child hierarchy in the tracing of Ditto 
regarding consumption of messages, e.g. via Apache Kafka.

#### Fix wrongly calculated path in WoT validation errors

PR [#2091](https://github.com/eclipse-ditto/ditto/pull/2091) fixes that a JsonPointer in WoT validation messages was
calculated wrongly for PATCHing Thing attributes.
