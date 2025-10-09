---
title: "Announcing Eclipse Ditto Release 3.8.0"
published: true
permalink: 2025-10-10-release-announcement-380.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Eclipse Ditto team is excited to announce the availability of a new minor release, including new features: 
Ditto [3.8.0](https://projects.eclipse.org/projects/iot.ditto/releases/3.8.0).



## Adoption

Companies are willing to show their adoption of Eclipse Ditto publicly: 
[https://iot.eclipse.org/adopters/?#iot.ditto](https://iot.eclipse.org/adopters/?#iot.ditto)

When you use Eclipse Ditto it would be great to support the project by putting your logo there.  


## Changelog

The main improvements and additions of Ditto 3.8.0 are:

* **Diverting** Ditto **connection responses** to other connections (e.g. to allow multi-protocol workflows)
* Dynamically **re-configuring WoT validation settings** without restarting Ditto
* **Enforcing** that WoT model based **thing definitions are used** and match a certain pattern when **creating new things**
* Support for **OAuth2 "password" grant** type for **authenticating outbound HTTP connections**
* **Configure JWT claims** to be **added** as information to command **headers**
* Added support for **client certificate based authentication** for **Kafka and AMQP 1.0** connections
* Extend **"Normalized"** connection **payload mapper** to include **deletion events**
* Support **silent token refresh** in the **Ditto UI** when using **SSO via OAuth2/OIDC**
* Enhance **conditional updates** for **merge thing commands** to contain **several conditions** to dynamically decide which parts of a thing to update and which not

The following non-functional work is also included:

* **Improving** WoT based **validation performance** for **merge** commands
* **Enhancing distributed tracing**, e.g. with a span for the authentication step and by adding the error response for failed API requests
* Updating dependencies to their latest versions
* Providing **additional configuration options** to **Helm values**

The following notable fixes are included:

* Fixing **nginx CORS configuration** which caused **Safari / iOS** browsers to fail with **CORS errors**
* Fixing **transitive resolving of Thing Models** referenced with `tm:ref`
* Fixing **sorting on array fields** in Ditto search
* Fixing issues around **"put-metadata"** in combination **with merge commands**
* Fixing that **certificate chains** for **client certificate based authentication** in Ditto connection was not fully parsed
* Fixing **deployment of Ditto on OpenShift**

Please have a look at the [3.8.0 release notes](release_notes_380.html) for a more detailed information on the release.


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
