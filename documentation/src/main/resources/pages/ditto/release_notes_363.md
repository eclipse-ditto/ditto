---
title: Release notes 3.6.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.3 of Eclipse Ditto, released on 27.11.2024"
permalink: release_notes_363.html
---

This is a bugfix release, no new features since [3.6.2](release_notes_362.html) were added.

## Changelog

Compared to the latest release [3.6.2](release_notes_362.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.6.3).

#### Fixed tracing and moving traces to their correct parent in many places

PR [#2060](https://github.com/eclipse-ditto/ditto/pull/2060) fixes that OpenTelemetry traces often were not correctly
assigned to the "correct" parent span.

#### Fix that boolean values could not be used in aggregated metrics

PR [#2068](https://github.com/eclipse-ditto/ditto/pull/2068) fixes issue [#2067](https://github.com/eclipse-ditto/ditto/issues/2067)
and adds support for using boolean values for filter in [aggregation based metrics](installation-operating.html#operator-defined-custom-aggregation-based-metrics).

#### Fix that resolving WoT extensions did not preserve "submodels" from parent models

PR [#2069](https://github.com/eclipse-ditto/ditto/pull/2069) fixes a WoT "extension" resolving bug, `tm:submodels` defined
in extended WoT models were not "copied" to the "links" of the extending models.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration
bugs and enhancements which are also addressed with this bugfix release.

#### Enhance Helm chart by startupProbe

PR [#2063](https://github.com/eclipse-ditto/ditto/pull/2063) enhances the Helm chart with a `startupProbe` - which
makes the `initialDelaySeconds` for the `readinessProbe` obsolete and a Ditto pod can become more quickly "ready" as
a result.  
Also, the configuration of `topologySpreadConstraints` was enhanced to be an array and be able to take more than one
constraint.

#### Fix Pod Disruption Budget value for thingssearch

The `PodDisruptionBudget` of the Helm chart referenced for the `thingssearch` service the value of the `things` service,
this was fixed via PR [#2070](https://github.com/eclipse-ditto/ditto/pull/2070).
