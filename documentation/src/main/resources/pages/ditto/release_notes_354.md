---
title: Release notes 3.5.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.4 of Eclipse Ditto, released on 08.04.2024"
permalink: release_notes_354.html
---

This is a bugfix release, no new features since [3.5.3](release_notes_353.html) were added.

## Changelog

Compared to the latest release [3.5.3](release_notes_353.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.4).

#### Fix Policy announcements not working when connection is using namespace filtering

Ditto 3.5.3 contained an [optimization for  Ditto internal pub/sub using namespace filtering](https://github.com/eclipse-ditto/ditto/issues/1894).  
This however did not take into account [Policy announcements](basic-signals-announcement.html) which was reported in
issue [#1920](https://github.com/eclipse-ditto/ditto/issues/1920) for which PR [#1921](https://github.com/eclipse-ditto/ditto/pull/1921) provides a fix.

#### Fix weak eTag handling of If-Match and If-None-Match headers

The [conditional request headers](httpapi-concepts.html#conditional-requests) `If-Match` and `If-None-Match` did not handle
"weak" eTags correctly - they always assumed strong eTags for `If-Match` and weak etags for `If-None-Match`.  
This was fixed in PR [#1924](https://github.com/eclipse-ditto/ditto/pull/1924).

#### Fix nested lookup of tm:refs in WoT model extension resolving

PR [#1923](https://github.com/eclipse-ditto/ditto/pull/1923) fixes a bug when in the [WoT integration](basic-wot-integration.html) a
ThingModel referenced another ThingModel which again referenced another one using `tm:ref`.  
This kind of "nested" lookup lead to a parsing error - which was fixed.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Adding Helm gateway option for token-integration-subject

In PR [#1912](https://github.com/eclipse-ditto/ditto/pull/1912) a new Helm configuration was added in order to configure
the environment variable `OAUTH_TOKEN_INTEGRATION_SUBJECT` for Ditto's gateway service.

#### Add support to use kubernetes secrets for basicAuthUsers passwords

In PR [#1913](https://github.com/eclipse-ditto/ditto/pull/1913) the Helm chart was enhanced to use existing k8s secrets
for obtaining usernames and passwords for authenticating users via nginx using basic auth.

```
# existingSecret contains the name of existing secret containing user and password
#  format: ${user}:${password}, where secret key is ${user} and value is ${password}
#  example creating secret for users ditto and jane:
#    kubectl create secret generic ditto-basic-auth --from-literal ditto=ditto --from-literal jane=janesPw
```

#### Fix issues with trailing slash on ui and apidoc

PR [#1916](https://github.com/eclipse-ditto/ditto/pull/1916) fixes accessing the Ditto UI and apidocs when not adding a
trailing slash.
