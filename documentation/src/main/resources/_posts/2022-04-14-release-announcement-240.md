---
title: "Announcing Eclipse Ditto Release 2.4.0"
published: true
permalink: 2022-04-14-release-announcement-240.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams announces availability of Eclipse Ditto [2.4.0](https://projects.eclipse.org/projects/iot.ditto/releases/2.4.0).

The main topics in this release were the move from Java 11 to Java 17 (and the switch in Ditto's pre-built Docker containers from OpenJ9 to Hotspot runtime), 
W3C WoT (Web of Things) integration and enhanced placeholder capabilities, e.g. used in JWT claim extraction and signal 
enrichment.

## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 2.4.0 are:

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

Please have a look at the [2.4.0 release notes](release_notes_240.html) for a more detailed information on the release.


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
