---
title: "Announcing Eclipse Ditto Release 3.0.0"
published: true
permalink: 2022-09-28-release-announcement-300.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams is proud to announce the availability of Eclipse Ditto 
[3.0.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.0.0).

With 3.0.0, the required amount of different service roles was reduced from 6 to only 5 as the "ditto-concierge" 
service's responsibilities (performing authorization) was moved to other services.  
That reduces the overall resource consumption and network traffic of a Ditto installation.

The other big topic for version 3.0.0 is the new search index which does not only respond much quicker to search queries, 
but also brings lots of performance improvements regarding MongoDB utilization and also a huge stabilization regarding 
consistency of the search in Ditto.

Many other new features and improvements are also part of the release, e.g. a new HTTP API for managing connections and
even the first version of a Ditto UI.

## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 3.0.0 are:

* Ability to **search in JSON arrays** and thus also for feature definitions
* Several **improvements around "metadata"** in Ditto managed things
* Creation of **new HTTP API for CRUD** management of Ditto managed **connections**
* Addition of **Ditto explorer UI** for managing things, policies and connections
* Support for EC signed JsonWebKeys (JWKs)
* W3C WoT (Web of Things) adjustments and improvements for latest 1.1 "Candidate Recommendation" from W3C
* Make "default namespace" for creating new entities configurable
* Provide custom namespace when creating things via HTTP POST
* Make it possible to provide multiple OIDC issuer urls for a single configured openid-connect "prefix"
* Addition of a "CloudEvents" mapper for mapping CE payloads in Ditto connections

The following non-functional work is also included:

* New Ditto **"thing" search index** massively improving write performance; reducing the search consistency lag
  and improving search query performance
* **Removal of former "ditto-concierge" service**, moving its functionality to other Ditto services; reducing overall
  resource consumption and improving latency+throughput for API calls
* Creation of common way to extend Ditto via DittoExtensionPoints
* Rewrite of Ditto managed **MQTT connections to use reactive-streams based client**, supporting consumption applying
  backpressure
* Further improvements on rolling updates and other failover scenarios
* Consolidate and simplify DevOps command responses

The following notable fixes are included:

* **Passwords** stored in the URI of **connections** to **no longer need to be double encoded**
* Using the `Normalized` connection payload mapper together with enriched `extra` fields lead to wrongly merged things
* Adding custom Java based `MessageMappers` to Ditto via classpath was no longer possible

Please have a look at the [3.0.0 release notes](release_notes_300.html) for a more detailed information on the release.


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

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
