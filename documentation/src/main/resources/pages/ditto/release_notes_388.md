---
title: Release notes 3.8.8
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.8 of Eclipse Ditto, released on 09.12.2025"
permalink: release_notes_388.html
---

This is a bugfix release, no new features since [3.8.7](release_notes_387.html) were added.

## Changelog

Compared to the latest release [3.8.7](release_notes_387.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.8).

#### Reduce memory usage regarding duplicated configs in Persistence and other actors

PR [#2285](https://github.com/eclipse-ditto/ditto/pull/2285) fixes an issue about unnecessary memory usage in regard
to getting and storing static configuration data in several actors (e.g. Persistence actor).  
Now, the configuration is only stored once and shared via references to reduce memory footprint.

#### Update lz4-java version to fix critical security vulnerabilities

PR [#2289](https://github.com/eclipse-ditto/ditto/pull/2289) updates `l44-java` dependency, fixing known CVEs:
* [CVE-2025-12183](https://nvd.nist.gov/vuln/detail/CVE-2025-12183)
* [CVE-2025-66566](https://nvd.nist.gov/vuln/detail/CVE-2025-66566)


### Helm chart

#### Introduce feature toggle to choose implementation of policy evaluator

PR [#2286](https://github.com/eclipse-ditto/ditto/pull/2286) adds a configuration option (exposed as feature toggle) 
to the Helm chart to choose between the two implementations of the policy evaluator:
* the default implementation which ensure high throughput and low latency however with the cost of additional memory usage
* a memory optimized implementation which uses less memory but has a bit higher latency and lower throughput
