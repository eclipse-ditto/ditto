---
title: Connectivity service
keywords: architecture, service, connectivity, amqp, hono
tags: [architecture, connectivity]
permalink: architecture-services-connectivity.html
---

The "connectivity" service enables Ditto to establish and manage client-side connections to external service endpoints.
You can communicate with your connected things/twins over those connections via [Ditto Protocol] messages. The 
connectivity service supports various transport protocols, which are bound to the [Ditto Protocol] via specific 
[Protocol Bindings].
 
If you don't have the option to transform your payload to a [Ditto Protocol Message] on the client-side, the 
connectivity service offers a flexible and customizable [payload mapping] on top.

## Model

The model of the connectivity service is defined around the entity `Connection`:


* [model](https://github.com/eclipse/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model)

## Signals

Other services can communicate with the connectivity service via:

* [commands](https://github.com/eclipse/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model/signals/commands):
  containing commands and command responses which are processed by this service
* [events](https://github.com/eclipse/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model/signals/events):
  containing events which are emitted when entities managed by this service were modified

## Persistence

The connectivity service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](basic-signals.html#architectural-style) in order to persist changes to 
and restore persisted [connections](basic-connections.html).

## Tasks

* create/remove connections (by persisting them)
* connect/disconnect to endpoints
* restore existing connections upon restart/failover
* translate incoming [Ditto Protocol] messages to [commands](basic-signals-command.html)
  and translate [command responses](basic-signals-commandresponse.html) back to [Ditto Protocol] response messages
* map custom message protocols to the [Ditto Protocol]




  
[AMQP 1.0]: connectivity-protocol-bindings-amqp10.html
[Ditto Protocol]: protocol-overview.html
[Ditto Protocol Message]: protocol-specification-things-messages.html
[payload mapping]: protocol-specification-things-messages.html
[Protocol Bindings]: protocol-bindings.html
