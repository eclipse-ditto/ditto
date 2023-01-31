---
title: Release notes 0.1.0-M3
tags: [release_notes, search]
keywords: release notes, announcements, changelog
summary: "Version 0.1.0-M3 of Eclipse Ditto, released on 12.01.2018"
permalink: release_notes_010-M3.html
---

Since the first milestone of Eclipse Ditto [0.1.0-M1](release_notes_010-M1.html), the following new features and
bugfixes were added.

{% include note.html content="In milestone 0.1.0-M2 we had identified a problem right after building it, 
    that's why M3 follows M1." %}


## New features

### [AMQP Bridge](https://github.com/eclipse-ditto/ditto/pull/65)

A new service "ditto-amqp-bridge" was added in order to be able establish a connection to AMQP 1.0
endpoints like for example [Eclipse Hono](https://eclipse.org/hono/).

That way messages in [Ditto Protocol](protocol-overview.html) coming from Eclipse Hono can be processed. 
For more details, please have a look at the [AMQP 1.0 binding](connectivity-protocol-bindings-amqp10.html) and the 
[AMQP-Bridge architecture](architecture-services-connectivity.html).

### [DevOps commands HTTP endpoint](https://github.com/eclipse-ditto/ditto/pull/55)

In order to dynamically make changes to a running Ditto cluster without restarting, Ditto added an implementation
of so called "DevOps commands". Those can be triggered via a HTTP API for all services at once or specifically targeted
at single instances.

For the start the following commands are supported:
* dynamically retrieve and change log levels
* create new AMQP Bridge connections during runtime

Further information can be found in the [operating chapter](installation-operating.html#devops-commands)


## Bugfixes

### [Stabilization of eventually consistent search index](https://github.com/eclipse-ditto/ditto/pull/83)

In various conditions the search index which is updated by the [search](basic-search.html) of Ditto was not updated in case
events were missed or there were timing issues.

Those issues were resolved by improving the approach for keeping track of the already processed events. 

### Various smaller bugfixes

This is a complete list of the [merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.1.0-M3+).


## Documentation

Enhanced the documentation at various places:
* [Ditto Protocol](protocol-overview.html) and various subpages
* documentation of Ditto's [search](basic-search.html) feature
* [architecture overview](architecture-overview.html) including involved microservices
* notes on how to [operate Ditto](installation-operating.html)
* ...

