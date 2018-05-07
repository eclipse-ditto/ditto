## Eclipse Ditto :: Services

This module contains the service
* **models** - specific service-internal model classes
* **utils** - common functionality shared across all services

and the actual services:
* **policies** - persistence of `Policies`
* **things** - persistence of `Things` and `Features`
* **thingsearch** - tracking changes to `Things`, `Features`, `Policies` and updating an optimized
search index + executes queries on this search index
* **gateway** - provides HTTP and WebSocket API and orchestrates the backing persistence services 
* **connectivity** - connects to AMQP 1.0 (e.g. [Eclipse Hono](https://eclipse.org/hono/)) and AMQP 0.9.1 endpoints 
and consumes messages in Ditto Protocol from it, optionally converts other formats to Ditto Protocol
