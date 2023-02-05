---
title: "Announcing Eclipse Ditto Release 1.0.0"
published: true
permalink: 2019-12-12-release-announcement-100.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today the Eclipse Ditto team is thrilled to announce the availability of Eclipse Ditto's first major release
[1.0.0](https://projects.eclipse.org/projects/iot.ditto/releases/1.0.0).


## Maturity

The initial code contribution was done in October 2017, 2 years later and 2 releases 
([0.8.0](2018-11-28-release-announcement-080.html) and [0.9.0](2019-07-10-release-announcement-090.html)) later, we 
think its time to graduate from the Eclipse "incubation" phase and officially declare the project as mature.

Recent adoptions and contributions from our community show us that Eclipse Ditto solves problems which also other
companies have. Adopters add Eclipse Ditto as a central part of their own IoT platforms.

### API stability

Having reached 1.0.0, some additional promises towards "API stability" do apply:

#### HTTP API stability
Ditto uses schema versioning (currently schema version 1 and 2) at the HTTP API level in order to being able to 
evolve APIs.
It is backward compatible to the prior versions 0.8.0 and 0.9.0.

#### JSON API stability
Ditto kept its main JSON APIs (regarding things, policies and search) backwards compatible to 0.8.0 and 0.9.0 releases.
The JSON format of "connections" was changed since 0.9.0 and will from 1.0.0 on be kept backwards compatible as well.

#### Java API stability
The Java APIs will for the 1.x release be kept backwards compatible, so only non-breaking additions to the APIs will be done. This is enforced by a Maven tooling.

The following Java modules are treated as API for which compatibility is enforced:

* ditto-json
* ditto-model-*
* ditto-signals-*
* ditto-protocol-adapter
* ditto-utils
* ditto-client

### Scalability

The focus on the 0.9.0 and 1.0.0 releases regarding non-functionals were laid on horizontal scalability.

With Eclipse Ditto 1.0.0 we are confident to face production grade scalability requirements being capable of handling 
millions of managed things.


## Changelog

The main changes compared to the last release, [0.9.0](release_notes_090.html), are:

* addition of a Java and a JavaScript client SDK in separate [GitHub repo](https://github.com/eclipse-ditto/ditto-clients)
* configurable OpenID Connect authorization servers
* support for OpenID Connect / OAuth2.0 based authentication in Ditto Java Client
* invoking custom foreign HTTP endpoints as a result of events/messages
* ability to reflect Eclipse Hono's device connection state in Ditto's things
* configurable throttling of max. consumed WebSocket commands / time interval
* Addition of "definition" field in thing at model level containing the model ID a thing may follow
* Improved connection response handling/mapping

Please have a look at the [1.0.0 release notes](release_notes_100.html) for a more detailed information on the release.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

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
