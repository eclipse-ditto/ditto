---
title: Policies service
keywords: architecture, service, policies
tags: [architecture]
permalink: architecture-services-policies.html
---

The "policies" service takes care of persisting [Policies](basic-policy.html).

## Model

The model of the policies service is defined around the entity `Policy`:


* [Policy model](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model)

## Signals

Other services can communicate with the policies service via:

* [PolicyCommands](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model/signals/commands/PolicyCommand.java):
  implementing classes provide commands which are processed by this service
* [PolicyEvents](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model/signals/events/PolicyEvent.java):
  implementing classes represent events which are emitted when entities managed by this service were modified

## Persistence

The policies service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](basic-signals.html#architectural-style) in order to persist changes to  
and restore persisted [policies](basic-policy.html).

## Enforcement

The policies service enforces/authorizes [policy signals](#signals) by the "own" [policy](basic-policy.html).
