---
title: Release notes 3.3.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.2 of Eclipse Ditto, released on 30.06.2023"
permalink: release_notes_332.html
---

This is a bugfix release, no new features since [3.3.0](release_notes_330.html) were added.

## Changelog

Compared to the latest release [3.3.0](release_notes_330.html), the following changes and bugfixes were added.

### Changes

We had to skip Ditto version 3.3.1, as there were errors in our release pipeline.


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.2).

#### [Fix blocking Pekko dispatcher thread on WoT skeleton creation](https://github.com/eclipse-ditto/ditto/pull/1666)

Ditto's [WoT integration](basic-wot-integration.html) did block the Pekko dispatcher thread which could lead to deadlock 
situations when e.g. creating new twins based on a WoT ThingModel.  

This was solved and the complete WoT operations are now executed asynchronously.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm), which was enhanced and changed 
a lot for version 3.3.0, contained some configuration bugs which are also addressed with this bugfix release.


#### [Fix that nginx's worker_processes setting 'auto' causes problems with workers having many CPUs](https://github.com/eclipse-ditto/ditto/pull/1667)

Configure the "dittoui"'s nginx to only use 1 worker process, as it only serves static content.  
Add configuration option in the Helm chart `values.yaml` to configure the nginx's used worker_processes and default to 4

#### [Fix wrong config path for pulling devops and status password from helm values](https://github.com/eclipse-ditto/ditto/pull/1671)

The devopsPassword and statusPassword were read from `.Values.gateway.devopsPassword` and `.Values.gateway.statusPassword`.
In the `values.yaml` these two values were however located at different configuration levels.

As a result, the two passwords werew always regenerated and the passed passwords were not used.

#### [Configure queryReadConcern: "local" for ditto things-search by default](https://github.com/eclipse-ditto/ditto/pull/1672)

The `readConcern` was by default configured to `"linearizable"` which however fails in a "single instance" MongoDB setup.  
Changed to be by default `queryReadConcern: "local"` with hint to configure otherwise for replicated MongoDB setups.

#### [Support for ingress controller](https://github.com/eclipse-ditto/ditto/pull/1668)

The Ditto Helm chart now supports to additionally deploy a (nginx-based) "Ingress controller", if configured.  
By default, the deployment of the Ingress controller is however disabled.
