---
title: Release notes 0.3.0-M2
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.3.0-M2 of Eclipse Ditto, released on 15.06.2018"
permalink: release_notes_030-M2.html
---

Since the last milestone of Eclipse Ditto [0.3.0-M1](release_notes_030-M1.html), the following changes, new features and
bugfixes were added.


## Changes

### [Reduce network load for cache-sync](https://github.com/eclipse-ditto/ditto/issues/126)

With 0.3.0-M1 Ditto had a performance issue when managing more than ~100.000 Things in its memory as Ditto used a 
distributed cluster cache which was not intended to be used in that way. Over time, as cache entries could not be deleted
from this cache, a Ditto cluster got slower and slower.

This is fixed now in 0.3.0-M2 by introducing a new Ditto service: "ditto-concierge"
which is also shown in the [architecture overview](architecture-overview.html).

This is the biggest change in this milestone and required a lot of refactoring effort. Kudos to our two
committers Daniel and Yufei who did an amazing job: the roundtrip times in a Ditto cluster are now at a constant and
very good rate.

### [Cluster bootstrapping improved](https://github.com/eclipse-ditto/ditto/issues/167)

Ditto now uses the [akka-management](https://developer.lightbend.com/docs/akka-management/current/index.html) library
in order to bootstrap a new cluster. By default Ditto now uses a DNS-based approach to find its other cluster-nodes and
bootstrap a not yet formed cluster. This works very well for Docker (and Docker swarm) based clusters.

The benefit is also that the containers no longer need to be started in a specific order and with delay.

Future versions could also benefit from the other bootstrapping mechanisms for `kubenertes`, `mesos` or `ec2` (AWS) 
environment.

## New features

No new features for this milestone.


## Bugfixes

### Search index fixes

The search-index of the [Ditto search](basic-search.html) had several issues which lead to a poor query performance
when searching for Things.

These issues were adressed in several fixes:
* [#159](https://github.com/eclipse-ditto/ditto/pull/159)
* [#169](https://github.com/eclipse-ditto/ditto/pull/169)
* [#175](https://github.com/eclipse-ditto/ditto/pull/175)


### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.3.0-M2+).

