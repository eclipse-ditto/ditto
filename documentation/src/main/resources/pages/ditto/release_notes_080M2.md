---
title: Release notes 0.8.0-M2
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.8.0-M2 of Eclipse Ditto, released on 27.09.2018"
permalink: release_notes_080-M2.html
---

This milestone release focuses mainly on the newly added MQTT connectivity. It brings Ditto an important step closer 
to the [first planned release 0.8.0](https://projects.eclipse.org/projects/iot.ditto/releases/0.8.0).

Since the last milestone of Eclipse Ditto [0.8.0-M1](release_notes_080-M1.html), the following changes, new features and
bugfixes were added.


## Changes

### [Define and enforce max. entity sizes in Ditto cluster](https://github.com/eclipse-ditto/ditto/issues/221)

In previous versions of Ditto the entity sizes (e.g. for things and policies) were technically not limited. The only 
implicit limit was a max. cluster message size of 10m. As Ditto is not intended to manage digital twins which are that
big, the sizes of the twins are now by default limited to:
* max [Thing](basic-thing.html) size: 100k
* max [Policy](basic-policy.html) size: 100k
* max [Message payload](basic-messages.html) size: 250k

They can be adjusted to other needs via the configuration located in 
[ditto-limits.conf](https://github.com/eclipse-ditto/ditto/blob/master/services/base/src/main/resources/ditto-limits.conf).

The default maximum frame size in the ditto cluster was changed to 256k and can be adjusted in the 
[ditto-akka-config.conf](https://github.com/eclipse-ditto/ditto/blob/master/services/base/src/main/resources/ditto-akka-config.conf#L93).


## New features

### [MQTT support](https://github.com/eclipse-ditto/ditto/issues/220)

In two big PRs ([#225](https://github.com/eclipse-ditto/ditto/pull/225) and [#235](https://github.com/eclipse-ditto/ditto/pull/235)) 
Ditto added support for connecting to MQTT brokers (like for example [Eclipse Mosquitto](https://mosquitto.org)) via its
[connectivity feature](connectivity-overview.html). Have a look at the 
[MQTT protocol binding](connectivity-protocol-bindings-mqtt.html) for details.

### [Subscribing for change notifications by optional filter](https://github.com/eclipse-ditto/ditto/issues/149)

Until previous versions, when [subscribing for changes](basic-changenotifications.html), the subscriber always got all
changes he was entitled to see (based on [access control](basic-auth.html)). Now it is possible to specify for which
changes to subscribe based on the optional `namespaces` to consider and an optional [RQL](basic-rql.html) `filter`.
The new feature is documented as [change notification filters](basic-changenotifications.html#filtering).

### [Support for conditional requests](https://github.com/eclipse-ditto/ditto/pull/226)

Ditto's APIs now support `If-Match` and `If-None-Match` headers specified in 
[rfc7232](https://tools.ietf.org/html/rfc7232#section-3.2) for `things` and `policies` resources. Have a look at the
new [documentation for conditional requests](https://www.eclipse.org/ditto/httpapi-concepts.html) for how this concept
can help with advanced interaction-patterns with your twins.


## Bugfixes

### [Reconnection to AMQP 0.9.1 connections](https://github.com/eclipse-ditto/ditto/issues/228)

Reconnection did not always work, e.g. when the AMQP 0.9.1 broker was not reachable for a while.

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.8.0-M2+).
