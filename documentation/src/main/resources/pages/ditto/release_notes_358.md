---
title: Release notes 3.5.8
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.8 of Eclipse Ditto, released on 12.06.2024"
permalink: release_notes_358.html
---

This is a bugfix release, no new features since [3.5.7](release_notes_357.html) were added.

## Changelog

Compared to the latest release [3.5.7](release_notes_357.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.8).

#### Fix generation of WoT Thing Descriptions wrong base and href paths

Reported issue [#1959](https://github.com/eclipse-ditto/ditto/issues/1959) correctly identified a bug in the 
[WoT (Web of Things) Thing Description generation](basic-wot-integration.html#thing-description-generation).  
The generated `base` was not ending with a slash and the relative `href` in the TDs were beginning with a slash which 
leads to wrongly interpreted absolute paths, e.g. when using the browser `URL` object.

This was fixed in PR [#1962](https://github.com/eclipse-ditto/ditto/pull/1962).

#### Fix performance regression issue when running against MongoDB 6 - part 2

In Ditto [3.5.7](release_notes_357.html) a MongoDB `aggregation` query done by Ditto as part of background deletion 
was aimed to be optimized.  
This fix provided in PR [#1956](https://github.com/eclipse-ditto/ditto/pull/1956) however could lead to side effects resulting again in very long-running 
aggregation queries.

With PR [#1961](https://github.com/eclipse-ditto/ditto/pull/1961) provided in this bugfix release, the options to 
configure the index `hint`s and even to create additional indexes were enhanced and the defaults changed so that no `hint` 
is provided by default, but the MongoDB query planner decides on which index to use.  
The option to configure a `hint` however was enhanced, depending on which fields were present in the `$match` aggregation step.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Provided configuration for MongoReadJournal aggregation index hinting

Provide Helm value options to configure settings for the above-mentioned PR 
[#1961](https://github.com/eclipse-ditto/ditto/pull/1961).
