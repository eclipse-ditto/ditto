---
title: Release notes 3.8.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.3 of Eclipse Ditto, released on 27.10.2025"
permalink: release_notes_383.html
---

This is a bugfix release, no new features since [3.8.2](release_notes_382.html) were added.

## Changelog

Compared to the latest release [3.8.2](release_notes_382.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.3).

#### Fix empty object removal flag prevents null values from deleting fields in PATCH operations

Issue [#2244](https://github.com/eclipse-ditto/ditto/issues/2244) described a bug introduced with Ditto 3.8's [config
option](installation-operating.html#empty-object-removal-after-patch-condition-filtering) 
`remove-empty-objects-after-patch-condition-filtering` which caused that in merge / PATCH requests the `null` value
did no longer delete as defined in the [JSON merge patch](https://tools.ietf.org/html/rfc7396) spec which Ditto implements
for merge / PATCH commands.  
This was fixed in PR [#2245](https://github.com/eclipse-ditto/ditto/pull/2245).

#### Fixes for reducing ERROR logs during restart of ditto-things service

PR [#2246](https://github.com/eclipse-ditto/ditto/pull/2246) contains several fixes and improvements to reduce the
amount of ERRORs logged during a rolling restart of "things" service.

#### Fix that feature toggles were not applied correctly

PR [#2247](https://github.com/eclipse-ditto/ditto/pull/2247) resolves that most of Ditto's feature toggles / flags 
were not applied correctly.

#### Add configuration for string escaping buffer size in order to be able to tune a very often invoked operation

PR [#2248](https://github.com/eclipse-ditto/ditto/pull/2248) provides a configuration to configure the buffer size
of a StringBuilder in `org.eclipse.ditto.json.JavaStringToEscapedJsonString` class - which is used a lot for all JSON
operations (serialization / deserialization). If identified during profiling that this method is a bottleneck for the 
JSON payload specific to the Ditto installation, this buffer size can now be tuned and e.g. increased from default `1.0` 
to e.g. `2.0` in order to reduce the number of buffer resizes and copies.

### Helm chart

#### Enhance Helm config option by G1 GC tuning and thing supervisor ask timeout config

PR [#2249](https://github.com/eclipse-ditto/ditto/pull/2249) provides more options to tune the JVM G1 garbage collector.  
And additionally to configure "ask timeouts" for the thing supervisor.
