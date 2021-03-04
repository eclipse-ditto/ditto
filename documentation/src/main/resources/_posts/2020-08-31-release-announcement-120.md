---
title: "Announcing Eclipse Ditto Release 1.2.0"
published: true
permalink: 2020-08-31-release-announcement-120.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today, the Ditto team is happy to announce the second minor (feature) update of Ditto `1.x`:<br/>
**Eclipse Ditto 1.2.0**

1.2.0 focuses on the following areas:

* "At least once" (QoS 1) processing of messages consumed/sent via Ditto's managed [connections](basic-connections.html)
   (via [acknowledgements](basic-acknowledgements.html))
* Addition of a `"_created"` timestamp for newly created digital twins (things)
* Possibility to inject arbitrary `"_metadata"` when modifying digital twins (things)
* Authenticate HTTP push connections with client certificates

Please have a look at the [1.2.0 release notes](release_notes_120.html) for a more detailed information on the release.


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
