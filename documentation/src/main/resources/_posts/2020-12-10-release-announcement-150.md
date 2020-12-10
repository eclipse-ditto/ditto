---
title: "Announcing Eclipse Ditto Release 1.5.0"
published: true
permalink: 2020-12-10-release-announcement-150.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Wrapping up this crazy year, the Ditto team is happy to announce the next feature update of Ditto `1.x`: 
**Eclipse Ditto 1.5.0**

1.5.0 focuses on:

* Desired properties management (CRUD)
* Addition of "cloudevents" HTTP endpoint
* Ditto internal pub/sub supports using a "grouping" concept which improves Ditto's scalability capabilities
* Issuing "weak Acknowledgements" when a command requesting acks was filtered out by Ditto (improvement of "at least once" delivery scenarios)
* Feature ID may be used in header mappings of connections

Please have a look at the [1.5.0 release notes](release_notes_150.html) for a more detailed information on the release.


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
