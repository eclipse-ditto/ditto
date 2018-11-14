---
title: "Announcing Ditto Milestone 0.8.0-M3"
published: true
permalink: 2018-11-14-milestone-announcement-080-M3.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Preparing the upcoming first release [0.8.0](https://projects.eclipse.org/projects/iot.ditto/releases/0.8.0) of 
Eclipse Ditto,  this milestone is a last checkpoint to ensure that the release will be performed smoothly.<br/>
Therefore, this milestone release primarily focuses on stabilization. 

Have a look at the Milestone [0.8.0-M3 release notes](release_notes_080-M3.html) for what changed in detail.

{% include warning.html content="If you want to upgrade an existing Ditto installation, you'll have to execute a small 
        database migration - see release notes." %}

The main changes and new features are

* speed up of search index creation
* applying enforcement of messages received via connections (e.g. from Eclipse Hono)
* copying already existing policies when creating things 

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
