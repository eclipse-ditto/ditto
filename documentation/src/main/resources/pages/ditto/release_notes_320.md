---
title: Release notes 3.2.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.2.0 of Eclipse Ditto, released on 08.03.2023"
permalink: release_notes_320.html
---

The second minor release of Ditto 3.x, Eclipse Ditto version 3.2.0 is here.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 3.2.0 focuses on the following areas:

* New **History API** in order to be able to:
  * access historical state of things/policies/connections (with either given revision number or timestamp)
  * stream persisted events of things/policies via async APIs (WebSocket, Connections) and things also via existing SSE (Server-Sent-Events) API
  * configure deletion retention of events in the database for each entity
* Addition of new **Eclipse Hono** connection type for Ditto managed connections
* Option to do **case-insensitive searches** and addition of a new RQL operator to declare case-insensitive like: `ilike`
* UI enhancements:
  * Push notifications on the Ditto UI using SSE (Server-Sent-Events), e.g. on thing updates
  * Autocomplete functionality for the search slot
  * Added configuring `Bearer` auth type for the "devops" authentication
* JavaScript client:
  * Support for **"merge" / "patch"** functionality in the **JS client**

The following non-functional work is also included:

None in this release.

The following notable fixes are included:

* Undo creating implicitly created policy as part of thing creation if creation of thing failed


### New features

#### [Provide API to stream/replay persisted events from the event journal](https://github.com/eclipse-ditto/ditto/issues/1498)

Starting with this release, Eclipse Ditto is now able to provide historical values via its APIs.  
Ditto makes use of the [Event Sourcing](basic-signals.html#architectural-style) persistence pattern in order to persist
the different managed entities:
* things
* policies
* connections

As a result of this persistence pattern, the history therefore was "always there" (as delta events in the database), 
it was just not yet provided as API.

This gap has now been closed, see the added [documentation for "History capabilities"](basic-history.html).

A configurable [cleanup retention time](basic-history.html#cleanup-retention-time-configuration) lets the operator of 
Ditto decide how much of history to keep, differently configured for "things", "policies" and "connections".  
For policies and connections it might even make sense to never clean up any events (if only few updates are expected to be done) 
and as a result get audit log capabilities for those entities (to find out "who" did "what" changes "when") - or to e.g.
undo configuration changes to connections or compare between different revisions.

Possible is also streaming all events from a thing or policy in order to e.g. show the change of a thing's state 
(or a particular property value) over time.

{% include note.html content="Ditto's history API capabilities are not comparable with the features of a time series database.
    E.g. no aggregations on or compactions of the historical data can be done." %}

#### [Add new "Hono" connection type](https://github.com/eclipse-ditto/ditto/pull/1548)

A new connection type [Hono](connectivity-protocol-bindings-hono.html) was added which takes some configuration from 
static Ditto configuration (e.g. the Kafka endpoint and credentials) in order to better abstract from potential changes
to [Eclipse Hono™](https://www.eclipse.org/hono/), e.g. regarding Kafka topic structure.

#### [Things-Search Case Sensitivity Option](https://github.com/eclipse-ditto/ditto/issues/1087)

Ditto's search API did not yet provide the option to perform a search (or apply an [RQL filter](basic-rql.html)) ignoring
the upper/lower case of words.  
This option has been added as new `ilike` function, both to the [search](basic-search.html) and to all places where
[RQL filter](basic-rql.html) can be used.

This was a contribution from [Abhijeet Mishra](https://github.com/Abhijeetmishr) - many thanks for that.

#### Enhancements in Ditto explorer UI

We again received several contributions by [Thomas Fries](https://github.com/thfries),
who contributed the Ditto explorer UI.  
The latest live version of the UI can be found here:  
[https://eclipse-ditto.github.io/ditto/](https://eclipse-ditto.github.io/ditto/)

You can use it in order to e.g. connect to your Ditto installation to manage things, policies and even connections.

Contributions in this release:
* [UI activate Server Sent Events](https://github.com/eclipse-ditto/ditto/pull/1532)
* [Explorer UI - autocomplete for search](https://github.com/eclipse-ditto/ditto/pull/1580)
* [UI - removed old dropdown for searchfilters](https://github.com/eclipse-ditto/ditto/pull/1584)
* [UI: Provide Bearer authentication for devops user](https://github.com/eclipse-ditto/ditto/issues/1592)


#### Enhancements in Ditto Clients

##### [added merge functionality to javascript client sdk](https://github.com/eclipse-ditto/ditto-clients/pull/217)

The [Ditto JS client](https://github.com/eclipse-ditto/ditto-clients/tree/master/javascript) did not yet support the
[merge/path](protocol-specification-things-merge.html) of Ditto.  
This was now provided and is available in the 3.2.0 version of the JS client.

This was a contribution from [Pieter-Jan Lanneer](https://github.com/PJGitLan) - many thanks for that.

### Changes

None in this release.


### Bugfixes

Some bugs in Ditto 3.1.x were also fixed for 3.2.0.  
This is a complete list of the
* [merged pull requests for milestone 3.2.0](https://github.com/eclipse-ditto/ditto/pulls?q=is:pr+milestone:3.2.0)

Here as well for the Ditto Clients: [merged pull requests for milestone 3.2.0](https://github.com/eclipse-ditto/ditto-clients/pulls?q=is:pr+milestone:3.2.0)

#### [Creation of thing's policy is atomic with creation the of thing itself](https://github.com/eclipse-ditto/ditto/pull/1581)

Fixes providing atomic behaviour while creating a thing and an implicit policy as result of the thing creation.  
In the rare case of failing to create a thing, its policy will be rolled back (deleted) in order to not fail create retry.

## Migration notes

There are no migration steps required when updating from Ditto 3.1.x to Ditto 3.2.0.  
When updating from Ditto 2.x version to 3.2.0, the migration notes of 
[Ditto 3.0.0](release_notes_300.html#migration-notes) and [Ditto 3.1.0](release_notes_310.html#migration-notes) apply.


## Roadmap

Looking forward, the (current) ideas for Ditto 3.3.0 are:

* [Support replacing certain json objects in a merge/patch command instead of merging their fields](https://github.com/eclipse-ditto/ditto/issues/1593)
* [UI: Enhance Ditto-UI to configure log levels of Ditto](https://github.com/eclipse-ditto/ditto/issues/1590)
* [Provide CoAP endpoint in Ditto's gateway, providing the Ditto HTTP API via CoAP](https://github.com/eclipse-ditto/ditto/issues/1582)
  * Maybe - as no-one declared interest yet in this
* Perform a benchmark of Ditto and provide a "tuning" chapter in the documentation as a reference to the commonly 
  asked questions 
  * how many Things Ditto can manage
  * how many updates/second can be done
  * whether Ditto can scale horizontally
  * how many resources (e.g. machines) are required at which scale
