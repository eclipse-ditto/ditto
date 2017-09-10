## Eclipse Ditto :: Services

This module contains the service
* **models** - specific service-internal model classes
* **utils** - common functionality shared across all services
* **endpoints** - the HTTP API of the services

and the actual services:
* **policies** - persistence of `Policy`s
* **things** - persistence of `Thing`s and `Feature`s
* **thingsearch** - tracking changes to `Thing`s, `Feature`s and `Policy`s and updating an optimized
search index + executes queries on this search index
* **gateway** - provides HTTP and WebSocket API using the routes defined in the 'endpoints' module + 
orchestrates the backing persistence services 
