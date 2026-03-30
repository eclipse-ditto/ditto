---
title: Connectivity service
keywords: architecture, service, connectivity, amqp, hono
tags: [architecture, connectivity]
permalink: architecture-services-connectivity.html
---

The Connectivity service establishes and manages client-side connections to external message brokers, translating between [Ditto Protocol] messages and external transport protocols.

{% include callout.html content="**TL;DR**: The Connectivity service persists connection configurations, manages connections to external endpoints (AMQP, MQTT, Kafka, HTTP), translates messages to/from Ditto Protocol, and supports custom payload mapping." type="primary" %}

## Overview

You use the Connectivity service to integrate Ditto with external systems. It creates client-side connections to message brokers and other endpoints, sends and receives [Ditto Protocol] messages over those connections, and maps custom payloads when needed.

If you cannot transform your payload to a [Ditto Protocol Message] on the client side, the Connectivity service provides flexible and customizable [payload mapping].

## How it works

### Model

The service is built around the `Connection` entity:

* [Connection model](https://github.com/eclipse-ditto/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model)

### Signals

Other services communicate with the Connectivity service through two signal types:

* [ConnectivityCommands](https://github.com/eclipse-ditto/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model/signals/commands/ConnectivityCommand.java): Commands for managing connections (create, modify, open, close, delete)
* [ConnectivityEvents](https://github.com/eclipse-ditto/ditto/tree/master/connectivity/model/src/main/java/org/eclipse/ditto/connectivity/model/signals/events/ConnectivityEvent.java): Events emitted when connection entities change

### Persistence

The Connectivity service uses [Pekko persistence](https://pekko.apache.org/docs/pekko/current/persistence.html?language=java) with [event sourcing](basic-signals.html#overview) to persist and restore [connections](basic-connections.html).

### Enforcement

The Connectivity service does not enforce authorization through [policies](basic-policy.html) because connections do not reference a `policyId`. Access control for connection management is handled through the [DevOps user](operating-devops.html) or via the HTTP API with appropriate permissions.

### Tasks

The Connectivity service performs these core tasks:

* Create and remove connections by persisting their configuration
* Connect to and disconnect from external endpoints
* Restore existing connections on restart or failover
* Translate incoming [Ditto Protocol] messages to [commands](basic-signals-command.html) and translate [command responses](basic-signals-commandresponse.html) back to [Ditto Protocol] response messages
* Map custom message protocols to the [Ditto Protocol] via [Protocol Bindings]

## Further reading

* [Connections concept](basic-connections.html)
* [Protocol Bindings](protocol-bindings.html)
* [Ditto Protocol Overview](protocol-overview.html)
* [Architecture Overview](architecture-overview.html)


[Ditto Protocol]: protocol-overview.html
[Ditto Protocol Message]: protocol-specification-things-messages.html
[payload mapping]: connectivity-mapping.html
[Protocol Bindings]: protocol-bindings.html
