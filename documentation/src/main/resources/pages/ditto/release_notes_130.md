---
title: Release notes 1.3.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 1.3.0 of Eclipse Ditto, released on 30.09.2020"
permalink: release_notes_130.html
---

Ditto **1.3.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 1.x versions.

## Changelog

Compared to the latest minor release [1.2.0](release_notes_120.html), the following changes, new features and
bugfixes were added.


### Changes

#### [Update Akka, Akka HTTP and Scala to latest versions](https://github.com/eclipse-ditto/ditto/issues/774)

The core libraries Ditto is built on were updated to their latest versions which should improve cluster stability
and overall performance.

#### [Removed OWASP security headers](https://github.com/eclipse-ditto/ditto/pull/804)

Setting the [OWASP recommended secure HTTP headers](https://owasp.org/www-project-secure-headers/) 
(e.g. `X-Frame-Options`, `X-Content-Type-Options`, `X-XSS-Protection`) was removed from the Ditto codebase as such 
headers are typically set in a reverse proxy (e.g. nginx) or in a cloud loadbalancer in front of Ditto. 

#### [Ditto Java Client: Changed default initial reconnect behavior](https://github.com/eclipse-ditto/ditto-clients/pull/86)

A newly created configuration was added whether a Ditto Java Client should retry connecting to a Ditto backend
even when the initial connection attempt failed 
(see the `initialConnectRetryEnabled(boolean)` option on the `WebSocketMessagingConfiguration` builder).
 

{% include warning.html content="The default behavior was changed so that a Ditto Java Client **does not reconnect** 
    when the initial connection to a Ditto WebSocket failed! **Have a look at [the migration notes](#migration-notes) 
    in order to restore the old behavior when updating to Ditto Java Client 1.3.0**." %}


Previously, up to Ditto Java Client 1.2.x, the client always retried connecting, even when the initial connection 
attempt failed.<br/>
We got feedback that this is not always desirable, e.g. when the credentials are wrong during development, the initial
connection should fail with an exception instead, so this is the new default behavior.


### New features

#### [Automatic creation of things](https://github.com/eclipse-ditto/ditto/issues/760)

Added a [payload mapper](connectivity-mapping.html) for connectivity which implicitly creates a new digital twin (thing)
for incoming messages: [ImplicitThingCreation Mapper](connectivity-mapping.html#implicitthingcreation-mapper).

This is very useful when e.g. a device connectivity layer (like [Eclipse Hono](https://eclipse.org/hono/)) also 
automatically creates connected devices, for example when a new device connects for the first time to an IoT gateway.

This new feature can work together with the 
[Hono feature for implicit registration of devices connected via gateways](https://github.com/eclipse/hono/issues/2053).

#### [Use response of HTTP push connections as live message response](https://github.com/eclipse-ditto/ditto/pull/809)

When [HTTP connections](connectivity-protocol-bindings-http.html) there are now several options 
to respond to published [live messages](protocol-specification-things-messages.html): 
[Responding to messages]([HTTP connections](connectivity-protocol-bindings-http.html#responding-to-messages)

For example, it is possible to use the HTTP response of the foreign HTTP endpoint (Webhook) as Ditto live message 
response.

#### [Raw payload mapper to enable raw pass-through of live messages](https://github.com/eclipse-ditto/ditto/issues/777)

Added a [payload mapper](connectivity-mapping.html) for connectivity which converts consumed messages via connectivity 
in "raw mode": [RawMessage mapper](connectivity-mapping.html#rawmessage-mapper).
 
This mapper creates a [Ditto Protocol live message](protocol-specification-things-messages.html) from consumed messages
preserving the payload (e.g. JSON or text, binary, etc.) and publishing that message again to interested subscribers.

This can be useful for connections which only need Ditto to forward a message to e.g. another connection or to a 
WebSocket.


### Bugfixes

Several bugs in Ditto 1.2.x were fixed for 1.3.0.<br/>
This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.3.0), including the fixed bugs.<br/>
Here as well for the Ditto Java Client: [merged pull requests](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is%3Apr+milestone%3A1.3.0)

#### [Responses from HTTP /messages API were JSON escaped](https://github.com/eclipse-ditto/ditto/issues/805)

With Ditto 1.2.0 HTTP responses to the `POST /messages` APIs which transported `application/json` were falsely JSON 
escaped. As the fix for that had to be done in several steps and at several places, the fix is not backported to the 
Ditto 1.2.0 line and it is suggested to update to Ditto 1.3.0 right away, if affected by this bug.

#### [Putting `_metadata` while creating a Thing does not work bug](https://github.com/eclipse-ditto/ditto/issues/801)

When putting [Metadata](basic-metadata.html) as part of a "create thing" API call, the metadata was not applied. Only
when updating an existing thing, the metadata was applied.

#### [Ditto Java Client: threads leakage](https://github.com/eclipse-ditto/ditto-clients/pull/87)

The Ditto Java client did not close/cleanup its threadpools when closing the client.


## Migration notes

### Check `initialConnectRetryEnabled` option when upgrading to Ditto Java Client 1.3.0

If you require that you Ditto Java Client reconnects even when the first connection attempt failed (this might e.g.
be useful when you use the client in a backend application which might be restarted or scaled up/down at any given time),
please configure the `initialConnectRetryEnabled(boolean)` to `true`:

```java
AuthenticationProvider authenticationProvider = ...;

MessagingProvider messagingProvider =
    MessagingProviders.webSocket(WebSocketMessagingConfiguration.newBuilder()
        .endpoint("wss://ditto.eclipseprojects.io")
        .initialConnectRetryEnabled(true) // set this to true in order to enable retry on initial connection errors
        .build(), authenticationProvider);

DittoClient client = DittoClients.newInstance(messagingProvider);

// ...
```
