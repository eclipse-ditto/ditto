---
title: "Announcing Eclipse Ditto Release 3.2.0"
published: true
permalink: 2023-03-08-release-announcement-320.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

The Eclipse Ditto teams is proud to announce the availability of Eclipse Ditto 
[3.2.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.2.0).

Version 3.2.0 brings a new **History API**, **Eclipse Hono** connection type, **case-insensitive searches** and
other smaller improvements, e.g. on the Ditto UI and in the JS client.


## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.2.0 are:

* New **History API** in order to be able to:
  * access historical state of things/policies/connections (with either given revision number or timestamp)
  * stream persisted events of things/policies via async APIs (WebSocket, Connections) and things also via existing SSE (Server-Sent-Events) API
  * configure deletion retention of events in the database for each entity
* Addition of new **Eclipse Hono** connection type for Ditto managed connections
* Option to do **case-insensitive searches** and addition of a new RQL operator to declare case-insensitive like: `ilike`
* UI enhancements:
  * Push notifications on the Ditto UI using SSE (Server-Sent-Events), e.g. on thing updates
  * Autocomplete functionality for the search slot
  * Added configuring `Bearer` auth type for the "devops" authentication
* JavaScript client:
  * Support for **"merge" / "patch"** functionality in the **JS client**

The following non-functional enhancements are also included:

None in this release.

The following notable fixes are included:

* Undo creating implicitly created policy as part of thing creation if creation of thing failed

Please have a look at the [3.2.0 release notes](release_notes_320.html) for a more detailed information on the release.


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
