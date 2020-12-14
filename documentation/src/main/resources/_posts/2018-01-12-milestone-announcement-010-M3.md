---
title: "Announcing Ditto Milestone 0.1.0-M3"
published: true
permalink: 2018-01-12-milestone-announcement-010-M3.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

We wish you all a happy new year<br/>
and an hope you are curious about our new Eclipse Ditto milestone.


## Milestone 3

Our new milestone, namely [0.1.0-M3](release_notes_010-M3.html), adds an AMQP 1.0 bridge. The bridge enables to connect 
to a running instance of [Eclipse Hono](https://eclipse.org/hono/). Ditto can consume telemetry and event messages from Hono  
and interpret those, given that these are compatible to our [Ditto Protocol](protocol-overview.html).

Find more information about the milestone in the [0.1.0-M3 release notes](release_notes_010-M3.html).


## Sandbox

Together with this milestone release we have set up a sandbox at [https://ditto.eclipseprojects.io](https://ditto.eclipseprojects.io).

There, everyone with a Google account can try out the HTTP API by using the interactive 
[HTTP API documentation](https://ditto.eclipseprojects.io/apidoc/) (powered by [Swagger](https://swagger.io)).

Try it out and share your experience.

The sandbox does not yet start the new Connectivity in order to connect to Hono; we will work on that soon.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-amqp-bridge](https://hub.docker.com/r/eclipse/ditto-amqp-bridge/)

{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
