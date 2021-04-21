---
title: Release notes 0.9.0
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.9.0 of Eclipse Ditto, released on 10.07.2019"
permalink: release_notes_090.html
---

During several very hot summer days the Eclipse Ditto team worked on that last issues which were blocking our next
release: `0.9.0`.

The successor release of [0.8.0](release_notes_080.html) will most likely be the last "0." incubation release as Ditto
is moving forward for a `1.0.0` and a project graduation in Eclipse IoT.

Same as for `0.8.0` this release is completely [IP (intellectual property) checked by 
the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip) meaning that project code as well as all used 
dependencies were "[...] reviewed to ensure that the copyrights expressed are correct, licensing is valid 
and compatible, and that other issues have been uncovered and properly investigated."

## What's in this release?

Eclipse Ditto 0.9.0 focuses on the following areas:
                               
* Memory improvements for huge amounts (multi million) of digital twins which are held in memory
* Adding metrics and logging around the connectivity feature in order to enable being able to operate connections to foreign systems/brokers via APIs
* Enhancing Ditto's connectivity feature by additionally being able to connect to Apache Kafka
* Performance improvements of Ditto's search functionality
* Stabilization of cluster bootstrapping
* Refactoring of how the services configurations are determined
* Addition of a Helm template in order to simplify Kubernetes based deployments
* Contributions from Microsoft in order to ease operating Eclipse Ditto on Microsoft Azure

{% include warning.html content="If you want to upgrade an existing Ditto 0.8.0 installation, the following database 
        migration has to be done: **Follow the steps documented in [the migration notes](architecture-services-things-search.html#migration-from-ditto-090-m1)**." %}


### Changelog

Compared to the latest milestone release [0.9.0-M2](release_notes_090-M2.html), the following changes, new features and
bugfixes were added.


#### Changes

##### [Streamline configuration](https://github.com/eclipse/ditto/issues/350)

In this release, we changed how internally the configuration is determined in Ditto's microservices. This should not 
have any impact on a user of Eclipse Ditto. As however some configuration keys were remanded or restructured, some
adjustments when manually configuring Ditto could be required.


#### New features

##### [Introduce cursor-based paging for search requests](https://github.com/eclipse/ditto/pull/407)

In order to provide a constant performance for using pagination for the `things-search` miscroservice across even large 
result sets (100s of thousands or more) of digital twins, a new cursor-based approach was added in addition to the 
old `offset/limit` approach which gets slower for each page.

##### [Simple throttling for amqp 1.0 consumers](https://github.com/eclipse/ditto/pull/420)

Added a simple throttling mechanism for amqp 1.0 consumers.
The throttling is configurable by defining the number of messages allowed per time interval.

##### [Collect connectivity log entries and provide via devops command](https://github.com/eclipse/ditto/issues/318)

In order to let a user analyze failures / unexpected behavior in his connections (e.g. to an MQTT or AMQP broker) 
without giving him full access to the log-files of Ditto, a connection scoped mechanism for retrieving connectivity logs 
was added.


#### Bugfixes

##### [Memory and performance fixes in concierge, gateway](https://github.com/eclipse/ditto/pull/400)

Ditto's `concierge` service did use a sharding approach for enforcing authorization information which lead to huge 
amounts of memory required when managing multi million of digital twins.

That, in addition to some memory fixes in the gateway and addition of metrics, was fixed in this PR.

##### [Ensure ordering for processed commands](https://github.com/eclipse/ditto/pull/417)

In previous versions of Ditto the order in which command were processed not guaranteed to be maintained. As maintaining
the order however is the expected behavior, we decided to treat that as bug and added to the `concierge` service (where
the order could be lost) to sequentially process commands issues via HTTP/WebSocket and connections.

This release contains several bugfixes, this is a complete list of the 
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A0.9.0+).



## API stability

As you noticed, Ditto is not yet released as 1.x version, it is still an 
[incubating Eclipse project](https://wiki.eclipse.org/Development_Resources/Process_Guidelines/What_is_Incubation).

That means that the project _could_ choose to break APIs at any time without prior deprecation.

We however can already guarantee that at the HTTP API for API version 1 and 2 the
API stability is already ensured for the core functionality of managing `things` and `policies`.

As the commercial product based on Eclipse Ditto, [Bosch IoT Things](https://www.bosch-iot-suite.com/things/), is 
already used productive and in various projects, the API stability has to be and will be ensured moving forward towards 
a 1.0.0 release.

## Roadmap

The Ditto project plans on releasing (non-milestone releases) twice per year, once every 6 months. 

In late 2019 we expect to graduate (exit the Eclipse incubation phase) with a 1.0.0 release. 
