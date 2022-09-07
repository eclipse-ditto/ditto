---
title: "Announcing Ditto Milestone 0.3.0-M2"
published: true
permalink: 2018-06-15-milestone-announcement-030-M2.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today we, the Eclipse Ditto team, are happy to announce our next milestone 0.3.0-M2.

The main changes are 

* improvement of Ditto's cluster performance with many managed Things
    * a new Ditto service "ditto-concierge" was added for this
* improved cluster bootstrapping based on DNS with the potential to easy plugin other mechanism (e.g. for Kubernetes)

Have a look at the Milestone [0.3.0-M2 release notes](release_notes_030-M2.html) for a detailed description of what 
changed.

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
