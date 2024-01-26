---
title: "Announcing Eclipse Ditto Release 3.5.0"
published: true
permalink: 2024-01-26-release-announcement-350.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto team wished you a happy new year and is excited to announce availability of Ditto
[3.5.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.5.0).

In 3.5.0 a lot of UI improvements are contained and several smaller but very useful features were added.  
Thanks a lot to the contributors who contributed to this release, this is really appreciated.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.5.0 are:

Eclipse Ditto 3.5.0 focuses on the following areas:

* **Search in the history** of a **single thing** using an RQL filter
* **Configure per namespace** the **fields to index** in Ditto's **search index**
* Configure **defined search count queries** to be **exposed as Prometheus metrics** by Ditto periodically
* Providing **new placeholder functionality** to the **time placeholder**, being able to **add and subtract to/from
  the current time** and to truncate the time to a given unit
* Enhance **WoT (Web of Things) JSON skeleton creation** to be able to **fail with an exception** on **invalid** WoT models
* Provide **negative numbers** when **querying for the historical events** of an entity (thing, policy, connection) in order to
  **e.g. get "latest 10" events**
* UI enhancements:
  * Show **policy imports** in Ditto explorer UI
  * Enhance UI **Operations** functionality to be able to **perform devops/piggyback commands**
  * Allow **editors in UI** to toggle **full screen mode**
  * **Display attributes in UI** inside a **JSON editor** in order to correctly display structured JSON payloads
  * Enhance "**Incoming Thing Updates**" section by **displaying "Action" and "Path" in the table** and adding a **dropdown to
    select the amount of details** to show per event
  * Add **client side filter option** for filtering **Incoming Thing Updates** and **Connection logs**

The following non-functional work is also included:

* Configured docker-compose to by default retain only the last 50m of log messages per Ditto service
* Migrated SLF4J to version 2.x and logback to version 1.4.x
* Benchmark tool improvements and fixes
* Improve cluster stability when running in Kubernetes, e.g. on updates or k8s node-shutdowns

The following notable fixes are included:

* Fix enriching Thing creation events with the inlined `_policy`
* Fixed that Ditto's own calculated "health" was not exposed to the `/alive` endpoint scraped by Kubernetes to check for
  aliveness of single services
* Fixed that no cache was used when updating the search index when an "imported" policy was modified

Please have a look at the [3.5.0 release notes](release_notes_350.html) for a more detailed information on the release.


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
