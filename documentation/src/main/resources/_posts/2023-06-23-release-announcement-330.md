---
title: "Announcing Eclipse Ditto Release 3.3.0"
published: true
permalink: 2023-06-23-release-announcement-330.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams is proud to announce the availability of Eclipse Ditto 
[3.3.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.3.0).

Version 3.3.0 contains features improving **merge/PATCH** commands, **skipping modifications** of a twin if the
value would be equal after the modification and a more production ready Ditto Helm chart.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.3.0 are:

* Support **replacing certain json objects** in a **merge/PATCH command** instead of merging their fields
* Implicitly **convert a merge/PATCH command** to a **"Create Thing"** if thing is **not yet existing**
* Provide option to **skip a modification** in the "twin" **if the value "is equal"** to the previous value
* Addition of the **DevOps API endpoints** to Ditto's **OpenAPI** definition
* Improve DittoProtocol MessagePath to be aware of message subject
* Support alternative way of specifying **"list" query parameters**
* UI enhancements:
  * Enhance Ditto-UI to dynamically configure log levels of Ditto
  * Building and packaging the UI with esbuild

The following non-functional enhancements are also included:

* Provide **official Eclipse Ditto Helm chart** via **Docker Hub** and move its sources to Ditto Git repository
  * In addition, provide a lot more configuration options and hardening of the chart to make it more feasible
    for productive use

The following notable fixes are included:

* Fix that redeliveries for acknowledgeable connectivity messages were issued too often
* Fix WoT dispatcher starvation by adding timeouts to fetch models

Please have a look at the [3.3.0 release notes](release_notes_330.html) for a more detailed information on the release.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

The Ditto JavaScript client release was published on [npmjs.com](https://www.npmjs.com/~eclipse_ditto):
* [@eclipse-ditto/ditto-javascript-client-dom](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-dom)
* [@eclipse-ditto/ditto-javascript-client-node](https://www.npmjs.com/package/@eclipse-ditto/ditto-javascript-client-node)


The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-connectivity](https://hub.docker.com/r/eclipse/ditto-connectivity/)

The Ditto Helm chart has been published to Docker Hub:
* [eclipse/ditto](https://hub.docker.com/r/eclipse/ditto/)

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
