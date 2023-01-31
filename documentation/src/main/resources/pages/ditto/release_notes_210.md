---
title: Release notes 2.1.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.1.0 of Eclipse Ditto, released on 27.09.2021"
permalink: release_notes_210.html
---

Ditto **2.1.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Eclipse Ditto 2.1.0 includes the following topics/enhancements:

* Support consuming messages from Apache Kafka -> completing the Apache Kafka integration as fully supported Ditto managed connection type
* Conditional requests (updates + retrievals)
* Enrichment of extra fields for ThingDeleted events
* Support for using (HTTP) URLs in Thing and Feature "definition" fields, e.g. linking to WoT (Web of Things) Thing Models
* HMAC based authentication for Ditto managed connections
* SASL authentication for Azure IoT Hub
* Publishing of connection opened/closed announcements
* Addition of new "misconfigured" status category for managed connections indicating that e.g. credentials are wrong or connection to endpoint could not be established to to configuration problems
* Support "at least once" delivery for policy subject expiry announcements

The following notable fixes are included:

* Fix "search-persisted" acknowledgement not working for thing deletion
* Fix reconnect loop to MQTT brokers when using separate MQTT publisher client

The following non-functional work is also included:

* Support for tracing reporting traces to an "Open Telemetry" endpoint
* Improving cluster failover and coordinated shutdown + rolling updates
* Logging improvements, e.g. configuring a logstash server to send logs to or more options to configure a logging file appender
* Improving background deletion of dangling DB journal entries / snapshots based on the current MongoDB load
* Improving search update by applying "delta updates" saving lots of bandwidth to MongoDB
* Reducing cluster communication for search updates using a smart cache

<br/>

For a complete list of all merged PRs, inspect the following milestones:
* [merged pull requests for milestone 2.1.0-M1](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.1.0-M1)
* [merged pull requests for milestone 2.1.0-M2](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.1.0-M2)
* [merged pull requests for milestone 2.1.0-M3](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.1.0-M3)
* [merged pull requests for milestone 2.1.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.1.0)

<br/>
<br/>

Compared to the latest release [2.0.0](release_notes_200.html), the following most notable changes, new features and
bugfixes were added.

### Changes

#### [Support using URLs in "definitions"](https://github.com/eclipse-ditto/ditto/pull/1185)

