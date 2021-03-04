---
title: "Announcing Eclipse Ditto Release 0.9.0"
published: true
permalink: 2019-07-10-release-announcement-090.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today the Eclipse Ditto team proudly presents its second release 
[0.9.0](https://projects.eclipse.org/projects/iot.ditto/releases/0.9.0).

The topics of this release in a nutshell were:

* Memory improvements for huge amounts (multi million) of digital twins which are held in memory
* Adding metrics and logging around the connectivity feature in order to enable being able to operate connections to foreign systems/brokers via APIs
* Enhancing Ditto's connectivity feature by additionally being able to connect to Apache Kafka
* Performance improvements of Ditto's search functionality
* Stabilization of cluster bootstrapping
* Refactoring of how the services configurations are determined
* Addition of a Helm template in order to simplify Kubernetes based deployments
* Contributions from Microsoft in order to ease operating Eclipse Ditto on Microsoft Azure 

Please have a look at the [0.9.0 release notes](release_notes_090.html) for a more detailed information on the release.

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
