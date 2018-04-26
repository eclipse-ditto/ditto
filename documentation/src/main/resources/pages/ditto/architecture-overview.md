---
title: Architecture overview
keywords: architecture, overview, component, services
tags: [architecture]
permalink: architecture-overview.html
---

The architecture chapter describes the overall architecture of Eclipse Ditto and in detail which sub-components fulfill
which responsibilities.

## Top level component view

This overview shows the Ditto services (components), the externally provided and consumed API endpoints, 
the external dependencies (MongoDB and nginx) and the relations of the services to each other.

{% include image.html file="pages/architecture/context-overview.png" alt="Ditto services and context" caption="Ditto services in blue and context with nginx as reverse proxy and MongoDB" max-width=800 %}

The components have the following tasks:

* [Policies](architecture-services-policies.html): persistence of [Policies](basic-policy.html)
* [Things](architecture-services-things.html): persistence of [Things](basic-thing.html) and [Features](basic-feature.html)
* [Things-Search](architecture-services-things-search.html): tracking changes to `Things`, `Features`, `Policies` and 
  updating an optimized search index + executes queries on this search index
* [Gateway](architecture-services-gateway.html): provides HTTP and WebSocket API and orchestrates the backing persistence services
* [Connectivity](architecture-services-connectivity.html): connects to AMQP 1.0 endpoints (e.g. [Eclipse Hono](https://eclipse.org/hono/))
  or AMQP 0.9.1 endpoints (e.g. RabbitMQ) and consumes messages in [Ditto Protocol](protocol-overview.html) from it or transforms
  messages into Ditto Protocol

## Components

Ditto consists of multiple "microservices" as shown in the above component view.

A "microservice" in Ditto is defined as:

* has its own data store which only this microservice may access and write to
* has an API in form of [signals](basic-signals.html) (commands, command responses, events)
* can be accessed by other services only via the defined [signals](basic-signals.html)

## Communication

All microservices can communicate asynchronously in a Ditto cluster. Communication is done via 
[Akka remoting](https://doc.akka.io/docs/akka/current/general/remoting.html) which means that each service acts as server, 
providing a TCP endpoint, as well as client sending data to other services.

Therefore it is required that all Ditto microservices can reach each others TCP port `2551`.

Another consequence is that all messages which are send between Ditto microservices are in a way serializable and deserializable.
All Ditto [signals](basic-signals.html) can be serialized from Java objects to JSON representation and deserialized back 
from JSON to Java objects. 
