# General Architecture Deep Dive

## Overview

This deep dive provides a comprehensive introduction to Eclipse Ditto's software architecture and building blocks, covering microservices structure, 
inter-service communication, and the frameworks used.

## Key Topics Covered

### Architecture Overview

- High-level system architecture using microservices pattern
- Communication between services via Apache Pekko (formerly Akka) cluster
- Event-driven architecture with CQRS pattern

### Microservices

#### Concierge Service

**Role**: Authorization and routing hub

- **Authorization**: Responsible for enforcing policy-based access control
  - Policies are created in the Policies service
  - Policy attachment happens in the entity (e.g., Thing's `policyId` field)
  - Responsible entity services (e.g. "things" service for `ThingCommand`s) validates all commands against policies before applying
- **Caching**: Maintains caches of both Things and Policies for performance
- **Scaling**: Static scaling based on Pekko Cluster Sharding
  - Sharding distribution based on entity identifiers (Thing ID, Policy ID, Connection ID)
  - Commands route to specific shard based on entity ID
  - Fault/instance failure may cause data loss before recovery

#### Things-Search Service

**Role**: Provides search/query capabilities over Things

- Maintains separate search index for efficient querying
- Updates triggered by Thing internal events
- Separate MongoDB database for search indexes
- Background synchronization ensures consistency
- **Note**: Covered in detail in separate Search deep dive

#### Gateway Service

**Role**: External API entry point

- Exposes HTTP REST API and WebSocket endpoints
- Handles authentication (derives subjects from JWT tokens, credentials)
- Can be verified using `/whoami` endpoint

#### Connectivity Service

**Role**: Integration with external systems

- Manages connections to external message brokers
- **Sources**: Define inbound message flows from external systems
  - Each source can specify authorization subjects
- **Targets**: Define outbound message flows to external systems
  - Each target can specify authorization subjects

### Command and Event Flow

#### Create Thing Example

1. Command arrives at Gateway (authenticated)
2. Gateway forwards to Things persistence actor
3. Things enforcer actor checks authorization via `PolicyEnforcer`
4. **Command → Event translation**: CreateThing command becomes ThingCreated event
5. Event is persisted (event sourcing)
6. Event is transmitted to interested parties
7. Similar flow for ThingModified and other events

### Events & Event Filtering

- Events can be filtered using RQL (Resource Query Language)
- **Client-side filtering**: Raw events received by client, filter applied locally using RQL
- **Direct publishing**: If API Gateway is not present, events published directly via WebSockets
- Feature-level event filtering available

### Acknowledgements

- **Auto-acknowledge policy**: Special case applying to all connection target types
- Ensures reliable message delivery guarantees

### Payload Mapping

- Transforms external payloads to Ditto Protocol format and vice versa
- Any mapper can be used as long as output is Ditto-compatible
- Enables integration with various IoT protocols and formats

## Configuration

### Service Configuration

- **Format**: HOCON configuration files
- **Deployment**: Helm for configuration management
- **Static configuration only**: No dynamic runtime reconfiguration
- **Environment variables**: Can override configuration per deployment environment
- **Versioning**:
  - Semantic versions used for Ditto releases

## Frameworks & Technology Stack

### Primary Framework: Apache Pekko (formerly Akka)

- **Actor model**: Core programming paradigm
- **Cluster**: TCP/IP based clustering for distributed deployment
- **Sharding**: Distributes entities across cluster nodes
- **Persistence**: Event sourcing with MongoDB journal
- **Supervision**: Hierarchical error handling

### Observability

- **Tracing**: Via reporting traces to OTEL backend
- **Monitoring**: Relies on metrics and logging

## Key Architectural Patterns

1. **Event Sourcing**: All state changes persisted as events
2. **CQRS**: Separation of command and query responsibilities
3. **Actor Model**: Concurrent, distributed processing via Pekko actors
4. **Microservices**: Independently deployable, scalable services
5. **Policy-Based Authorization**: Fine-grained access control via inline policies
