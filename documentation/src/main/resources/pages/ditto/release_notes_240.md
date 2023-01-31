---
title: Release notes 2.4.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.4.0 of Eclipse Ditto, released on 14.04.2022"
permalink: release_notes_240.html
---

Ditto **2.4.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Eclipse Ditto 2.4.0 includes the following topics/enhancements:

* W3C WoT (Web of Things) integration
* SSE (ServerSentEvent) API for subscribing to messages
* Recovery status for connections indicating when e.g. recovery is no longer tried after max backoff
* Enhance placeholders to resolve to multiple values
* Advanced JWT placeholder operations
* Support for a wildcard/placeholder identifying the changed feature in order to enrich e.g. its definition

The following notable fixes are included:

* Several fixes and improvements regarding consistency and performance of search updates
* Don't publish messages with failed enrichments and issue failed ack
* Filter for incorrect element types in jsonArray of feature definitions
* Fix of placeholder resolvment in "commandHeaders" of "ImplicitThingCreation" mapper
* Fix `fn:substring-after()` function returning incorrect data

The following non-functional work is also included:

* Upgrade of compiler target level for service modules from Java 11 to Java 17
* Switch of used Java runtime in pre-built Docker containers from OpenJ9 to Hotspot
* Publication of pre-built multi-architecture Docker images for `linux/amd64` (as always) and now in addition `linux/arm64`
* Removal of rate limiting / throttling limits as default
* Update of several used dependencies

<br/>

For a complete list of all merged PRs, inspect the following milestones:
* [merged pull requests for milestone 2.4.0-M1](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.4.0-M1)
* [merged pull requests for milestone 2.4.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.4.0)

<br/>
<br/>

Compared to the latest release [2.3.0](release_notes_230.html), the following most notable changes, new features and
bugfixes were added:


### Changes

#### [Upgrade to Java 17 + change of Java runtime to Hotspot](https://github.com/eclipse-ditto/ditto/issues/1283)

We upgraded the compiler target level for our service modules from 11 to 17 and also use a Java 17 runtime environment
for our service containers. Please note that the Ditto model still remains compatible to Java 8.  
This change only affects you when you're building and deploying Ditto on your own.

#### Publication of pre-built multi-architecture Docker images

Acknowledging the raise of the ARM processor architecture, starting with Ditto 2.4.0, 
Docker images for the following architectures will be published to docker.io:
* `linux/amd64`
* `linux/arm64` (new)

#### [Removal of rate limiting / throttling limits as default](https://github.com/eclipse-ditto/ditto/pull/1324)

By default, Ditto had configurations in place to rate limit consumption of messages received via:
* AMQP 1.0 connections
* Apache Kafka connections
* the WebSocket endpoint

