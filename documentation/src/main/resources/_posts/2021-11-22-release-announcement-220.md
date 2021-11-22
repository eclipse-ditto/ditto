---
title: "Announcing Eclipse Ditto Release 2.2.0"
published: true
permalink: 2021-11-22-release-announcement-220.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams announces availability of Eclipse Ditto [2.2.0](https://projects.eclipse.org/projects/iot.ditto/releases/2.2.0).

It features several nice added features and e.g. allows using the dash `-` in the namespace part of thing IDs.

## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: [https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 2.2.0 are:

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

Please have a look at the [2.2.0 release notes](release_notes_220.html) for a more detailed information on the release.


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
