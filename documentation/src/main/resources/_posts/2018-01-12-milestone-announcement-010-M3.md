---
title: "Announcing Ditto Milestone 0.1.0-M3"
published: true
permalink: 2018-01-12-milestone-announcement-010-M3.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
---

We wish you all a happy new year and have the next Ditto milestone which is worth a small post.

Our second milestone, [0.1.0-M3](release_notes_010-M3.html), adds an AMQP 1.0 bridge which can be used in order to connect
to a running instance of [Eclipse Hono](https://eclipse.org/hono/) in order to consume telemetry and event messages
and interpret those which are in [Ditto Protocol](protocol-overview.html).

More information about that milestone can be found in the [0.1.0-M3 release notes](release_notes_010-M3.html).


## Sandbox

Together with this milestone release we set up a sandbox at [https://ditto.eclipse.org](https://ditto.eclipse.org).

There, everyone with a Google account can try out the HTTP API by using the interactive 
[HTTP API documentation](https://ditto.eclipse.org/apidoc/) (powered by [Swagger](https://swagger.io)).
Try it out if you like.

The sandbox does not yet start the new AMQP-bridge in order to connect to Hono, we'll work on that next.


## Artifacts

Again Java artifacts were published to both the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

And Docker images were pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-amqp-bridge](https://hub.docker.com/r/eclipse/ditto-amqp-bridge/)

{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