These limitations are now by default disabled (as they mainly make sense for a multi-tenant environment) 
and can be enabled manually, as mentioned in the [configuration - rate limiting section](installation-operating.html#rate-limiting).


### New features

#### [W3C WoT (Web of Things) integration](https://github.com/eclipse-ditto/ditto/issues/1034)

Ditto adds and optional (and currently *experimental*) integration of the 
[Web of Things (WoT) Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/) specification.

Digital twins managed by Ditto can reference to WoT "Thing Models" (being accessible via an HTTP URL) in which the
capabilities of the twin are defined.  
Using this linked model, Ditto can generate a WoT "Thing Description" on a thing instance level and e.g. generate a JSON
skeleton upon creation of a thing.  
For more details, please have a look at the [blogpost](2022-03-03-wot-integration.html) and the 
[WoT integration documentation](basic-wot-integration.html).

#### [SSE (ServerSentEvent) API for subscribing to messages](https://github.com/eclipse-ditto/ditto/issues/1186)

Messages to or from a digital twin can now be subscribed to with the [SSE endpoint](httpapi-sse.html), either on 
[Thing level](httpapi-sse.html#subscribe-for-messages-for-a-specific-thing) or for a specific 
[Feature](httpapi-sse.html#subscribe-for-messages-of-a-specific-feature-of-a-specific-thing)

#### [Recovery status for connections indicating when e.g. recovery is no longer tried after max backoff](https://github.com/eclipse-ditto/ditto/pull/1336)

The new recovery status contains one of the values:
* ongoing
* succeeded
* backOffLimitReached
* unknown

and can be used to find out whether an automatic failover is still ongoing or if the max amount of configured reconnects 
applying backoff was reached and that recovery is no longer happening.

#### [Enhance placeholders to resolve to multiple values](https://github.com/eclipse-ditto/ditto/pull/1331)

Placeholders may now resolve to multiple values instead of only a single one which enables e.g. applying 
[placeholder functions](basic-placeholders.html#function-expressions) to each element of an array.

#### [Advanced JWT placeholder operations](https://github.com/eclipse-ditto/ditto/pull/1309)

Using the above feature of placeholders being resolved to multiple values, the JWT placeholder, which can be used
in [scope of the OpenID connect configuration](basic-placeholders.html#scope-openid-connect-configuration), can now 
be used with [functions](basic-placeholders.html#function-expressions).  
This can e.g. be used in order to filter out unwanted subjects in the 
[OpenId connect configuration](installation-operating.html#openid-connect), or to additionally split a JWT claim into 
several values using the new `fn:split(' ')` function.

Example extracting only subjects from a JSON array "roles" contained in a JWT ending with "moderator": 
```
{%raw%}{{ jwt:extra/roles | fn:filter('like','*moderator') }}{%endraw%}
```

#### [Support for a wildcard/placeholder identifying the changed feature in order to enrich e.g. its definition](https://github.com/eclipse-ditto/ditto/issues/710)

Using the above feature of placeholders being resolved to multiple values, it is now possible to use a placeholder 
`{%raw%}{{ feature:id }}{%endraw%}` as part of an [enrichment `extraFields` pointer](basic-enrichment.html) resolving
to all affected feature ids of a change.

This can e.g. be used to enrich all the feature `definition`s of all modified features in an event which should be 
published via websocket or a connection:
```
{%raw%}extraFields=features/{{feature:id}}/definition{%endraw%}
```


### Bugfixes

Several bugs in Ditto 2.3.x were fixed for 2.4.0.
This is a complete list of the
[merged pull requests for 2.4.0-M1](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.4.0-M1) and 
[merged pull requests for 2.4.0](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.4.0), 
including the fixed bugs.


## Migration notes

Migrations required updating from Ditto 2.3.x or earlier versions:
* With this release we not only switched from Java 11 to Java 17 but also from OpenJ9 to Hotspot runtime.
  This means that the environment variable `OPENJ9_JAVA_OPTIONS` needs to be renamed to `JAVA_TOOL_OPTIONS` and that 
  options specific to the OpenJ9 runtime are no longer effective.  
  Make sure that all options that are defined are valid for the Hotspot JVM.


## Ditto clients

For a complete list of all merged client PRs, inspect the following milestones:
* [merged pull requests for milestone 2.4.0-M1](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.4.0-M1)

### Ditto Java SDK

#### [Fix returning the revision of a Policy when retrieved via the client](https://github.com/eclipse-ditto/ditto-clients/pull/182)

When using the `client.policies().retrievePolicy(PolicyId)` functionality in the Ditto Java client, the `getRevision()`
method of the returned policy was always empty.  
The revision will now be included.

### Ditto JavaScript SDK

See separate [Changelog](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/CHANGELOG.md) of JS client.


## Roadmap

Looking forward, the plan for Ditto 2.5.0 is to work on:
* Enhancing MQTT (3.1.1 and 5) connections to use reactive MQTT driver (with backpressure support)
* Improving of MongoDB based search index used in [things-search](architecture-services-things-search.html),
  using a new index structure:
  * smaller index size for large thing / policy combinations
  * fewer used CPU resources on MongoDB
  * faster search queries
  * better vertical scalability of search index

Looking even more ahead, the plan for Ditto 3.0.0 is to work on:
* Simplifying Ditto's architecture by:
  * removing the "concierge service"
  * potentially (to be evaluated) even removing the [gateway service](architecture-services-gateway.html) and merging its 
    functionality (providing HTTP + WS endpoints) into the 
    [connectivity service](architecture-services-connectivity.html)
* Finalizing the experimental WoT (Web of Things) integration when the "Thing Description 1.1" is published as W3C Recommendation
