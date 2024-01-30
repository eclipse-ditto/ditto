---
title: Release notes 3.5.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.1 of Eclipse Ditto, released on 30.01.2024"
permalink: release_notes_351.html
---

This is a bugfix release, no new features since [3.5.0](release_notes_350.html) were added.

## Changelog

Compared to the latest release [3.5.0](release_notes_350.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.1).

#### Fix when using Policy Imports, the Ditto search index might lose data

Bug [#1882](https://github.com/eclipse-ditto/ditto/issues/1882) was introduced due to an unforeseen side effect introduced by a
[bugfix for Ditto 3.5.0](https://github.com/eclipse-ditto/ditto/pull/1872).  
PR [#1883](https://github.com/eclipse-ditto/ditto/pull/1883) provides a fix for this.

When making use of [Policy Imports](basic-policy.html#policy-imports) it is strongly advised to skip Ditto 3.5.0 and
directly use Ditto 3.5.1.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Fix hook-scripts configmap missing in Helm pre-upgrade Hooks

The newly introduced [calculation of pod deletion cost via a Helm hook](https://github.com/eclipse-ditto/ditto/pull/1871)
was missing setting up the configmap containing the script properly.  
This was fixed in [#1880](https://github.com/eclipse-ditto/ditto/pull/1880).  
PR [#1886](https://github.com/eclipse-ditto/ditto/pull/1886) in addition fixes a wrong name variable used for the configmap.

#### Fix Helm config for operatorMetrics not having defined a "scrapeInterval"

When using the newly introduced [operator metrics](installation-operating.html#operator-defined-custom-metrics) and
configuring via the Helm chart, there was an issue when no `scrapeInterval` was provided for a metric.  
This was fixed in [#1884](https://github.com/eclipse-ditto/ditto/pull/1884).
