---
title: "Announcing Ditto Milestone 0.2.0-M1"
published: true
permalink: 2018-02-07-milestone-announcement-020-M1.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Ditto team is proud to announce the next milestone release.


Have a look at the Milestone [0.2.0-M1 release notes](release_notes_020-M1.html). 

The main changes are
* being able to [search in namespaces](httpapi-search.html#query-parameters) which can speed up search queries when applied
  to a large population of digital twins
* the enhancement of our [Feature entity](basic-feature.html) by [Definitions](basic-feature.html#feature-definition)
  which lays the foundation for using Features in a typesafe way (later by enforcing the schema with the help of an
  Eclipse Vorto generator


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-amqp-bridge](https://hub.docker.com/r/eclipse/ditto-amqp-bridge/)


## Virtual IoT Meetup

Today at 8am PT / 11am ET / 5pm CET Eclipse IoT will host a [Virtual IoT meetup](https://www.meetup.com/Virtual-IoT/events/247048104/)
in which we will show Eclipse Ditto's features from a technical perspective.

The video will be streamed on YouTube and will be available afterwards here: [youtube.com/watch?v=NpC4ROGqwKc](https://www.youtube.com/watch?v=NpC4ROGqwKc)

See you there ;-)

<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