In addition to using the currently supported pattern `<namespace>:<name>:<version>` in 
[thing definitions](basic-thing.html#definition) and 
[feature definitions](basic-feature.html#feature-definition), the definitions now additionally support saving HTTP URLs.

This is useful to link a Thing to a model definition available via an HTTP URL, which could for example be a 
[WoT (Web of Things) Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model).

A discussion of how a WoT "Thing Model" can be mapped to an Eclipse Ditto "Thing" is currently 
[ongoing on GitHub](https://github.com/eclipse-ditto/ditto/discussions/1163), please join in to provide feedback.

#### [Addition of category "misconfigured" for current connection status](https://github.com/eclipse-ditto/ditto/pull/1151)

When [retrieving the connection status](connectivity-manage-connections.html#retrieve-connection-status), a connection 
was previously in status `"failed"` when e.g. the credentials to an endpoint were wrong or a queue in a message broker
was not existing.

By adding a new status `"misconfigured"` such configuration errors are now differentiable from real "failures".

#### [Tracing support, reporting traces to an "Open Telemetry" endpoint](https://github.com/eclipse-ditto/ditto/issues/1135)

Eclipse Ditto can now optionally be configured to enable [tracing](installation-operating.html#tracing) and to report
traces to an "Open Telemetry" endpoint.  
Reading and propagating [W3C trace context](https://www.w3.org/TR/trace-context/) headers at the
edges of the Ditto service (e.g. Gateway and Connectivity service) is also supported.

#### Improving cluster failover and coordinated shutdown + rolling updates

Several stabilization efforts were done in order to improve rolling updates of running Eclipse Ditto with minimal impact
to end users.

#### Logging improvements

E.g. support configuration of a [logstash endpoint or a custom file appender](installation-operating.html#logging).

#### [Improving background deletion of dangling DB journal entries / snapshots based on the current MongoDB load](https://github.com/eclipse-ditto/ditto/pull/1168)

The deletion of dangling / no longer needed DB entries was improved to react better to the current load of the database.

#### [Improving search update by applying "delta updates" saving lots of bandwidth to MongoDB](https://github.com/eclipse-ditto/ditto/pull/1174)

Previous versions of Ditto updated the documents in the search index always completely, also if only one value of a thing
was modified.  
This is now drastically improved so that only changed values are updated in the search index which saves a lot of 
network traffic towards the database.

#### [Reducing cluster communication for search updates using a smart cache](https://github.com/eclipse-ditto/ditto/pull/1150)

Minimized internal cluster roundtrips to load thing data in order to calculate the search index documents by using a 
cache.


### New features

#### [Support consuming messages from Apache Kafka](https://github.com/eclipse-ditto/ditto/issues/586)

Implemented connection sources for Apache Kafka connections.  
This latest addition completes the Apache Kafka integration in Eclipse Ditto and can utilize Ditto's 
[acknowledgements](basic-acknowledgements.html) in order to guarantee processing messages with backpressure applied from
Kafka topics.

Learn about all options of [Ditto managed Kafka connections](connectivity-protocol-bindings-kafka2.html) in order to
integrate your favorite Digital Twin framework with your favorite event streaming platform.

When updating from a Ditto version where Kafka was used before, please notice and follow the 
[migration notes](#adapt-kafka-client-configuration) on Kafka related configuration changes.

#### [Conditional requests (updates + retrieves)](https://github.com/eclipse-ditto/ditto/issues/559)

Utilizing the existing [RQL](basic-rql.html) filters, it is now possible to specify a `condition` when performing any of
operations (like modifications or retrievals) on a thing.

Using a "conditional update", you may for example specify to update a property of a thing only if it changed.  
Or, using a "conditional retrieve", to only retrieve a bigger payload from the twin if a separately managed timestamp is 
older than a specified timestamp in the condition.

Please have a look at our [blog post](2021-09-23-conditional-requests.html) about this new feature and the newly added 
[documentation of conditions](basic-connections.html) to find out more.

#### [Enrichment of "ThingDeleted" events with extra fields](https://github.com/eclipse-ditto/ditto/pull/1184)

Previously, Eclipse Ditto could not [enrich](basic-enrichment.html) `ThingDeleted` events with additional data from the
deleted thing - this now is supported.

#### [HMAC based authentication for Ditto managed connections](https://github.com/eclipse-ditto/ditto/issues/1060)

For better integration with Microsoft's Azure and Amazon's AWS ecosystems, Eclipse Ditto HTTP connections now support 
HMAC-based authentication methods such as that of "Azure Monitor Data Collector" and "AWS Signature Version 4".

Please read the [blog post](2021-06-17-hmac-credentials.html) to learn more about that.

#### [SASL authentication for Azure IoT Hub](https://github.com/eclipse-ditto/ditto/issues/1078)

For better integration with "Azure IoT Hub", Eclipse Ditto AMQP and HTTP connections now support its shared 
access signature authentication.

#### [Publishing of connection opened/closed announcements](https://github.com/eclipse-ditto/ditto/issues/1052)

An Eclipse Ditto connection can now be configured to send an 
[announcement](protocol-specification-connections-announcement.html) to the target endpoint, when the connection
is opening and when it is closing again (e.g. during a restart of Eclipse Ditto).

#### [Support "at least once" delivery for policy subject deletion announcements](https://github.com/eclipse-ditto/ditto/issues/1107)

Policy subject [deletion announcements](basic-policy.html#subject-deletion-announcements) can now be configured to be 
delivered "at least once", using Eclipse Ditto's built in [acknowledgement](basic-acknowledgements.html) mechanism.


### Bugfixes

Several bugs in Ditto 2.0.x were fixed for 2.1.0.  
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.1.0), including the fixed bugs.

#### [Fix "search-persisted" acknowledgement not working for thing deletion](https://github.com/eclipse-ditto/ditto/pull/1141)

Previously, when requesting the built-in [acknowledgement label "search-persisted"](basic-acknowledgements.html#built-in-acknowledgement-labels)
for thing deletions the response did not wait for the deletion in the search index which has been fixed now.

#### [Fix reconnect loop to MQTT brokers when using separate MQTT publisher client](https://github.com/eclipse-ditto/ditto/pull/1117)

Eclipse Ditto 2.0.0 could run into an endless reconnect loop to an MQTT broker when a separate publisher client was 
configured. This has been fixed.


## Migration notes

### Adapt Kafka client configuration

The connection configuration for Kafka producers was changed in order to align it with the new consumer properties.  
All properties moved from `ditto.connectivity.connection.kafka.producer.internal.kafka-clients` to
`ditto.connectivity.connection.kafka.producer`.

The complete configuration looks like:

```hocon
ditto.connectivity.connection {
  kafka {
    consumer {

    }

    producer {
      connections.max.idle.ms = 540000
      reconnect.backoff.max.ms = 10000
      reconnect.backoff.ms = 500
      acks = "1"
      retries = 0
      request.timeout.ms = 10000
      delivery.timeout.ms = 10000
      max.block.ms = 10000
    }
  }
}
```

## Ditto clients

For a complete list of all merged client PRs, inspect the following milestones:
* [merged pull requests for milestone 2.1.0-M1](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.1.0-M1)
* [merged pull requests for milestone 2.1.0-M2](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.1.0-M2)
* [merged pull requests for milestone 2.1.0-M3](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.1.0-M3)
* [merged pull requests for milestone 2.1.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.1.0)

### Ditto Java SDK

#### [Enhance Ditto java client with a "disconnection listener"](https://github.com/eclipse-ditto/ditto-clients/pull/167)

A new disconnection listener can be configured which:
* is called whenever the connection to the Ditto backend was disconnected
* provides a context of who initiated the disconnection + an optional cause
* provides means to e.g. close the client as a result or to perform a reconnect

### Ditto JavaScript SDK

See separate [Changelog](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/CHANGELOG.md) of JS client.


## Roadmap

Looking forward, the current plans for Ditto 2.2.0 are:

* [Add HTTP API for "live" commands](https://github.com/eclipse-ditto/ditto/issues/106)
* [Add support for twin life-cycle events](https://github.com/eclipse-ditto/ditto/issues/898)
* Defining "channel" conditions on API requests against Ditto so that Ditto decides based on the condition whether to 
  use "twin" or "live" channel
* Concept and first work on a WoT (Web of Things) integration

