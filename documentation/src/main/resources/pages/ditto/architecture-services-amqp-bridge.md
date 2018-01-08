---
title: AMQP-Bridge service
keywords: architecture, service, amqp-bridge, amqp, hono
tags: [architecture]
permalink: architecture-services-amqp-bridge.html
---

In Ditto 0.1.0-M2 the AMQP-Bridge service was added with the goal to be able to establish connections to 
[Eclipse Hono](https://eclipse.org/hono/) as well as other [AMQP 1.0](protocol-bindings-amqp10.html) endpoints.

## Model

The model of the amqp-bridge service is defined around the entity `AmqpConnection`:
* [model](https://github.com/eclipse/ditto/tree/master/model/amqp-bridge/src/main/java/org/eclipse/ditto/model/amqpbridge)

## Signals

Other services can communicate with the amqp-bridge service via:

* [commands](https://github.com/eclipse/ditto/tree/master/signals/commands/amqp-bridge/src/main/java/org/eclipse/ditto/signals/commands/amqpbridge):
  containing commands and command responses which are processed by this service
* [events](https://github.com/eclipse/ditto/tree/master/signals/events/amqp-bridge/src/main/java/org/eclipse/ditto/signals/events/amqpbridge):
  containing events which are emitted when entities managed by this service were modified

## Persistence

The amqp-bridge service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](http://localhost:4000/basic-signals.html#architectural-style) in order to persist changes 
and restore persisted entities.

## Tasks

* create/remove connections (by persisting them)
* connect/disconnect to endpoints
* restore existing connections upon restart/failover
* translate incoming [Ditto Protocol](protocol-overview.html) messages to [commands](basic-signals-command.html)
  and translate [command responses](basic-signals-commandresponse.html) back to [Ditto Protocol](protocol-overview.html) response messages