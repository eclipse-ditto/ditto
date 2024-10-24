---
title: "Announcing Eclipse Ditto Release 3.6.0"
published: true
permalink: 2024-10-07-release-announcement-360.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

After a longer time of "bugfix releases" only, the Eclipse Ditto team is once again happy to announce the availability
of a new minor release, including new features: Ditto [3.6.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.6.0).

The most work in this release went into the enforcement/validation of a linked WoT (Web of Thing) "Thing Model" to make
sure that a Ditto managed digital twin can only be modified in ways which are valid based on the defined WoT model.

But also other features like SSO in the Ditto-UI were added, so inform yourself in this blogpost about the changes in 
the new release.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.6.0 are:

* **WoT (Web of Things)** Thing Model based validation of modifications to things and action/event payloads
* **AWS IAM based authentication** against **MongoDB**
* Configure **defined aggregation queries** to be **exposed as Prometheus metrics** by Ditto periodically
* **SSO (Single-Sign-On)** support in the Ditto UI via OpenID connect provider configuration

The following non-functional work is also included:

* Update Java runtime to **run Eclipse Ditto with** to **Java 21**
* Run **Ditto system tests** in **GitHub actions**

The following notable fixes are included:

* Fix **JWT placeholder** not resolving correctly in **JSON arrays nested** in JSON objects
* Fix **retrieving a Thing** at a **given historical timestamp**
* Generating UNIX "Epoch" as neutral element when creating new things based on WoT TM models for types declared as "date-time" format


Please have a look at the [3.6.0 release notes](release_notes_360.html) for a more detailed information on the release.


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
