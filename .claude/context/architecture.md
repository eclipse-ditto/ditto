# Architecture Overview

Eclipse Ditto implements a microservices architecture using Apache Pekko (an Akka fork), event sourcing, and CQRS patterns.

## Core Microservices

Ditto consists of five main microservices that communicate via Pekko clustering:

### 1. Things Service (`/things/service/`)
- Manages Thing entities (digital twins) with attributes and features
- Event-sourced via `ThingPersistenceActor`
- Enforces policies inline via `ThingCommandEnforcement`
- Fetches policies from the Policies Service and creates policy enforcers used for enforcing policies with `PolicyEnforcerProvider`
- Handles both "twin" (persisted) and "live" (real-time) channels
- Performs WoT (Web of Things) model validation on Things and their features, including sent messages and their responses

### 2. Policies Service (`/policies/service/`)
- Manages authorization policies controlling access to Things
- Event-sourced via `PolicyPersistenceActor`
- Provides fine-grained access control at field level

### 3. Gateway Service (`/gateway/service/`)
- Stateless HTTP/WebSocket/SSE entry point
- Handles authentication (JWT, OAuth2)
- Routes commands to appropriate shard regions
- No persistence layer
- Considered as an "edge" service

### 4. Connectivity Service (`/connectivity/service/`)
- Manages connections to external systems (MQTT, AMQP, Kafka, HTTP)
- Bidirectional message flow and protocol adaptation
- Event-sourced via `ConnectionPersistenceActor`
- Considered as an "edge" service

### 5. Things-Search Service (`/thingsearch/service/`)
- Provides RQL-based search on Things
- Maintains separate read model synchronized via Thing events
- Implements CQRS query side
- Uses MongoDB for building up a wildcard index based search index for Things
- Translates RQL queries to MongoDB queries
- **MongoDB-level authorization enforcement**: Search queries are augmented at the MongoDB query level to ensure the user has `READ` permission on all fields referenced in the RQL filter. The search index stores policy information alongside Thing data, enabling database-level filtering of unauthorized data.
- Performs periodically execution of configured custom metrics, exposing the results via a Prometheus endpoint

## Inter-Service Communication

- **Pekko Cluster Sharding**: Entity actors (Things, Policies, Connections) are sharded across cluster nodes by entity ID
- **Distributed Pub-Sub**: Events published cluster-wide for search indexing and subscriptions
- **Direct Actor Messaging**: No HTTP between services - all communication via Pekko messages via Pekko remoting where every instance serves as both client and server
- **Shared MongoDB**: Separate databases per service for event journal and snapshots

## Key Architectural Patterns

### Event Sourcing
- All entity state changes persisted as events in MongoDB
- Base class: `AbstractPersistenceActor` in `/internal/utils/persistent-actors/`
- Persistence ID format: `{entity-type}:{entity-id}` (e.g., `thing:org.eclipse.ditto:my-thing`)
- Snapshots taken periodically to optimize recovery

### CQRS with Strategy Pattern
- Commands handled by strategy classes (e.g., `CreateThingStrategy`, `ModifyThingStrategy`)
- Located in `/things/service/persistence/actors/strategies/commands/`
- Events applied via event strategies in `/strategies/events/`
- Easy to extend with new command types

### Actor Supervision Hierarchy
- `DittoRootActor` → service-specific supervisor → entity actors
- Supervisor actors extend `AbstractPersistenceSupervisor`
- Restart strategies handle actor failures

### Protocol Adaptation
- Ditto Protocol: JSON-based message format in `/protocol/` module
- `DittoProtocolAdapter` converts domain signals to/from protocol
- Supports twin (persisted) and live (real-time) channels

### Policy Enforcement
- Inline enforcement before commands reach persistence actors
- `PolicyEnforcerProvider` caches policy enforcers
- `ThingCommandEnforcement` in Things service

## Technology Stack

- **Language**: Java 21
- **Build**: Maven 3.9.x
- **Actor System**: Apache Pekko (Akka fork)
- **Persistence**: MongoDB 7.0+ (event store and snapshots)
- **HTTP**: Pekko HTTP
- **Clustering**: Pekko Cluster with Sharding
- **Metrics**: Kamon with Prometheus reporter
- **Logging**: Logback (SLF4J)
- **JSON**: Jackson + custom Ditto JSON API
- **Testing**: JUnit 5, Mockito, Pekko TestKit
