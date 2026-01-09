# Twin Updates Deep Dive

## Overview

This deep dive explains how Thing updates flow through Ditto, from initial request through authorization to final persistence.   
Understanding this flow is essential for debugging update issues and performance optimization.

## Goals

Deeper understanding of:
- Who sends twin updates
- How updates are authorized
- How ordering is guaranteed in parallel scenarios
- How updates are persisted
- Journal vs snapshot collections
- When snapshots are written

## Update Flow Overview

**High-level path**: Entry Point → Gateway/Connectivity → Things Service (Authorization + Persistence) → MongoDB

## Entry Points

### Who is Sending Updates?

**Multiple sources**:
- Cloud applications via HTTP API
- Devices via Connectivity (MQTT, Kafka, AMQP, HTTP)
- WebSocket clients
- Internal services

## Gateway Service - HTTP API

### Pekko HTTP Routes

**HTTP REST API**:
- **ThingsRoute.java**: Handles `/things` endpoints
  - GET, PUT, POST, PATCH, DELETE operations
  - Thing-level and sub-resource operations

**WebSocket API**:
- **WebSocketRoute.java**: Handles WebSocket connections
  - Bidirectional streaming
  - Commands and events over single connection

### HttpRequestActor

**Location**: `gateway/service/src/main/java/org/eclipse/ditto/gateway/service/endpoints/actors/HttpRequestActor.java`

Per-request actor that manages correlation between request and response with a 70-second timeout. Responses after timeout are sent to dead-letter queue for debugging.

## Connectivity Service - External Protocols

### Example: Kafka Connection Flow

**AtMostOnceConsumerStream.java**:
- Consumes messages from Kafka
- At-most-once delivery semantics

**InboundMappingSink.java**:
- Applies payload mapping
- Transforms external format to Ditto Protocol

**InboundDispatchingSink.java**:
- Routes mapped messages to appropriate service
- Determines target based on command type

**ConnectivityProxyActor.java**:
- Forwards commands to entity services (Things, Policies, etc.)
- Handles responses back to connection

## Things Service - Authorization

### Authorization in Entity Services

**Architecture change**: Authorization moved from separate Concierge service into entity services themselves

**Benefits**:
- Reduced network hops (Gateway → Things instead of Gateway → Concierge → Things)
- Simplified architecture
- Lower latency and higher throughput
- Better stability during rolling updates

### Actor Hierarchy for Authorization

**Three-level hierarchy** within Things service:

1. **ThingSupervisorActor** (extends AbstractPersistenceSupervisor)
   - **Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/persistence/actors/ThingSupervisorActor.java`
   - Started by shard region for each Thing ID
   - Entry point for all signals
   - Manages enforcer and persistence actors as children

2. **ThingEnforcerActor** (child with name "en")
   - **Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/enforcement/ThingEnforcerActor.java`
   - Extends AbstractPolicyLoadingEnforcerActor
   - Handles authorization logic
   - Loads and caches policy enforcers

3. **ThingPersistenceActor** (child with name "pa")
   - Handles persistence operations (covered below)

**Communication**: Via local method calls using `Patterns.ask()` with CompletionStage APIs

### Signal Transformers (Pre-Processing)

**Purpose**: Apply transformations on incoming signals before enforcement

**Extension mechanism**: DittoExtensionPoint - configured via `signal-transformers-provider`

**Execution**: Applied **before** pre-enforcement starts

**Example transformers**:
- **ModifyToCreateThingTransformer**: Converts ModifyThing to CreateThing if thing doesn't exist
- **ThingsPlaceholderSubstitution**: Resolves placeholders in commands

**Location example**: `things/service/src/main/java/org/eclipse/ditto/things/service/signaltransformation/`

### Pre-Enforcers

**Purpose**: Enforce rules **in addition to** policy-based authorization

**Extension mechanism**: DittoExtensionPoint - configured via `pre-enforcer-provider`

**Execution**: Applied **before** policy enforcement

**Example pre-enforcers**:
- **ThingCreationRestrictionPreEnforcer**: Enforces creation limits
- Blocked namespace checks
- Custom business rules and limits

**Location example**: `things/service/src/main/java/org/eclipse/ditto/things/service/enforcement/pre/`

**Note**: Pre-enforcers execute sequentially in configured order

### ThingCommandEnforcement

**Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/enforcement/ThingCommandEnforcement.java`

**Responsibilities**:
- Load policy enforcer for the Thing
- Check if authenticated subject has required permissions
- Enforce resource-level authorization
- Handle special cases (e.g., merge commands, live channel, smart channel)

**Policy loading**:
- Uses `PolicyEnforcerProvider` to load policy enforcer
- Caches policy enforcers for performance
- Retrieves Thing's policyId via SudoRetrieveThing when needed

### Merge Command Authorization

**Challenge**: Merge commands contain multiple changes at different resource levels

**Implementation**: For merge commands with partial permissions, every leaf path is validated individually

**Reference**: `things/service/src/main/java/org/eclipse/ditto/things/service/enforcement/ThingCommandEnforcement.java`

**Implication**: Users cannot modify fields they don't have access to, even in merge operations

## Things Service - Persistence

### Pekko Sharding for Routing

**Purpose**: Commands for same Thing ID always routed to same Things instance

**Benefit**:
- In-memory state caching
- Event sourcing efficiency
- Consistent ordering guarantees

### ThingPersistenceActor

**Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/persistence/actors/ThingPersistenceActor.java`

**Pattern**: One persistence actor per Thing

**Shard key**: Thing ID

**Message queue**:
- Commands received during actor startup are queued
- Processed sequentially after initialization
- Guarantees ordering per Thing

### Update Persistence Flow

1. **Command validation**: Validate payload during command construction
2. **State check**: Verify command valid for current state
3. **Event generation**: Convert command to corresponding event
4. **Event persistence**: Write event to `things_journal` collection
5. **State update**: Apply event to in-memory Thing state
6. **Event publishing**: Publish event via Ditto Pub/Sub (if subscribers)
7. **Response**: Send response to command sender
8. **Snapshot check**: Check if snapshot should be written

## MongoDB Collections

Updates are persisted using event sourcing to MongoDB collections:
- **things_journal**: Stores all state-changing events
- **things_snaps**: Stores Thing state snapshots every 15 minutes or 500 changes (whichever comes first)

Actor recovery loads the latest snapshot and replays events since that snapshot. See [TwinEvents.md](TwinEvents.md) for detailed collection structures.

## Ordering Guarantees

### How Do We Guarantee Ordering in Parallel Scenarios?

**Per-Thing guarantee**: Pekko Cluster Sharding ensures:
- All commands for Thing ID X routed to same actor instance
- Actor processes commands sequentially
- Events persisted in order

**Cross-Thing behavior**:
- No ordering guarantee between different Things
- Things A and B can be updated in parallel
- Completely independent processing

**Consistency model**: Per-entity strong consistency, cross-entity eventual consistency

## Configuration

**Location**: `things/service/src/main/resources/things.conf`

**Key Pekko Persistence settings**:
- Journal plugin configuration
- Snapshot plugin configuration
- MongoDB connection settings
- Snapshot interval and threshold

## Command Validation

**When**: During command construction (early validation)

**What is validated**:
- JSON schema compliance
- Required fields present
- Data type correctness
- Size limits

**Benefit**: Fail fast before authorization and persistence

## Key Insights

### Timeout Management

**HttpRequestActor lifespan**: 70 seconds
- Long enough for most operations
- Prevents resource leaks from abandoned requests
- Dead-letter queue captures late responses for debugging

### Actor Lifecycle

**ThingPersistenceActor**:
- Created on-demand when first command arrives
- Remains active while receiving commands
- Passivates (shuts down) after idle timeout
- Reactivates automatically on next command

### Performance Considerations

**Caching**:
- Things service caches policy enforcers
- Reduces authorization latency
- Invalidation on policy updates
- PolicyEnforcerProvider handles cache management

**Sharding**:
- Distributes load across cluster
- Enables horizontal scaling
- Ensures consistent routing

## References

- Gateway service: ThingsRoute.java, WebSocketRoute.java, HttpRequestActor.java
- Connectivity service: InboundMappingSink.java, InboundDispatchingSink.java, ConnectivityProxyActor.java
- Things service:
  - ThingSupervisorActor.java (supervisor and orchestrator)
  - ThingEnforcerActor.java (authorization)
  - ThingCommandEnforcement.java (enforcement logic)
  - ThingPersistenceActor.java (persistence)
  - things.conf (configuration)
- Pre-enforcers: things/service/.../enforcement/pre/
- Signal transformers: things/service/.../signaltransformation/
- Extension points: AbstractPersistenceSupervisor.java, AbstractEnforcerActor.java
