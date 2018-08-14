---
title: "Announcing Ditto Milestone 0.8.0-M1"
published: true
permalink: 2018-08-14-milestone-announcement-080-M1.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Even during the summer break the Ditto team worked hard in order to provide the next milestone release. Here it is: 
Milestone 0.8.0-M1. 

Have a look at the Milestone [0.8.0-M1 release notes](release_notes_080-M1.html) for what changed in detail and why
there was a version bump from 0.3.0-M2 to 0.8.0-M1. 

The main changes and new features are

* security enhancement by making some of Ditto's headers not settable from the outside
* report application metrics to Prometheus
* automatically form a cluster when running in Kubernetes
* improvement of Ditto's `things-service` memory consumption
* stabilization of the connectivity to AMQP 1.0 and 0.9.1

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
