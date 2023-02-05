---
title: Release notes 2.0.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.0.1 of Eclipse Ditto, released on 20.05.2021"
permalink: release_notes_201.html
---

This is a bugfix release, no new features since [2.0.0](release_notes_200.html) were added.

## Changelog

Compared to the latest release [2.0.0](release_notes_200.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.0.1), including the fixed bugs.

#### [Fixed that error responses in WS contained wrong topic path](https://github.com/eclipse-ditto/ditto/pull/1057)

The Ditto WebSocket returned a wrong `topic` for DittoProtocol messages for error responses.

#### [Optimized MQTT protocol level acknowledgements](https://github.com/eclipse-ditto/ditto/pull/1064)

When using MQTT option `"reconnectForRedelivery"`, the downtime during the reconnect was optimized to be very small
in order to lose only few "QoS 0" messages.

#### [Made AckUpdater work with ddata sharding](https://github.com/eclipse-ditto/ditto/pull/1063)

"Weak" Acknowledgements were broken in 2.0.0 when Ditto was operated in a cluster with more than 1 instances.

#### [Fixed write-concern for commands requesting "search-persisted" ACK](https://github.com/eclipse-ditto/ditto/pull/1059)

The Ditto search was updated with a wrong write concern which caused higher search update times.  
In addition, requests with "search-persisted" acknowledgement used the wrong write concern as well which could have
caused search inconsistencies. 

#### [Fixed that logging was not configurable](https://github.com/eclipse-ditto/ditto/pull/1066)

Previously, there were no options to configure logging for Ditto - this was fixed and it is possible to
either configure a "logstash" endpoint or files based log appending.
