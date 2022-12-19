---
title: "Announcing Eclipse Ditto Release 3.1.0"
published: true
permalink: 2022-12-16-release-announcement-310.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams is proud to announce the availability of Eclipse Ditto 
[3.1.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.1.0).

Version 3.1.0 brings **policy imports**, **AMQP 1.0 message annotation** support, **conditional message sending** and
other smaller improvements, e.g. regarding shutdown/restart improvements.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.1.0 are:

* **Conditional message processing** based on a specified condition targeting the twin state
* Support for **reading/writing AMQP 1.0 "Message annotations"** in Ditto managed connections
* **Policy imports**: Reference other policies from policies, enabling reuse of policy entries
* Several Ditto explorer UI enhancements
* Support for configuring an **audience** for Ditto managed HTTP connections performing OAuth2.0 based authentication

The following non-functional enhancements are also included:

* End-2-End **graceful shutdown support**, enabling a smoother restart of Ditto services with less user impact
* Support for **encryption/decryption of secrets** (e.g. passwords) part of the Ditto managed connections before
  persisting to the database
* IPv6 support for blocked subnet validation

The following notable fixes are included:

* Fixing that known connections were not immediately started after connectivity service restart

Please have a look at the [3.1.0 release notes](release_notes_310.html) for a more detailed information on the release.


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

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
