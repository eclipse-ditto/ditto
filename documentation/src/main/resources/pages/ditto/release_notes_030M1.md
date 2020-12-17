---
title: Release notes 0.3.0-M1
tags: [release_notes, connectivity]
keywords: release notes, announcements, changelog
summary: "Version 0.3.0-M1 of Eclipse Ditto, released on 26.04.2018"
permalink: release_notes_030-M1.html
---

Since the last milestone of Eclipse Ditto [0.2.0-M1](release_notes_020-M1.html), the following changes, new features and
bugfixes were added.


## Changes

### [OpenJ9 based Docker images](https://github.com/eclipse/ditto/pull/133)

The official Eclipse Ditto Docker images are now based on the [Eclipse OpenJ9](https://www.eclipse.org/openj9/) JVM.
With this JVM (cheers to the OpenJ9 developers for this awesome JVM) Ditto's containers need a lot less memory having
similar if not better throughput.

This especially comes in handy for the [Ditto sandbox](https://ditto.eclipseprojects.io) which only has 4GB RAM and 1 CPU core ;-)

### AMQP bridge renaming

Ditto's former AMQP bridge service was renamed to `connectivity` as it no longer only manages AMQP 1.0 connections, see
the [new features](#new-features).


## New features

### [AMQP 0.9.1 connectivity](https://github.com/eclipse/ditto/issues/129)

The new `connectivity` service can now, additionally to AMQP 1.0, manage and open connections to AMQP 0.9.1 endpoints 
(e.g. provided by a [RabbitMQ](https://www.rabbitmq.com) broker).

### [Payload mapping to/from Ditto Protocol](https://github.com/eclipse/ditto/issues/130)

The new `connectivity` service can now also map message arbitrary text or byte payload from incoming AMQP 1.0 / 0.9.1 
connections which are not yet in [Ditto Protocol](protocol-overview.html) messages in such and can also map outgoing 
Ditto Protocol messages (e.g. responses or events) back to some arbitrary text or byte payload.
 

## Bugfixes

### Failover and stability fixes in connectivity service

The former AMQP bridge did loose the connection to AMQP 1.0 endpoints. This is now much more stable from which also the 
new AMQP 0.9.1 connections benefit.

### [Docker compose config was wrong](https://github.com/eclipse/ditto/issues/140)

The entrypoint/command was pointing to a wrong `starter.jar`.


### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A0.3.0-M1+).


## Documentation

The documentation for the new/renamed `connectivity` service now has its own new section: 
[Connectivity API](connectivity-overview.html).
