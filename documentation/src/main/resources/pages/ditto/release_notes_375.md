---
title: Release notes 3.7.5
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.5 of Eclipse Ditto, released on 22.05.2025"
permalink: release_notes_375.html
---

This is a bugfix release, no new features since [3.7.4](release_notes_374.html) were added.

## Changelog

Compared to the latest release [3.7.4](release_notes_374.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.5).

#### Fix wildcard index of search collection in MongoDB is missing _id field resulting in poor sorting performance

PR [#2170](https://github.com/eclipse-ditto/ditto/pull/2170) addresses a potential performance issue for Ditto's [search](basic-search.html).  
In case a single user e.g. has access to `READ` a lot of things and queries with a simple query those things, adding
sorting the results explicitly by their `thingId`, this sort was not using the MongoDB wildcard index and was instead
done in memory, resulting in a potential really slow API call.

The PR contains adding another wildcard index, adding the `_id` field as well as "compound index".  
This however can only be used when using MongoDB version 7.0 or higher, were support for compound indexes including a wildcard index was added.

Therefore, this is not activated as default index - also in order to not break existing Ditto deployments.

The index can be activated via configuration - there is a new search configuration option `activated-index-names` available, 
e.g. also exposed via the Helm chart.

#### Fixing CPU hotspots in mapping strategies and JSON key validation

PR [#2171](https://github.com/eclipse-ditto/ditto/pull/2171) provides a fix for Ditto internal performance hotspots.

One of those is configurable as it would alter the behavior of how Ditto validates JSON keys.  
Currently, Ditto prevents using control characters in JSON keys by validating the keys against a regex. 
The validation is done recursively for each contained key in a JSON object and every time a JSON object is internally built.

This can have quite an influence on CPU usage, so this behavior can be deactivated via the environment variable
`DITTO_DEVOPS_FEATURE_JSON_KEY_VALIDATION_ENABLED`.

It can e.g. make sense to disable this validation if there are other means to ensure that JSON keys are valid and no 
control characters are used.  
For example, by using [WoT based validation](basic-wot-integration.html#configuration-of-thing-model-based-validation).


### Helm Chart

#### Added more configuration options for ingress-nginx

PR [#2167](https://github.com/eclipse-ditto/ditto/pull/2167) provides more options to configure the ingress-nginx controller:
* workerProcesses
* workerConnections
* workerOpenFiles 

#### Fixed setting tag of hook image in Helm chart

PR [#2169](https://github.com/eclipse-ditto/ditto/pull/2169) fixes a mix up of parameters which caused the "pod deletion cost"
hook image version to be always `latest` instead of the configured version.
