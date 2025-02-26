---
title: "Announcing Eclipse Ditto Release 3.7.0"
published: true
permalink: 2025-02-26-release-announcement-370.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Eclipse Ditto team is excited to announce the availability of a new minor release, including new features: 
Ditto [3.7.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.7.0).

The focus of this release was to ease the migration of Things "definitions" (following WoT Things Models) and to provide 
a new Policy decision API to check permissions for a logged-in user.  
On the operating side, it is now possible to configure extra fields to be proactively added to Things in order to optimize
cluster roundtrips and to throttle the amount of updates to the search index after a re-used policy was updated.

## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.7.0 are:

* Introduce new **Policy decision API** to check with a single request what a logged-in user is allowed to do with a specific resource
* Include current **entity revision** of a resource (thing and policy) in the response of requests (commands) and in all emitted events
* Support updating referenced WoT ThingModel based **thing definition** for a Thing by defining a migration payload and when to apply it

The following non-functional work is also included:

* Add option to **configure pre-defined extra fields** (enrichments) to be proactively added internally in Ditto in order to save cluster roundtrips
* Include **throttling configuration option** for updating the search index as a result of a policy update targeting many things
* Add namespace to Ditto Helm chart managed Kubernetes resources

The following notable fixes are included:

* Fix flattening of JSON objects in arrays when an exists() RQL condition was used e.g. as a Ditto evaluated condition

Please have a look at the [3.7.0 release notes](release_notes_370.html) for a more detailed information on the release.


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
