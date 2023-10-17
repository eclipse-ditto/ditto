---
title: "Announcing Eclipse Ditto Release 3.4.0"
published: true
permalink: 2023-10-17-release-announcement-340.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams is proud to announce the availability of Eclipse Ditto 
[3.4.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.4.0).

Version 3.4.0 mainly concentrates on exchanging the use of the [Akka toolkit](https://akka.io) 
(due to a change in licensing) with its fork [Apache Pekko](https://pekko.apache.org/) which remains Apache 2.0 licensed.  
Apart from that, several improvements are also included which can be found in the changelog.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.4.0 are:

Eclipse Ditto 3.4.0 focuses on the following areas:

* Supporting **HTTP `POST`** for performing **searches** with a very **long query**
* Addition of a **new placeholder** to use **in connections** to use **payload of the thing JSON** e.g. in headers or addresses
* New **placeholder functions** for **joining** multiple elements into a single string and doing **URL-encoding and -decoding**
* Configure **MQTT message expiry interval for published messages** via a header
* **Reduce patch/merge thing commands** to **modify** only the **actually changed values** with a new option
* UI enhancements:
  * Adding sending messages to Things
  * Made UI (at least navigation bar) responsive for small screen sizes
  * Increase size of JSON editors in "edit" mode

The following non-functional work is also included:

* **Swapping the [Akka toolkit](https://akka.io)** (because of its switch of license to [BSL License](https://www.lightbend.com/akka/license-faq) after Akka v2.6.x)
  **with its fork [Apache Pekko](https://pekko.apache.org/)** which remains Apache 2.0 licensed.
* Support for using **AWS DocumentDB** as a replacement for MongoDB
* Improve logging by adding the **W3C Trace Context** `traceparent` header as MDC field to logs
* Adjust handling of special MQTT headers in MQTT 5
* Optimize docker files
* Migration of Ditto UI to TypeScript
* There now is an official **[Eclipse Ditto Benchmark](2023-10-09-ditto-benchmark.html)** which shows how Ditto is able
  to scale horizontally and provides some tuning tips
* Addition of a **benchmark tooling** to run own Ditto benchmarks

The following notable fixes are included:

* Fixed that failed retrieval of a policy (e.g. after policy change) leads to search index being "emptied out"
* Fixed that putting metadata when updating a single scalar value did not work
* UI fix, fixing that patching a thing will null values did not reflect that change in the UI

Please have a look at the [3.4.0 release notes](release_notes_340.html) for a more detailed information on the release.


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
