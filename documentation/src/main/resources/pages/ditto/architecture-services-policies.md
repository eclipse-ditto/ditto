---
title: Policies service
keywords: architecture, service, policies
tags: [architecture]
permalink: architecture-services-policies.html
---

The "policies" service takes care of persisting [Policies](basic-policy.html).

## Model

The model of the policies service is defined around the entity `Policy`:


* [model](https://github.com/eclipse/ditto/tree/master/model/policies/src/main/java/org/eclipse/ditto/model/policies)

## Signals

Other services can communicate with the policies service via:


* [commands](https://github.com/eclipse/ditto/tree/master/signals/commands/policies/src/main/java/org/eclipse/ditto/signals/commands/policies):
  containing commands and command responses which are processed by this service
* [events](https://github.com/eclipse/ditto/tree/master/signals/events/policies/src/main/java/org/eclipse/ditto/signals/events/policies):
  containing events which are emitted when entities managed by this service were modified

## Persistence

The policies service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](http://localhost:4000/basic-signals.html#architectural-style) in order to persist changes 
and restore persisted entities.

