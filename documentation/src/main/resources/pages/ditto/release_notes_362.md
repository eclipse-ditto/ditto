---
title: Release notes 3.6.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.2 of Eclipse Ditto, released on 06.11.2024"
permalink: release_notes_362.html
---

This is a bugfix release, no new features since [3.6.1](release_notes_361.html) were added.

## Changelog

Compared to the latest release [3.6.1](release_notes_361.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.2).

#### Fix aggregated metrics reporting `0` for non-matched filters

PR [#2050](https://github.com/eclipse-ditto/ditto/pull/2050) fixes the in Ditto 3.6.0 newly introduced 
[aggregation based metrics](installation-operating.html#operator-defined-custom-aggregation-based-metrics) reporting
`0` values for all non-matching `filters`.

#### Fix enter in Ditto UI submitting the wrong "authorize" button

PR [#2048](https://github.com/eclipse-ditto/ditto/pull/2048) fixes that pressing the "enter" button would not submit
the "authorize" button, but another one.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration
bugs and enhancements which are also addressed with this bugfix release.

#### Enhance Helm chart to configure Ditto via ConfigMap

PR [#2051](https://github.com/eclipse-ditto/ditto/pull/2051) introduces that Ditto services are configured not via
"System properties", but with a config file, mounted from a k8s `ConfigMap`.  
This can be important if e.g. many options are configured which lead to a huge amount of system properties, as those
system properties are passed as command line arguments and there is a limit in the length of possible command line 
argument size.
