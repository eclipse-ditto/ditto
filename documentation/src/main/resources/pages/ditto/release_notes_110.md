---
title: Release notes 1.1.0
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.0 of Eclipse Ditto, released on 29.04.2020"
permalink: release_notes_110.html
---

The first minor (feature adding) release of Eclipse Ditto 1 is finally here: **1.1.0**.

It is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to Eclipse Ditto 1.0.0.

## Changelog

Compared to the latest release [1.0.0](release_notes_100.html), the following changes, new features and
bugfixes were added.


### Changes

#### [Java 11 as runtime environment](https://github.com/eclipse-ditto/ditto/issues/308)

The default Java runtime for Ditto's Docker containers was switched from Java 8 to Java 11 which should have some 
benefits in storing Strings in memory (this was already added in Java 9).

Language features of newer Java versions can now be used in the "services" part of Ditto, the Java APIs and models relevant 
for [semantic versioning](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md) 
are still compatible to Java 8.

#### [CBOR as Ditto internal serialization provider](https://github.com/eclipse-ditto/ditto/pull/598)

As a bachelor thesis, [Erik Escher](https://github.com/erikescher) evaluated mechanisms to improve the serialization 
overhead done in Ditto clusters.

His findings using [CBOR](https://cbor.io) as an alternative to plain JSON resulted an approximate 10% improvement on 
roundtrip times and throughput.
The Ditto team was happy to accept his pull request, again improving overall performance in Ditto.

#### [More strict Content-Type parsing for HTTP request payload](https://github.com/eclipse-ditto/ditto/pull/650)

In the past, Ditto did not evaluate the HTTP `Content-Type` header of HTTP requests sending along payload. As this 
can be a potential security issue (e.g. in scope of CORS requests), the `Content-Type` is now strictly enforced to
be of `application/json` wherever Ditto only accepts JSON request payload. 


### New features

#### [Management of policies via Ditto Protocol and in Java client](https://github.com/eclipse-ditto/ditto/issues/554)

The [policy](basic-policy.html) entities can now - in addition to their HTTP API - be managed via the 
[Ditto Protocol](protocol-specification-policies.html). That means also via 
[WebSocket](httpapi-protocol-bindings-websocket.html) and [connections](basic-connections.html) (e.g. AMQP, MQTT, ..).

APIs for [policy management](client-sdk-java.html#manage-policies) were also added to the 
[Ditto Java Client](https://github.com/eclipse-ditto/ditto-clients/pull/46).

#### [Searching things via Ditto Protocol and in Java client](https://github.com/eclipse-ditto/ditto/issues/575)

New [Ditto Protocol for search](protocol-specification-things-search.html) was added in order to define a search query
via the Ditto Protocol and also get results via an asynchronous channel. As a result, searching for things is now also
possible via [WebSocket](httpapi-protocol-bindings-websocket.html) and [connections](basic-connections.html) 
(e.g. AMQP, MQTT, ..).

APIs for [searching things](client-sdk-java.html#search-for-things) were also added to the 
[Ditto Java Client](https://github.com/eclipse-ditto/ditto-clients/pull/53).

#### [Enriching messages and events before publishing to external subscribers](https://github.com/eclipse-ditto/ditto/issues/561)

When subscribing [change notifications](basic-changenotifications.html) or for messages to publish to external system or
deliver via [WebSocket](httpapi-protocol-bindings-websocket.html) it is now possible to [enrich](basic-enrichment.html) 
the payload with additional "extra fields" from the thing which was affected by the change.

This can be useful when e.g. only a sensor value of a device changes, but your application also needs to be aware of 
additional context of the affected thing (e.g. a location which does not change with each sensor update).

APIs for [enriching changes](client-sdk-java.html#subscribe-to-enriched-change-notifications) were also added to the 
[Ditto Java Client](https://github.com/eclipse-ditto/ditto-clients/pull/43).

#### [Establish connections to MQTT 5 brokers](https://github.com/eclipse-ditto/ditto/issues/561)

The Ditto community (namely [Alexander Wellbrock (w4tsn)](https://github.com/w4tsn) from 
[othermo GmbH](https://www.othermo.de)) contributed MQTT 5 support to Ditto's connectivity capabilities.<br/>
With that is is possible to also establish connections to MQTT 5 brokers and even apply 
[header mapping](connectivity-header-mapping.html) and e.g. responses via MQTT 5's `user properties` approach.

Thank you very much for this great contribution.

#### [End-2-end acknowledgements](https://github.com/eclipse-ditto/ditto/issues/611)

Until now, messages consumed by Eclipse Ditto were processed without a guarantee. That is being addressed with this
first feature addition, the model and logic in order to request and emit [acknowledgements](basic-acknowledgements.html).

The follow-up issue [#661](https://github.com/eclipse-ditto/ditto/issues/661) will automatically handle acknowledgements 
in Ditto managed connections, configured for connection sources and targets, providing QoS 1 (at least once) semantic
for message processing in Ditto via connections.

APIs for [requesting and issuing acknowledgements](client-sdk-java.html#request-and-issue-acknowledgements) were also 
added to the [Ditto Java Client](https://github.com/eclipse-ditto/ditto-clients/pull/56).

#### [Pre-authenticated authentication mechanism](https://github.com/eclipse-ditto/ditto/issues/560)

Officially added+[documented](installation-operating.html#pre-authentication) support of how Ditto external 
authentication providers may be configured to authenticate users in Ditto by adding them as an HTTP reverse proxy in 
front of Ditto.


### Deprecations

#### [API version 1 deprecation](https://github.com/eclipse-ditto/ditto/pull/608)

Now that Ditto has a full replacement for ACLs, namely [policies](basic-policy.html) which now can 
also be managed via the [Ditto Protocol](protocol-specification-policies.html) and the 
[Ditto Java client](client-sdk-java.html), it is time to deprecate the APIs around the ACL mechanism.

Starting with Ditto 1.1.0, usage of the API in version `1` (e.g. contained in HTTP URLs as `/api/1/things...`) is 
deprecated.<br/>
API version 1 and ACLs will very likely be removed in Ditto `2.0`.

So when you start using Ditto, please make sure to use API version `2` (using policies as 
[authorization mechanism](basic-auth.html#authorization)) from the very beginning.


### Bugfixes

Several bugs in Ditto 1.0.0 were fixed for 1.1.0.<br/>
This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.1.0), including the fixed bugs.


## Migration notes

Do not apply when updating from Eclipse Ditto 1.0.0 to 1.1.0.