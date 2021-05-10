---
title: "Announcing Eclipse Ditto Release 2.0.0"
published: true
permalink: 2021-05-06-release-announcement-200.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today, ~1.5 years after release [1.0.0](2019-12-12-release-announcement-100.html), the Eclipse Ditto team is happy to 
announce the availability of Eclipse Ditto [2.0.0](https://projects.eclipse.org/projects/iot.ditto/releases/2.0.0).

With the major version 2.0.0 the Ditto team removed technical debt and ended support for APIs which were deprecated 
long ago in order to have a better maintainable codebase. However some awesome new features are included as well.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: [https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 2.0.0 are:

* Merge/PATCH updates of digital twins
* Configurable OpenID Connect / OAuth2.0 claim extraction to be used for authorization
* Establishing connections to endpoints (via AMQP, MQTT, HTTP) utilizing a Ditto managed SSH tunnel
* Addition of a DevOps API in order to retrieve all known connections
* Expiring policy subjects + publishing of announcement message prior to expiry
* Addition of policy actions in order to inject a policy subject based on a provided JWT
* Built-in acknowledgement for search updates to have the option of twin updates with strong consistency of the search index
* Restoring active connection faster after a hard restart of the Ditto cluster via automatic prioritization of connections
* Support for LastWill/Testament + retain flag for MQTT connections

The step to a major version was done because of the following breaking API changes:

* Removal of "API version 1" (deprecated in [Ditto 1.1.0](release_notes_110.html#deprecations))
  from Ditto's Java APIs + HTTP API
* Removal of code in Java APIs marked as `@Deprecated`
* Binary incompatible changes to Java APIs
* Restructuring of Ditto's Maven modules in order to simplify/ease further development

The following non-functional enhancements are also included:

* Improvement of stability during rolling updates
* Addition of sharding concept for Ditto internal pub/sub enabling connection of e.g. tens of thousands Websocket sessions
* Background cleanup improvements in order to have less impact on DB roundtrip times
* Update of third party libraries (e.g. Akka)
* Documentation of deployment via K3S

Please have a look at the [2.0.0 release notes](release_notes_200.html) for a more detailed information on the release.


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
