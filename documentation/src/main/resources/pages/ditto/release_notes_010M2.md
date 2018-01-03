---
title: Release notes 0.1.0-M2
tags: [release_notes, search]
keywords: release notes, announcements, changelog
summary: "Version 0.1.0-M2 of Eclipse Ditto, released on xxx"
permalink: release_notes_010-M2.html
---

Since the first milestone of Eclipse Ditto [0.1.0-M1](release_notes_010-M1.html), the following new features and
bugfixes were added.


## New features

### AMQP Bridge

A new service "ditto-amqp-bridge" was added in order to be able establish a connection to AMQP 1.0
endpoints like for example [Eclipse Hono](https://eclipse.org/hono/).

That way messages in [Ditto Protocol](protocol-overview.html) coming from Eclipse Hono can be processed. 
For more details, please have a look at the [AMQP-Bridge documentation](architecture-services-amqp-bridge.html).

TODO:
* Add documentation of AMQP bridge
* Provide tutorial of how to connect to Hono


### DevOps commands HTTP endpoint

In order to dynamically make changes to a running Ditto cluster without restarting, Ditto added an implementation
of so called "DevOps commands". Those can be triggered via a HTTP API for all services at once or specifically targeted
at single instances.

For the start the following commands are supported:
* dynamically retrieve and change log levels
* create new AMQP Bridge connections during runtime

Further information can be found in the [operating chapter](installation-operating.html#devops-commands)

### Search in namespaces

TODO write
TODO enhance [search](basic-search.html) section



## Bugfixes

### Stabilization of eventually consistent search index

In various conditions the search index which is updated by the [search](basic-search.html) of Ditto was not updated in case
events were missed or there were timing issues.

Those issues were resolved by choosing another approach for keeping track of the already processed events. 

