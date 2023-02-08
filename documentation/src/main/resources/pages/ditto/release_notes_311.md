---
title: Release notes 3.1.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.1.1 of Eclipse Ditto, released on 05.01.2023"
permalink: release_notes_311.html
---

This is a bugfix release, no new features since [3.1.0](release_notes_310.html) were added.

## Changelog

Compared to the latest release [3.1.0](release_notes_310.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.1.1).

#### [Revert client actors from cluster sharding to router pool](https://github.com/eclipse-ditto/ditto/pull/1560)

In Ditto 3.1.0, a connection's `clientCount` greater than 1 did no longer guarantee that clients run on different 
connectivity services which could have scalability effects, e.g. regarding throttling.  
E.g. a connection with 5 clients in a cluster of 5 Ditto connectivity services could in the worst case have led to the 
situation that all 5 clients run on the same connectivity service.

This was fixed by undoing the in 3.1.0 added "cluster sharding" for connectivity clients.


### Bugfixes Ditto Java client

#### [Add options for live message conditions](https://github.com/eclipse-ditto/ditto-clients/pull/210)

For the Ditto Java client version 3.1.0 merging the client options for sending messages conditionally was forgotten
to be merged.  
The 3.1.1 client includes this new functionality.
