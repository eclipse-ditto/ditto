---
title: "Announcing Eclipse Ditto Release 1.3.0"
published: true
permalink: 2020-09-30-release-announcement-130.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today, the Ditto team is happy to announce the next feature update of Ditto `1.x`: **Eclipse Ditto 1.3.0**

1.3.0 focuses on the following areas:

* Implicit/automatic creation of digital twins (things)
* Use response of HTTP push connections as live message response
* "Raw" payload mapper for "pass-through" connectivity scenarios not affecting the twin

Please have a look at the [1.3.0 release notes](release_notes_130.html) for a more detailed information on the release.

Also, some bugs were fixed which are not backported to Ditto 1.2.x - it is recommended to update to Ditto 1.3.0 right
away and skip 1.2.x.


## Artifacts

The new Java artifacts have been published at the [Eclipse Maven repository](https://repo.eclipse.org/content/repositories/ditto/)
as well as [Maven central](https://repo1.maven.org/maven2/org/eclipse/ditto/).

Also the [Ditto Java client](client-sdk-java.html)'s artifacts were published to Maven central.

The Docker images have been pushed to Docker Hub:
* [eclipse/ditto-policies](https://hub.docker.com/r/eclipse/ditto-policies/)
* [eclipse/ditto-things](https://hub.docker.com/r/eclipse/ditto-things/)
* [eclipse/ditto-things-search](https://hub.docker.com/r/eclipse/ditto-things-search/)
* [eclipse/ditto-gateway](https://hub.docker.com/r/eclipse/ditto-gateway/)
* [eclipse/ditto-connectivity](https://hub.docker.com/r/eclipse/ditto-connectivity/)
* [eclipse/ditto-concierge](https://hub.docker.com/r/eclipse/ditto-concierge/)


## Kubernetes ready: Helm chart

In order to run Eclipse Ditto in a Kubernetes environment, best rely on the official 
[Helm chart](https://hub.helm.sh/charts/eclipse-iot/ditto) and deploy Ditto via the Helm package manager.


<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
