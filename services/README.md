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
* **amqp-bridge** - connects to an AMQP 1.0 endpoint (e.g. [Eclipse Hono](https://eclipse.org/hono/)) 
and consumes messages in Ditto Protocol from it
