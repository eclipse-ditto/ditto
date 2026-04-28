---
title: Architecture overview
keywords: architecture, overview, component, services
tags: [architecture]
permalink: architecture-overview.html
---

Eclipse Ditto consists of five microservices that communicate over a Pekko cluster to provide digital twin management, access control, search, and connectivity to external systems.

{% include callout.html content="**TL;DR**: Ditto runs five services -- Policies, Things, Things-Search, Gateway, and Connectivity -- all in one Pekko cluster. They communicate via TCP using serializable signals, with MongoDB as the persistence layer and nginx as a reverse proxy." type="primary" %}

## Overview

The architecture of Eclipse Ditto is built around five cooperating microservices, each with a clear responsibility. This page describes the top-level component view, how services are defined, and how they communicate.

## How it works

### Component view

{% include image.html file="pages/architecture/ditto-architecture-overview-2022.png" alt="Ditto services and context" caption="Ditto services in blue and context with nginx as reverse proxy and MongoDB" max-width=800 %}

The five services and their responsibilities:

| Service | Responsibilities |
|---------|-----------------|
| [Policies](architecture-services-policies.html) | Persist and enforce (authorize) [Policies](basic-policy.html) |
| [Things](architecture-services-things.html) | Persist and enforce (authorize) [Things](basic-thing.html) and [Features](basic-feature.html) |
| [Things-Search](architecture-services-things-search.html) | Track changes to `Things`, `Features`, and `Policies`; maintain an optimized search index; execute search queries |
| [Gateway](architecture-services-gateway.html) | Provide [HTTP](httpapi-overview.html) and [WebSocket](httpapi-protocol-bindings-websocket.html) APIs |
| [Connectivity](architecture-services-connectivity.html) | Persist [Connections](basic-connections.html); send and receive [Ditto Protocol](protocol-overview.html) messages to/from external message brokers |

All services run in the same [Pekko cluster](https://pekko.apache.org/docs/pekko/current/typed/cluster-concepts.html) and reach each other via TCP without requiring an additional message broker between them.

### Microservice definition

Each Ditto microservice follows three rules:

1. **Own data store**: Only the owning microservice can access and write to its data store.
2. **Signal-based API**: The service exposes its API as [signals](basic-signals.html) (commands, command responses, events).
3. **Signal-only access**: Other services interact with it exclusively through these signals.

### Communication

All microservices communicate asynchronously within the Ditto cluster using [Pekko remoting](https://pekko.apache.org/docs/pekko/current/general/remoting.html). Each service acts as both a TCP server (accepting connections) and a TCP client (sending messages to other services).

All messages sent between services are serializable. Ditto [signals](basic-signals.html) serialize from Java objects to JSON and deserialize back from JSON to Java objects.

## Further reading

* [Policies Service](architecture-services-policies.html)
* [Things Service](architecture-services-things.html)
* [Things-Search Service](architecture-services-things-search.html)
* [Gateway Service](architecture-services-gateway.html)
* [Connectivity Service](architecture-services-connectivity.html)
