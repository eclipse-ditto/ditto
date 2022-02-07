---
title: Release notes 2.2.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.2.1 of Eclipse Ditto, released on 15.12.2021"
permalink: release_notes_221.html
---

This is a bugfix release, no new features since [2.2.0](release_notes_220.html) were added.

## Changelog

Compared to the latest release [2.2.0](release_notes_220.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.2.1), including the fixed bugs.

#### [Update logback to 1.2.8 due to "possibility of vulnerability"](https://github.com/eclipse/ditto/pull/1253)

The reported [LOGBACK-1591](https://jira.qos.ch/browse/LOGBACK-1591) reports a "Possibility of vulnerability" with
a medium severity.

#### [Switch to ByteSerializer and ByteDeserializer for Kafka Consumer and Publisher](https://github.com/eclipse/ditto/pull/1241)

With Ditto 2.2.0, when consuming binary messages from Apache Kafka, the charset was not considered correctly and
therefore binary payload (e.g. protobuf messages) were not consumed correctly.  
That was fixed by using the binary deserializer.

#### [Also disable hostname verification when HTTP connection wants to ignore SSL](https://github.com/eclipse/ditto/pull/1243)

For [managed HTTP connections](connectivity-protocol-bindings-http.html) for which `validateCertificates` was disabled,
single HTTP interactions when publishing messages were still using certificate validation.  
This has been fixed.
