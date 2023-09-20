---
title: Release notes 3.3.7
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.7 of Eclipse Ditto, released on 15.09.2023"
permalink: release_notes_337.html
---

This is a bugfix release, no new features since [3.3.6](release_notes_336.html) were added.

## Changelog

Compared to the latest release [3.3.6](release_notes_336.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.7).

#### [Fix wrong exception message when policy could not be implicitly created when creating thing](https://github.com/eclipse-ditto/ditto/pull/1738)

The exception message thrown if implicit creation of an inline policy failed did not preserve the "root cause", 
e.g. a not existing policy import and e.g. always produced a text indicating to a policy "conflict".

#### [Allow spaces inside placeholder in target issued acknowledgement label](https://github.com/eclipse-ditto/ditto/pull/1743)

Issued acknowledgement label for target must start with connection ID.  
This can be achieved by using `connection:id` placeholder.  
Usually placeholders allow using spaces inside (right after opening and right before closing) curly brackets, 
for example, `{%raw%}{{ connection:id }}{%endraw%}`.  
But validation did not allow spaces in placeholder in target issued acknowledgement label.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) continues to receive 
valuable contributions in order to stabilize operation of Ditto and provide more configuration options.

#### [Spread ditto services equally among cluster nodes](https://github.com/eclipse-ditto/ditto/pull/1734)

1. Adds Pod Topology Spread Constraints to spread Ditto services equally among cluster nodes. 
   Each deployment has the ability to set constraints independently.
2. Extract nginx-ingress configuration hard-coded values to the values.yaml file:
   * replicaCount
   * updateStrategy
   * minReadySeconds
   * revisionHistoryLimit
   * resources

#### [Enable configuration of size "limits" in Helm chart](https://github.com/eclipse-ditto/ditto/pull/1735)

Exposes the configuration of limits like "thing size, policy size, message size" to Helm values.

#### [Allow priorityClassNames to be configured](https://github.com/eclipse-ditto/ditto/pull/1736)

Allows users to configure the priorityClass for pods, which allows more control over scheduling pods.

#### [Allow otelExporterOtlpEndpoint value to contain Release/Values references](https://github.com/eclipse-ditto/ditto/issues/1731)

Enhance Helm chart to use the `tpl` function in the place where `otelExporterOtlpEndpoint` gets used in order to resolve
a variable.
