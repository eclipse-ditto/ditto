---
title: Release notes 0.9.0-M1
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.9.0-M1 of Eclipse Ditto, released on 28.03.2019"
permalink: release_notes_090-M1.html
---

After the first release of Ditto [0.8.0](release_notes_080.html), this first milestone of the "0.9.0" release provides
a preview of what to expect in the next release.


## Changes

### [Optimized/reduced memory consumption of ditto-json](https://github.com/eclipse-ditto/ditto/pull/304)

`ditto-json` used quite a lot of memory when building up JSON structures - this change optimized that by a factor of 
10-20, so now it is possible to keep ~1.000.000 things in memory with ~4GB of memory.

### [Update several dependencies](https://github.com/eclipse-ditto/ditto/issues/300)

Several used dependencies were updated to their latest (bugfix) releases or even to a stable 1.x version.


## New features

### [Erasing data without downtime](https://github.com/eclipse-ditto/ditto/issues/234)

GDPR requires erasure of data on request of data subject.

### [Enhance placeholders by functions](https://github.com/eclipse-ditto/ditto/issues/337)

The already existing placeholder mechanism for connections was enhanced by optionally adding function calls (currently 
simple string manipulations like `fn:substring-before(':')`).

### [Connect to Apache Kafka](https://github.com/eclipse-ditto/ditto/issues/224)

In order to publish Ditto protocol messages (e.g. events/responses/messages/...) to Apache Kafka topics.

### [Collect and provide connection metrics](https://github.com/eclipse-ditto/ditto/issues/317)

Provide metrics about established connections (e.g. to AMQP 1.0, Kafka, MQTT, ...).


## Bugfixes

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.9.0-M1+).

