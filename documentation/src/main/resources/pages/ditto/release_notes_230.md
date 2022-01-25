---
title: Release notes 2.3.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.3.0 of Eclipse Ditto, released on 21.01.2022"
permalink: release_notes_230.html
---

Ditto **2.3.0** is API and [binary compatible](https://github.com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Eclipse Ditto 2.3.0 includes the following topics/enhancements:

* HTTP API for "live" commands
* Smart channel strategy for live/twin read access
* Configurable allowing creation of entities (policies/things) based on namespace and authenticated subjects
* Allow using `*` as a placeholder for the feature id in selected fields

The following notable fixes are included:

* Fix potential concurrent modification errors when using JavaScript payload mapping and global variables
* Fix reconnect backoff for Kafka connections with authentication failures
* Fix caching of JWTs in HTTP push connections
* Fix potentially unreachable client actors in connections with `clientCount > 1`
* Fix search inconsistencies for very active things during shard relocation (e.g. on rolling updates)
* Fix that a Kafka connection with only targets remains "open" even if Kafka broker is not available
* Allow usage of absolute domain paths ending with a "." as Kafka bootstrap servers
* Ensure that Ditto pub/sub state is eventually consistent with a guaranteed upper time limit

The following non-functional work is also included:

* Update of several used dependencies:
  * Akka: `2.6.18`
  * Akka Management: `1.1.2`
  * Caffeine: `3.0.5`
  * Classindex: `3.11`
  * Cloudevents: `2.3.0`
  * HiveMQ MQTT client: `1.3.0`
  * Jackson: `2.12.6`
  * Logback: `1.2.10`
  * SLF4J: `1.7.32`
  * SSL config core: `0.4.3`
  * Typesafe config: `1.4.1`
  * Rhino JS engine: `1.7.14`

<br/>

For a complete list of all merged PRs, inspect the following milestones:
* [merged pull requests for milestone 2.3.0](https://github.com/eclipse/ditto/pulls?q=is:pr+milestone:2.3.0)

<br/>
<br/>

Compared to the latest release [2.2.0](release_notes_220.html), the following most notable changes, new features and
bugfixes were added.


### New features

#### [HTTP API for "live" commands](https://github.com/eclipse/ditto/issues/106)

Ditto's ["live" channel](protocol-twinlive.html#live) is now also available for commands invoked via HTTP API.  
See also the [blogpost covering that topic](2021-12-20-http-live-channel.html).

To qualify a command (e.g. "modify thing" or "retrieve feature property") as a "live" command, the header or 
query parameter `channel=live` has to be specified.

Live commands bypass the twin and go directly to the devices.  
However, an existing twin is still required for policy enforcement.


#### [Smart channel strategy for live/twin read access](https://github.com/eclipse/ditto/issues/1228)

Ditto adds support for selecting the "twin" or the "live" channel for thing query commands based on an 
[RQL condition](basic-rql.html) of a newly added parameter 
[live channel condition](basic-conditional-requests.html#live-channel-condition).  
See also the [blogpost covering that topic](2021-12-22-live-channel-condition.html).

In addition, a new [payload mapper](connectivity-mapping.html#updatetwinwithliveresponse-mapper) automatically updating 
the twin based on received live data from devices was added.

#### [Configurable allowing creation of entities based on namespace and authenticated subjects](https://github.com/eclipse/ditto/pull/1251)

This added feature allows configuring restrictions, which [authenticated subjects](basic-auth.html#authenticated-subjects)
may create new entities (things / policies) in which namespaces.

#### [Allow using `*` as a placeholder for the feature id in selected fields](https://github.com/eclipse/ditto/pull/1277)

When selecting for certain [`fields` of a thing](httpapi-concepts.html#field-selector-with-wildcard) or when using 
[signal enrichment (extraFields)](basic-enrichment.html) in order to add more (unchanged) data from a twin to e.g. events 
the wildcard `*` can now be used in order to select all features of a thing without the need to know their feature names.


### Bugfixes

Several bugs in Ditto 2.2.x were fixed for 2.3.0.  
This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.3.0), including the fixed bugs.


## Migration notes

No migrations required updating from Ditto 2.2.x

## Ditto clients

For a complete list of all merged client PRs, inspect the following milestones:
* [merged pull requests for milestone 2.3.0](https://github.com/eclipse/ditto-clients/pulls?q=is:pr+milestone:2.3.0)

### Ditto Java SDK

No mentionable changes/enhancements/bugfixes.

### Ditto JavaScript SDK

See separate [Changelog](https://github.com/eclipse/ditto-clients/blob/master/javascript/CHANGELOG.md) of JS client.


## Roadmap

Looking forward, the current plans for Ditto 2.4.0 are:

* Update service code to Java 17 (APIs stay at Java 8) + run Ditto containers with Java 17 runtime
* Continue work on the started [WoT (Web of Things) integration](https://github.com/eclipse/ditto/pull/1270)
