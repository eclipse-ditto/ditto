---
title: "Announcing Ditto Milestone 0.9.0-M1"
published: true
permalink: 2019-03-28-milestone-announcement-090-M1.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today the Ditto team is happy to announce the first milestone of the upcoming release 
[0.9.0](https://projects.eclipse.org/projects/iot.ditto/releases/0.9.0).

Have a look at the Milestone [0.9.0-M1 release notes](release_notes_090-M1.html) for what changed in detail.

The main changes and new features since the last release [0.8.0](release_notes_080.html) are

* memory optimizations when working with millions of digital twins
* enhance connectivity to also be able to establish connections to Apache Kafka
* providing more detailed metrics for connections
* cluster bootstrapping stability improvements

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
