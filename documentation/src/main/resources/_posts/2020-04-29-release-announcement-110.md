---
title: "Announcing Eclipse Ditto Release 1.1.0"
published: true
permalink: 2020-04-29-release-announcement-110.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today, approximately 4 months after Eclipse Ditto's [1.0.0](2019-12-12-release-announcement-100.html) release, 
the team is happy to announce the first minor (feature) update of Ditto `1.0`:<br/>
**Eclipse Ditto 1.1.0**

The Ditto team was quite busy, 1.1.0 focuses on the following areas:

* Management of [Policies](basic-policy.html) via [Ditto Protocol](protocol-specification-policies.html)
    * Addition of policy APIs in Ditto [Java client](client-sdk-java.html)
* Possibility to [search](basic-search.html) via [Ditto Protocol](protocol-specification-things-search.html)
    * Addition of search APIs in Ditto [Java client](client-sdk-java.html)
* Enrich published Ditto events/message via additional [custom fields](basic-enrichment.html) of the affected thing
    * Addition of enrichment APIs in Ditto [Java client](client-sdk-java.html)
* Support for establishing managed [connections](basic-connections.html) via [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* End-2-end [acknowledgements](basic-acknowledgements.html) preparing Ditto to enable "at least once" processing
    * Addition of acknowledgement APIs in Ditto [Java client](client-sdk-java.html) 
* Officially documented [pre-authenticated](installation-operating.html#pre-authentication) authentication mechanism
* Use of Java 11 for running Ditto containers
* Deprecation of API version 1 (authorization via ACL mechanism)
* Use of CBOR as cluster internal replacement for JSON serialization
* Further improvements on increasing throughput

Please have a look at the [1.1.0 release notes](release_notes_110.html) for a more detailed information on the release.


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
