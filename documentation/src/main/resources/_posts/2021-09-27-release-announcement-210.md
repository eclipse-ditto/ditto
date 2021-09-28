---
title: "Announcing Eclipse Ditto Release 2.1.0"
published: true
permalink: 2021-09-27-release-announcement-210.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams announces availability of Eclipse Ditto [2.1.0](https://projects.eclipse.org/projects/iot.ditto/releases/2.1.0).

As the first minor of the 2.x series it adds a lot of new features, the highlight surely being the full integration of
[Apache Kafka as Ditto managed connection](connectivity-protocol-bindings-kafka2.html).


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: [https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 2.1.0 are:

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

Please have a look at the [2.1.0 release notes](release_notes_210.html) for a more detailed information on the release.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

The Ditto JavaScript client release was published on [npmjs.com](https://www.npmjs.com/~eclipse_ditto):
* [@eclipse-ditto/ditto-javascript-client-dom](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-dom)
* [@eclipse-ditto/ditto-javascript-client-node](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-node)


The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-connectivity](https://hub.docker.com/r/eclipse/ditto-connectivity/)
* [eclipse/ditto-concierge](https://hub.docker.com/r/eclipse/ditto-concierge/)

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
