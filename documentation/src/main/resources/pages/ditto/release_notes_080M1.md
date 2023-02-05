---
title: Release notes 0.8.0-M1
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.8.0-M1 of Eclipse Ditto, released on 14.08.2018"
permalink: release_notes_080-M1.html
---

Ditto is doing a version jump from the last milestone version 0.3.0-M2 to 0.8.0-M1. The reasons for this are:
* Ditto wants to provide a first non-milestone release in the next months
* the Ditto codebase is a very mature one, with most of the Java APIs being stable since the very first milestone, 
a 0.3.0 version would be an understatement
* the Ditto main APIs (things, policies, search) are stable and backwards compatible to all previously released milestones

Therefore Ditto's first release deserves to be a "mature 0.x" version, we think 0.8.0 reflects this quite well. What
is missing for a 1.0.0 are more features towards desired/reported state handling which will be on the agenda for after
0.8.0.

Since the last milestone of Eclipse Ditto [0.3.0-M2](release_notes_030-M2.html), the following changes, new features and
bugfixes were added.


## Changes

### [Marked some DittoHeaders as internal](https://github.com/eclipse-ditto/ditto/pull/195)

In order to prevent that a user of Ditto's API (e.g. WebSocket or AMQP) sets arbitrary security relevant headers, 
those `DittoHeaders` are no marked as "not readable from external". Other headers which should not be propagated to 
the outside of Ditto are marked as "not to be written to external".

### [Update to Kamon 1.0 and report metrics to Prometheus](https://github.com/eclipse-ditto/ditto/issues/105)

Previously, Ditto was using Kamon 0.6.x and reported metrics/traces to a [Graphite](https://graphiteapp.org) back-end. 
Together with the update to Kamon 1.0 the Ditto services now provide HTTP endpoints which can be scraped by 
[Prometheus](https://prometheus.io) in order to get insights about the services.

For more information, please read the [monitoring section](installation-operating.html#monitoring) in the operating 
guide.


## New features

### [Kubernetes cluster bootstrapping](https://github.com/eclipse-ditto/ditto/pull/201)

Ditto now discovers its cluster nodes automatically also when running in Kubernetes. It uses the 
kubernetes-api in order to discover the other cluster nodes.

An example on how to run Ditto with Kubernetes is provided in the /kubernetes git directory.

### [Allow placeholders inside connection config](https://github.com/eclipse-ditto/ditto/issues/178)

In many cases configuration values of a [Connection](connectivity-manage-connections.html) can be dependent on the 
message's headers. This feature allows Ditto connections to access header fields and use it in the connection's 
`authorizationContext`.

For connecting to a [Eclipse Hono](connectivity-protocol-bindings-amqp10.html) instance, it is e.g. possible to access the
from Hono authenticated `device-id` via the placeholder `header:device-id`.


## Bugfixes

### [Fixed excessive memory consumption of things-service](https://github.com/eclipse-ditto/ditto/pull/194)

In previous versions, Ditto's `things-service` created a lot of instances for each Thing which was loaded into memory.
This is now optimized so that the service does no longer need so much memory.

### Stabilization of AMQP 1.0 and 0.9.1 connections

The relatively new connectivity feature which Ditto added in previous versions had still some bugs and stability issues.
In this milestone we put a lot of effort in further stabilizing AMQP 1.0 and 0.9.1 connections

These issues were addressed in several fixes:

* [#189](https://github.com/eclipse-ditto/ditto/pull/189)
* [#178](https://github.com/eclipse-ditto/ditto/issues/178)

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.8.0-M1+).

