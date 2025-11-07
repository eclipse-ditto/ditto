---
title: Release notes 3.8.4
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.4 of Eclipse Ditto, released on 07.11.2025"
permalink: release_notes_384.html
---

This is a bugfix release, no new features since [3.8.3](release_notes_383.html) were added.

## Changelog

Compared to the latest release [3.8.3](release_notes_383.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.4).

#### Policies with expired (but not yet deleted) subjects could not be updated

Issue [#2233](https://github.com/eclipse-ditto/ditto/issues/2233) described a bug where policies could not be updated
when policy subjects in that policy were expired and failed to be deleted from the policy.
This was fixed in PR [#2253](https://github.com/eclipse-ditto/ditto/pull/2253).

#### Tune CBOR serialization and JsonSchema validation

PR [#2252](https://github.com/eclipse-ditto/ditto/pull/2252) contains performance improvements for Ditto-internal CBOR serialization and JsonSchema validation, 
reducing the amount of bytes allocated on the heap.

#### Optimize communication for thing/policy/connection existance checking by bypassing sharding proxy

PR [#2254](https://github.com/eclipse-ditto/ditto/pull/2254) bypassed the "sharding proxy" in order to check for existence
of things, policies and connections. This reduces the communication overhead and improves reliability in case shards
are being moved in the cluster, e.g. during a rolling restart.

#### Fix configuring missing cluster sharding healthcheck names

PR [#2255](https://github.com/eclipse-ditto/ditto/pull/2255) adds `healtcheck` configuration for Ditto shard region names
to ensure that only pods which allocated shard regions are considered healthy by Kubernetes readiness probes.  
This should improve reliability of Ditto clusters during rolling restarts.

#### Fixed exceptions during policy enforcer loading being silently treated as "empty enforcer"

PR [#2256](https://github.com/eclipse-ditto/ditto/pull/2256) fixes an issue where exceptions during loading of policy enforcers
were silently treated as "empty enforcer", leading to unexpected authorization results on e.g. "Ask timeout" errors, especially
during rolling restarts.


### Helm chart

#### Enhanced Helm configuration with terminationGracePeriodSeconds, garbage collection logging and more

PR [#2258](https://github.com/eclipse-ditto/ditto/pull/2258) enhances the available Helm configuration options with:
* Kubernetes `terminationGracePeriodSeconds` configuration for Ditto pods
* Garbage collection logging configuration
* Pekko cluster coordinated shutdown phases configuration
* "Ask with retry" configuration options for "edge" services (gateway, connectivity) and for enforcement (things, policies services)
