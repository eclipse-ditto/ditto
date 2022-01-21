---
title: "Announcing Eclipse Ditto Release 2.3.0"
published: true
permalink: 2022-01-21-release-announcement-230.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams announces availability of Eclipse Ditto [2.3.0](https://projects.eclipse.org/projects/iot.ditto/releases/2.3.0).

It contains mainly new features around the Ditto ["live" channel](protocol-twinlive.html#live) which can be used to 
directly interact with devices.

Such live commands may now be easily [created via the HTTP API](2021-12-20-http-live-channel.html) - and in addition
a conventional API call targeting the persisted [twin](protocol-twinlive.html#twin) may now be automatically converted
to a live command, based on a passed [live channel condition](2021-12-22-live-channel-condition.html).

With that, we are proud that we now can provide a really powerful addition to Ditto's "Digital Twin pattern":
* requesting data from an actual device e.g. when it is currently connected/online (based on a [live channel condition](basic-conditional-requests.html#live-channel-condition) targeting the online status)
* fall back to its last known persisted state
  * if it is currently not online (the condition did not match)
  * or also as a fallback, if the device does not answer within e.g. 10 seconds
* all that within one single API call, including information from which source the data was reported back
  * the "live" device or the persisted "twin" 

## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: [https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

From our various [feedback channels](feedback.html) we however know of more adoption.  
If you are making use of Eclipse Ditto, it would be great to show this by adding your company name to that list of 
known adopters.  
In the end, that's one main way of measuring the success of the project.


## Changelog

The main improvements and additions of Ditto 2.3.0 are:

* HTTP API for "live" commands
* Smart channel strategy for live/twin read access
* Configurable allowing creation of entities (policies/things) based on namespace and authenticated subjects
* Allow using `*` as a placeholder for the feature id in selected fields

The following notable fixes are included:

* Fix potential concurrent modification errors when using JavaScript payload mapping and global variables
* Fix reconnect backoff for Kafka connections with authentication failures
* Fix caching of JWTs in HTTP push connections
* Fix potentially unreachable client actors in connections with `clientCount > 1`
* Fix search inconsistencies for very active things during shard relocation (e.g. on rolling updates)
* Fix that a Kafka connection with only targets remains "open" even if Kafka broker is not available
* Allow usage of absolute domain paths ending with a "." as Kafka bootstrap servers
* Ensure that Ditto pub/sub state is eventually consistent with a guaranteed upper time limit

The following non-functional work is also included:

* Update of several used dependencies

Please have a look at the [2.3.0 release notes](release_notes_230.html) for a more detailed information on the release.


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
* [eclipse/ditto-concierge](https://hub.docker.com/r/eclipse/ditto-concierge/)

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
