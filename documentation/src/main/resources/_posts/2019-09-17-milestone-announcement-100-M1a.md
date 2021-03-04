---
title: "Announcing Ditto Milestone 1.0.0-M1a"
published: true
permalink: 2019-09-17-milestone-announcement-100-M1a.html
layout: post
author: johannes_schneider
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today the Ditto team is happy to announce the first milestone of the upcoming release 
[1.0.0](https://projects.eclipse.org/projects/iot.ditto/releases/1.0.0).

Have a look at the Milestone [1.0.0-M1a release notes](release_notes_100-M1a.html) for what changed in detail.

The main changes and new features since the last release [0.9.0](release_notes_090.html) are

* initial contribution of Java client SDK
* configurable OpenID Connect authorization servers
* fine grained access for connections
* scalable event publishing
* typed entity IDs

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
