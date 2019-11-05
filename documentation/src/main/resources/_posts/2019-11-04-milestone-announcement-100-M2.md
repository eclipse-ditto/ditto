---
title: "Announcing Ditto Milestone 1.0.0-M2"
published: true
permalink: 2019-11-04-milestone-announcement-100-M2.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The second and last milestone of the upcoming release 
[1.0.0](https://projects.eclipse.org/projects/iot.ditto/releases/1.0.0) was released today.

Have a look at the Milestone [1.0.0-M2 release notes](release_notes_100-M2.html) for what changed in detail.

The main changes and new features since the last release [1.0.0-M1a release notes](release_notes_100-M1a.html) are

* invoking custom foreign HTTP endpoints as a result of events/messages
* ability to reflect Eclipse Hono's device connection state in Ditto's things
* support for OpenID Connect / OAuth2.0 based authentication in Ditto Java Client
* configurbale throttling of max. consumed WebSocket commands / time interval

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
