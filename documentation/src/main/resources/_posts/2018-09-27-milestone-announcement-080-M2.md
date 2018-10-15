---
title: "Announcing Ditto Milestone 0.8.0-M2"
published: true
permalink: 2018-09-27-milestone-announcement-080-M2.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Brace yourself, Eclipse Ditto is preparing for its 
[first release 0.8.0](https://projects.eclipse.org/projects/iot.ditto/releases/0.8.0). We are happy to announce our next
milestone towards that goal.

Have a look at the Milestone [0.8.0-M2 release notes](release_notes_080-M2.html) for what changed in detail.

The main changes and new features are

* enforcement of max. entity size of twins and messages
* added MQTT support connecting to MQTT 3.1.1 brokers
* subscribing to changes based on filters
* conditional requests at all APIs

## Artifacts

Unfortunately, we had some problems during the milestone release build causing that the released artifacts have version
`0.8.0-M2b` instead of `0.8.0-M2`.

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
