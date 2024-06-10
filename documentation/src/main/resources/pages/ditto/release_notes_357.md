---
title: Release notes 3.5.7
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.7 of Eclipse Ditto, released on 10.06.2024"
permalink: release_notes_357.html
---

This is a bugfix release, no new features since [3.5.6](release_notes_356.html) were added.

## Changelog

Compared to the latest release [3.5.6](release_notes_356.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.7).

#### Fixed that configuring "oauth2" based authentication for "devops" access does not allow to use a different OpenID connect provider

Reported issue [#1946](https://github.com/eclipse-ditto/ditto/issues/1946), which caused that a configured `oauth2` based
authentication of the `/devops` resources was not working with a different OIDC provider than used for the default authentication, 
was fixed by PR [#1948](https://github.com/eclipse-ditto/ditto/pull/1948).

#### Fix performance regression issue when running against MongoDB 6

An `aggregation` query done by Ditto as part of background deletion was quick with MongoDB 5, however the MongoDB 6 query planner
chose to use another index to run that aggregation query than MongoDB 5.  
Depending on the amount of data in Ditto, this could lead to a 500 times higher query time, resulting in a lot of unneeded
disk IOPS in MongoDB.

The fix provided in PR [#1956](https://github.com/eclipse-ditto/ditto/pull/1956) provides the option to configure a 
hint which index name to use should provide the option to fine-tune as needed.  
By default, the mentioned query now is configured to use the default index `"_id_"` which should be best for that 
specific case.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Fix Ingress Websocket authentication

PR [#1953](https://github.com/eclipse-ditto/ditto/pull/1953) provides a fix for authentication of the `/ws` (WebSocket)
resource by the nginx configured as "Ingress" in the Helm chart.

#### Add possibility to customize image used for pod-deletion-cost annotation patching job

PR [#1954](https://github.com/eclipse-ditto/ditto/pull/1954) provides the option to customize the image to be used
for the `pod-deletion-cost` annotation jobs.  
This can e.g. be useful in order to use a custom image and/or a private docker registry for this image.
