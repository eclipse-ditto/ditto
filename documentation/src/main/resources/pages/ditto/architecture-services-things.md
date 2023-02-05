---
title: Things service
keywords: architecture, service, things
tags: [architecture]
permalink: architecture-services-things.html
---

The "things" service takes care of persisting [Things](basic-thing.html) and [Features](basic-feature.html).

## Model

The model of the things service is defined around the entities `Thing` and `Feature`:

* [Thing model](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model)

## Signals

Other services can communicate with the things service via:

* [ThingCommands](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model/signals/commands/ThingCommand.java):
  implementing classes provide commands which are processed by this service
* [ThingEvents](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model/signals/events/ThingEvent.java):
  implementing classes represent events which are emitted when entities managed by this service were modified

## Persistence

The things service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](basic-signals.html#architectural-style) in order to persist changes to 
and restore persisted [things](basic-thing.html).

## Enforcement

The things service enforces/authorizes [thing signals](#signals) by the via `policyId` referenced 
[policy](basic-policy.html).
