---
title: Release notes 3.6.10
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.10 of Eclipse Ditto, released on 04.02.2025"
permalink: release_notes_3610.html
---

This is a bugfix release, no new features since [3.6.9](release_notes_369.html) were added.

## Changelog

Compared to the latest release [3.6.9](release_notes_369.html), the following changes and bugfixes were added.

### Bugfixes
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.10).

#### Fix NPE in ScriptedOutgoingMapping when value from script resolved to null

PR [#2100](https://github.com/eclipse-ditto/ditto/pull/2100) fixed a bug in the ScriptedOutgoingMapping where 
a `NullPointerException` was thrown when the value resolved from the script was `null`.

#### Fix WoT action validation was only done for application/json content-type

In PR [#2102](https://github.com/eclipse-ditto/ditto/pull/2102) a bug was fixed which lead to the WoT validation of 
[messages](basic-messages.html) was not performed when a content-type different to `application/json` was set.  
As this bypasses ensuring that only well-defined messages are accepted by Ditto, this was considered as a bug.

#### Fix logging configuration for policies service

The default bundled `logback.xml` contained a syntax error. This was fixed in PR 
[#2104](https://github.com/eclipse-ditto/ditto/pull/2104).

#### Fix tracing propagation in general

Ditto 3.6 contained already many bugfixes where the span hierarchy of distributed tracing was calculated wrongly.  
The root cause of these issues now was identified and fixed in PR[#2105](https://github.com/eclipse-ditto/ditto/pull/2105).

#### Fix MQTT connections eagerly resolving DNS causing problems with dynamic DNS

PR [#2109](https://github.com/eclipse-ditto/ditto/pull/2109) provides a configuration for changing the behavior of 
MQTT connections in Ditto which eagerly resolved MQTT broker hostnames.  
This could have lead to issues when e.g. the MQTT broker was behind a dynamic DNS or often changed IP addresses behind
a DNS hostname.  
This resolves issue [#2108](https://github.com/eclipse-ditto/ditto/issues/2108).
