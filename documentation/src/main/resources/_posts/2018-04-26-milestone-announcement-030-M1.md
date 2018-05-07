---
title: "Announcing Ditto Milestone 0.3.0-M1"
published: true
permalink: 2018-04-26-milestone-announcement-030-M1.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

After some time of silence of Ditto milestone releases we are very proud to present our next one to the public.

Have a look at the Milestone [0.3.0-M1 release notes](release_notes_030-M1.html). 

The main changes are 

* switch to [Eclipse OpenJ9](https://www.eclipse.org/openj9/) JVM in Ditto's Docker images
* renaming of Ditto's "AMQP bridge" service to "Connectivity" due to more responsibilities for that service:
    * managing + connecting to AMQP 0.9.1 endpoints as well as to AMQP 1.0 endpoints
    * transforming/mapping message payloads to/from [Ditto Protocol](protocol-overview.html)
    * for further details, have a look at our [blogpost about that](2018-04-25-connectivity-service.html)

## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

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
