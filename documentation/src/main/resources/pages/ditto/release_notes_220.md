---
title: Release notes 2.2.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.2.0 of Eclipse Ditto, released on 22.11.2021"
permalink: release_notes_220.html
---

Ditto **2.2.0** is API and [binary compatible](https://github.com/eclipse-ditto/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Eclipse Ditto 2.2.0 includes the following topics/enhancements:

* Filter for twin life-cycle events like e.g. "thing created" or "feature deleted" via RQL expressions
* Possibility to forward connection logs via fluentd or Fluent Bit to an arbitrary logging system
* Add OAuth2 client credentials flow as an authentication mechanism for Ditto managed HTTP connections
* Enable loading additional extra JavaScript libraries for Rhino based JS mapping engine
* Allow using the dash `-` as part of the "namespace" part in Ditto thing and policy IDs

The following notable fixes are included:

* Policy enforcement for event publishing was fixed
* Search updater cache inconsistencies were fixed
* Fixed diff computation in search index on nested arrays

The following non-functional work is also included:

* Collect Apache Kafka consumer metrics and expose them to Prometheus endpoint

<br/>

For a complete list of all merged PRs, inspect the following milestones:
* [merged pull requests for milestone 2.2.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:2.2.0)

<br/>
<br/>

Compared to the latest release [2.1.0](release_notes_210.html), the following most notable changes, new features and
bugfixes were added.

### Changes

#### [Allow using the dash `-` as part of the "namespace" part in Ditto thing and policy IDs](https://github.com/eclipse-ditto/ditto/issues/1231)

A feedback from our community was that the dash `-` should be an allowed character in the 
[namespace](basic-namespaces-and-names.html#namespace) part of Ditto managed IDs.  
As there were no technical reasons to not allow the dash, we agreed and namespaces do now support dashes, e.g. in order
to use the reverse-domain-notation for a package name with domains including dashes.


### New features

#### [Filter for twin life-cycle events](https://github.com/eclipse-ditto/ditto/issues/898)

When applying [filtering for change notifications](basic-changenotifications.html#filtering), the existing RQL based
filter was enhanced with the possibility to use [topic and resource placeholders](basic-rql.html#placeholders-as-query-properties)
which enables defining RQL predicates like:

Only emit events for Thing creation and deletion:
```
and(in(topic:action,'created','deleted'),eq(resource:path,'/'))
```

#### [Possibility to forward connection logs](https://github.com/eclipse-ditto/ditto/pull/1230)

Configure [log publishing](connectivity-manage-connections.html#publishing-connection-logs)for your Ditto managed
connections in order to get connection logs wherever you need them to analyze.

#### [Add OAuth2 client credentials flow as an authentication mechanism for Ditto managed HTTP connections](https://github.com/eclipse-ditto/ditto/pull/1233)

Have a look at our [blog post](2021-11-03-oauth2.html) which shares an example of how to configure a Ditto managed
HTTP connection to make use of OAuth2.0 authentication.

#### [Enable loading additional extra JavaScript libraries for Rhino based JS mapping engine](https://github.com/eclipse-ditto/ditto/pull/1208)

The used [Rhino JS engine](https://github.com/mozilla/rhino) allows making use of "CommonJS" in order to load JS
modules via `require('')` into the engine. This feature has now been exposed by Ditto, configuring the environment
variable `CONNECTIVITY_MESSAGE_MAPPING_JS_COMMON_JS_MODULE_PATH` of the connectivity service to a path in the
connectivity Docker container where to load additional CommonJS modules from - e.g. use a volume mount in order to get
additional JS modules into the container.


### Bugfixes

Several bugs in Ditto 2.1.x were fixed for 2.2.0.  
This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.2.0), including the fixed bugs.


## Migration notes

No migrations required updating from Ditto 2.1.x

## Ditto clients

For a complete list of all merged client PRs, inspect the following milestones:
* [merged pull requests for milestone 2.2.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:2.2.0)

### Ditto Java SDK

No mentionable changes/enhancements/bugfixes.

### Ditto JavaScript SDK

See separate [Changelog](https://github.com/eclipse-ditto/ditto-clients/blob/master/javascript/CHANGELOG.md) of JS client.


## Roadmap

Looking forward, the current plans for Ditto 2.3.0 are:

* [Add HTTP API for "live" commands](https://github.com/eclipse-ditto/ditto/issues/106)
* [Smart channel strategy for live/twin read access](https://github.com/eclipse-ditto/ditto/issues/1228)
* More work on concept and first work on a WoT (Web of Things) integration
