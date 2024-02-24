---
title: Release notes 3.5.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.3 of Eclipse Ditto, released on 26.02.2024"
permalink: release_notes_353.html
---

This is a bugfix release, no new features since [3.5.2](release_notes_352.html) were added.

## Changelog

Compared to the latest release [3.5.2](release_notes_352.html), the following changes and bugfixes were added.

### Changes

#### Optimize Ditto internal pub/sub by adding subscribed for namespaces to topic

Issue [#1894](https://github.com/eclipse-ditto/ditto/issues/1894) described how to optimize the Ditto
internal pub/sub mechanism to filter out even more events before publishing to a subscriber which would
drop them.  
When using the `namespaces` filter when subscribing for events in e.g. connections, the selected namespaces
are now also part of the pub/sub topic which prevents unnecessary transmissions in the cluster.  
PR [#1900](https://github.com/eclipse-ditto/ditto/pull/1900) provides this internal optimization.

#### Update deployment files and unit tests to use MongoDB 6.0

In PR [#1897](https://github.com/eclipse-ditto/ditto/pull/1897) all unit tests and deployment files were
updated to use MongoDB 6.0.


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.3).

#### UI: Fix that incoming thing updates always repeated the first entry

The UI contained a bug which caused that when "watching" changes of a selected thing, the
"Incoming Thing Updates" list always repeated the first entry instead of correctly showing new entries.  
This was fixed in PR [#1901](https://github.com/eclipse-ditto/ditto/pull/1901).

#### Ensure consistency when doing signal enrichment

When e.g. a Ditto connection published many events for a single thing in a short time and using
[signal enrichment](basic-enrichment.hml), it was not guaranteed that the "enriched" data was from the same `revision`
as the published event - leading to inconsistencies for things with high frequent updates.  
This was reported in issue [#1893](https://github.com/eclipse-ditto/ditto/issues/1893) and fixed in PR 
[#1904](https://github.com/eclipse-ditto/ditto/pull/1904).

#### Fix time:now placeholder truncation

The in Ditto 3.5.0 added "truncation" for the [time:now placeholder](basic-placeholders.html#time-placeholder) did not 
work in certain cases.  
This was fixed via PR [#1903](https://github.com/eclipse-ditto/ditto/pull/1903).


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Added option to add custom annotations to podDeletionCostPatching jobs

In PR [#1902](https://github.com/eclipse-ditto/ditto/pull/1902) a new option to the Ditto Helm chart was added
in order to specify custom annotations for the Helm chart jobs adding a "pod deletion cost" to Ditto k8s pods.  
This e.g. is needed when running Ditto with Istio sidecars which would prevent the k8s jobs to finish.
