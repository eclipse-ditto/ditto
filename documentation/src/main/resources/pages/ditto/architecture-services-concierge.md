---
title: Concierge service
keywords: architecture, service, concierge
tags: [architecture]
permalink: architecture-services-concierge.html
---

The "concierge" service is responsible for **orchestrating** the backing persistence services and for performing
**authorization** of [commands](basic-signals-command.html) and [command responses](basic-signals-commandresponse.html).

It acts as a gatekeeper and entry point for other services providing APIs:
* [gateway](architecture-services-gateway.html)
* [connectivity](architecture-services-connectivity.html)

Those services do not need to be aware of authorization and routing of messages in the cluster.

## Model

The concierge service has no model by its own, but uses the model of all the services it orchestrates.

## Signals

The concierge service has no signals by its own, but uses the signals of all the services it orchestrates.

## Persistence

The concierge service uses [Akka persistence](https://doc.akka.io/docs/akka/current/persistence.html?language=java) and 
with that [Event sourcing](basic-signals.html#architectural-style) in order to persist changes to 
and restore persisted batch commands.

## Tasks

* accept any [commands](basic-signals-command.html), [command responses](basic-signals-commandresponse.html)
  and [events](basic-signals-event.html) of all orchestrated services
* apply [authorization](basic-auth.html) to those, reply with authorization failures if the caller had no permission
  to execute a command 
* delegate authorized commands/responses/events to the orchestrated services where they are processed
