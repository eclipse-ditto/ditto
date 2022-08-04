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

{% include image.html file="pages/architecture/ditto-architecture-overview-2022.png" alt="Ditto services and context" caption="Ditto services in blue and context with nginx as reverse proxy and MongoDB" max-width=800 %}

The components have the following tasks:

* [Policies](architecture-services-policies.html): persistence + enforcement (authorization) of [Policies](basic-policy.html)
* [Things](architecture-services-things.html): persistence + enforcement (authorization) of [Things](basic-thing.html) 
  and [Features](basic-feature.html)
* [Things-Search](architecture-services-things-search.html): tracking changes to `Things`, `Features`, `Policies` and 
  updating an optimized search index + executes queries on this search index
* [Gateway](architecture-services-gateway.html): provides HTTP and WebSocket API
* [Connectivity](architecture-services-connectivity.html):
   * persistence of [Connections](basic-connections.html)
   * sends [Ditto Protocol](protocol-overview.html) messages to external message brokers and receives messages from them

All services run in the same [Akka cluster](https://doc.akka.io/docs/akka/current/typed/cluster-concepts.html) and can
reach each other via TCP without the need for an additional message broker in between.

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

All messages which are sent between Ditto microservices must in a way be serializable and deserializable.  
All Ditto [signals](basic-signals.html) can be serialized from Java objects to JSON representation and deserialized back 
from JSON to Java objects. 
