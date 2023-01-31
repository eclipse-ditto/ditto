---
title: Release notes 2.1.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.1.1 of Eclipse Ditto, released on 28.10.2021"
permalink: release_notes_211.html
---

This is a bugfix release, no new features since [2.1.0](release_notes_210.html) were added.

## Changelog

Compared to the latest release [2.1.0](release_notes_210.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.1.1), including the fixed bugs.

In addition, this is a complete list of the
[merged Ditto Client pull requests](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is%3Apr+milestone%3A2.1.1), including the fixed bugs.

#### [Stabilize connection live status for AMQP 1.0 connections](https://github.com/eclipse-ditto/ditto/pull/1206)

The connection live status was not always correct for AMQP 1.0 connections which were `misconfigured`.

#### [Fixed drop behavior of mapping queue in LegacyBaseConsumerActor](https://github.com/eclipse-ditto/ditto/pull/1195)

Connections using the `LegacyBaseConsumerActor` like AMQP 0.9.1, MQTT, HTTP did silently drop messages if the configured
buffer size was reached and backpressure was applied.  
This is now logged as warning and a retry is invoked.

#### [Fix status 500 when sorting a field containing non-primitive values](https://github.com/eclipse-ditto/ditto/pull/1207)

When sorting on e.g. JSON objects in the things-search a HTTP status 500 was returned.

#### [Explicitly configure MongoDB query batchSize same as the limit](https://github.com/eclipse-ditto/ditto/pull/1211)

The used MongoDB driver used a max `batchSize` of `16` for performing searches. When the requested result size was 
higher than 16, this lead to multiple roundtrips/batches for a search resulting in not ideal performance.

#### [Fix mqtt connection status for sources with multiple addresses](https://github.com/eclipse-ditto/ditto/pull/1214)

The connection live status for MQTT connections with sources containing multiple addresses contained `failure` status
entries for "missing" sources.

#### Ditto Java Client: [Shutdown executor after stream cancellation](https://github.com/eclipse-ditto/ditto-clients/pull/174)

The Ditto Java client had a thread leak which is now fixed.