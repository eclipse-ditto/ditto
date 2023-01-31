---
title: Release notes 1.4.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 1.4.0 of Eclipse Ditto, released on 28.10.2020"
permalink: release_notes_140.html
---

Ditto **1.4.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 1.x versions.

## Changelog

Compared to the latest release [1.3.0](release_notes_130.html), the following changes, new features and
bugfixes were added.


### Changes

#### [Status codes of live responses are no longer interpreted for acknowledgement requests](https://github.com/eclipse-ditto/ditto/pull/833)

Senders of live responses may freely choose the status code, since it no longer affects the technical settlement and redelivery of the corresponding live commands.

#### [The header response-required is always set to false for responses and events](https://github.com/eclipse-ditto/ditto/pull/850)

Ditto sets the header `response-required` to `false` for signals that do not anticipate any responses,
so that the header has a consistent meaning regardless of signal type.

#### [OCSP is optional for connections](https://github.com/eclipse-ditto/ditto/pull/854)

Ditto will establish a connection even if the broker has a revoked certificate according to OCSP.
Failed revocation checks will generate warnings in the connection log.
This is to guard against unavailability of OCSP servers.

#### [Placeholder topic:entityId renamed to topic:entityName](https://github.com/eclipse-ditto/ditto/pull/859)

The placeholder `topic:entityId`  was not named correctly. It was resolved with the
name of an entity and not the complete ID. Therefore, a new placeholder
`topic:entityName` is introduced which reflects correctly what it means.

### New features

#### [Acknowledgement label declaration](https://github.com/eclipse-ditto/ditto/issues/792)

Each subscriber of Ditto signals by Websocket or other connections is required to declare the labels of acknowledgements
it may send. The labels should be unique to the subscriber. Labels of acknowledgements sent via a connection source or
issued by a connection target must be prefixed by the connection ID followed by a colon. This is to prevent racing in
fulfillment of acknowledgement requests and to detect misconfiguration early.

Acknowledgement label declaration is available in [Ditto java client](https://github.com/eclipse-ditto/ditto-clients/pull/98).

### Bugfixes

Several bugs in Ditto 1.3.0 were fixed for 1.4.0.<br/>
This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.4.0), including the fixed bugs.<br/>
Here as well for the Ditto Java Client: [merged pull requests](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is%3Apr+milestone%3A1.4.0)

#### [Thread-safe loggers added](https://github.com/eclipse-ditto/ditto/issues/773)

Concurrency issues in Ditto loggers and logging adapters are addressed by introducing thread-safe variants.

#### [Search via SSE enabled](https://github.com/eclipse-ditto/ditto/issues/822)

Search via SSE was disabled due to incorrect initialization. It is enabled again.

#### [Memory consumption of outgoing AMQP connections limited](https://github.com/eclipse-ditto/ditto/pull/853)

AMQP 1.0 connections to a slow broker could accumulate indefinitely messages yet to be published.
Now only a fixed number of messages are retained.

#### [Java client: Message ordering fixed](https://github.com/eclipse-ditto/ditto-clients/pull/97)

There was a bug in Ditto Java client that may cause messages to be handled in a different order
than when they are received. It made some search results look empty when they are not.

## Migration notes

### Acknowledgement labels need to be declared and unique

- Websocket connections need to declare the labels of any acknowledgments they may send.
  The acknowledgement labels should be declared as comma-separated list in the query parameter `declared-acks`.
  Declared acknowledgement labels should be unique to the Websocket connection. Declaring a duplicate label
  causes the Websocket connection to close after an error in Ditto protocol.

- AMQP and MQTT connection sources need to declare the labels of any acknowledgement they may send as a JSON array
  in the JSON field `declaredAcks`. The acknowledgement labels should be prefixed by the connection ID and a colon.

- Connection targets of all protocols need to prefix their issued acknowledgements by the connection ID and a colon.

Details are in the [documentation](basic-acknowledgements.html#issuing-acknowledgements).
