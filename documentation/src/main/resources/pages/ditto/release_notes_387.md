---
title: Release notes 3.8.7
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.7 of Eclipse Ditto, released on 03.12.2025"
permalink: release_notes_387.html
---

This is a bugfix release, no new features since [3.8.6](release_notes_386.html) were added.

## Changelog

Compared to the latest release [3.8.6](release_notes_386.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.7).

#### Improve stability of ThingsAggregatorProxy in "things" service during restart

PR [#2265](https://github.com/eclipse-ditto/ditto/pull/2265) introduces the "ask-with-retry" pattern when things
are aggregated as result of a search request. This improves the stability by reducing errors during restarts of Ditto.

#### Fix search timeout error handling

In PR [#2268](https://github.com/eclipse-ditto/ditto/pull/2268) the error handling for search timeouts was fixed. Now,
when a search request times out, a proper timeout error is returned instead of a generic internal server error.  
And the `ERROR` log was replaced - as it is not a server issue in general if a user made an overly complex search request
which lead to a timeout.

#### Fix ordering problems on twin commands not requiring a response

When a lot of twin (modifying) commands were sent to the same thing in a short time, the order of processing was not
guaranteed in case the commands did not require a response (e.g. by unsing header `response-required: false` or `timeout: 0`).  
PR [#2269](https://github.com/eclipse-ditto/ditto/pull/2269) fixes this issue.

#### Fix wrong exceptions being thrown when duplicates occur in WoT parsing

PR [#2276](https://github.com/eclipse-ditto/ditto/pull/2276) fixes that an internal server error (500) was returned on
several WoT TD parsing errors, where a bad request error (400) would have been more appropriate.

#### Add missing "authorization" header to make sure it is not written to external headers

Up to now, if the `authorization` header was present in an incoming request, it was not explicitly removed from
external headers sent to external systems (e.g. via a Ditto connection, HTTP or Kafka for example).  
This was fixed via PR [#2278](https://github.com/eclipse-ditto/ditto/pull/2278) to make sure that e.g. no authentication
information is leaked unintentionally.

#### Fix retrieving thing for a message with a condition

PR [#2279](https://github.com/eclipse-ditto/ditto/pull/2279) fixes an issue when a message was sent via Ditto containing
a condition.  
In such cases, the thing was not retrieved properly (e.g. authorization was bypassed and the original headers of the
message sending were copied, e.g. copying also headers like `response-required` or `timeout`).

#### Updating dependencies to fix issues

* PR [#2273](https://github.com/eclipse-ditto/ditto/pull/2273) updates the used Kamon version to `2.8.0`
* PR [#2282](https://github.com/eclipse-ditto/ditto/pull/2282) updates the used Rhino version (used for javascript payload mapping) to `1.8.1`


### Helm chart

#### Add possibility to configure tick-interval and queue-size for tracing exporter

PR [#2270](https://github.com/eclipse-ditto/ditto/pull/2270) adds the possibility to configure `tick-interval` and `queue-size` 
for the metrics/tracing library Ditto uses, Kamon.  
Those values must e.g. be adjusted if spans are dropped due to high load (e.g. if the queue is full) or if the payload
of sent spans to the OTEL backend must be reduced.

#### Add feature toggle for configuring trace span metric reporting

PR [#2275](https://github.com/eclipse-ditto/ditto/pull/2275) adds a configuration option for disabling the reporting of
metrics for spans captured as part of tracing.  
Those metrics can be quite many, as they are also reported as histograms.  
When they are not needed, they can now be disabled to reduce the amount of metrics being sent to the metrics backend.

#### Provide additional GC tuning config via Helm values

PR [#2283](https://github.com/eclipse-ditto/ditto/pull/2283) contains additional Garbage collector configuration options
which can be used to better fine-tune Ditto for specific memory needs.
