# Twin Events Deep Dive

## Overview

This deep dive provides comprehensive understanding of Twin Events in Eclipse Ditto - how they're published, distributed, and consumed across the system.

## Goals

- Understand who publishes events
- Understand who receives events
- Learn how authorization is enforced for event delivery
- Understand available event types

## Event Sourcing Foundation

### Core Pattern

Ditto uses **Event Sourcing** as its fundamental persistence pattern:
- All state changes recorded as immutable events
- Events stored in append-only journal
- Current state reconstructed by replaying events
- Enables complete audit trail and time travel

### Pekko Persistence

**Framework**: Pekko Persistence with MongoDB plugin

**Key components**:
- Pekko Cluster Sharding: Routes commands for same ID to same actor instance
- Pekko Persistence: Event sourcing implementation
- MongoDB Persistence Plugin: Stores events and snapshots

**Plugin**: [scullxbones/pekko-persistence-mongo](https://github.com/scullxbones/pekko-persistence-mongo)

## Event Types

### Thing Events

**Location**: `things/model/src/main/java/org/eclipse/ditto/things/model/signals/events`

**Event hierarchy**:
- `ThingEvent` - Base interface
- `ThingCreated` - Thing created
- `ThingModified` - Thing modified (full replacement)
- `ThingDeleted` - Thing deleted
- `AttributeCreated` - Single attribute created
- `AttributeModified` - Single attribute modified
- `AttributeDeleted` - Single attribute deleted
- `AttributesCreated` - All attributes created
- `AttributesModified` - All attributes modified
- `AttributesDeleted` - All attributes deleted
- `FeatureCreated` - Feature created
- `FeatureModified` - Feature modified
- `FeatureDeleted` - Feature deleted
- `FeaturePropertyCreated` - Feature property created
- `FeaturePropertyModified` - Feature property modified
- `FeaturePropertyDeleted` - Feature property deleted
- `FeaturePropertiesCreated` - All feature properties created
- `FeaturePropertiesModified` - All feature properties modified
- `FeaturePropertiesDeleted` - All feature properties deleted
- `DefinitionCreated` - Definition created
- `DefinitionModified` - Definition modified
- `DefinitionDeleted` - Definition deleted
- `PolicyIdCreated` - Policy ID set
- `PolicyIdModified` - Policy ID changed

**Package structure**: All events follow consistent naming pattern and inheritance

## MongoDB Persistence

### Collections

#### things_journal

**Purpose**: Event storage (event sourcing journal)

**Content**: Every event persisted here

**Structure**:
```json
{
  "pid": "thing:org.eclipse.ditto:thing-1",
  "sn": 3,
  "to": 1649531807903,
  "s2": {
    "__lifecycle": "ACTIVE",
    "_revision": 3,
    "_modified": "2022-04-10T11:38:26.1120295352",
    "_created": "2022-04-10T11:27.B9106JN0G3"
    // ... event payload
  }
}
```

**Fields**:
- `pid`: Persistence ID (thing ID)
- `sn`: Sequence number
- `to`: Timestamp
- `s2`: Event payload (serialized)

#### things_snaps

**Purpose**: Snapshot storage (performance optimization)

**Content**: Complete Thing state snapshots

**Structure**:
```json
{
  "_id": "ObjectId(...)",
  "pid": "thing:org.eclipse.ditto:thing-1",
  "sn": 3,
  "ts": 1649531807903,
  "s2": {
    "_lifecycle": "ACTIVE",
    "_revision": 3,
    "_modified": "2022-04-10T11:38:26.1120295352",
    // ... complete Thing JSON
    "thingId": "org.eclipse.ditto:thing-1",
    "policyId": "org.eclipse.ditto:thing-1",
    "namespace": "org.eclipse.ditto",
    "attributes": { ... },
    // ... full Thing state
  }
}
```

**Snapshot triggers**:
- **Time-based**: Every 15 minutes (default)
- **Event-based**: Every 500 changes (default)

**Purpose**: Avoid replaying thousands of events to reconstruct state

## Event Publishing

### ThingPersistenceActor

**Role**: Primary actor for Thing lifecycle

**Location**: `things/service/src/main/java/org/eclipse/ditto/things/service/persistence/actors/ThingPersistenceActor.java`

**Sharding**: One actor per Thing (shard key = Thing ID)

**Event flow**:
1. Command arrives
2. Command validated and converted to event
3. Event persisted to `things_journal`
4. Event applied to in-memory state
5. Event published to Ditto Pub/Sub (if subscribers exist)
6. Response sent to command sender

### Ditto Pub/Sub Mechanism

Events are published via Ditto's custom pub/sub system, which checks for subscribers before publishing to avoid unnecessary cluster traffic. See [Ditto-PubSub.md](Ditto-PubSub.md) for details on the pub/sub architecture.

## Event Distribution and Authorization

### Authorization During Publishing

**Process**:
1. Event published from persistence actor
2. Enforcement actor adds **read subjects** to event headers
3. Read subjects derived from Policy
4. Events routed to subscribers based on subjects

### Read Subjects in Headers

**Purpose**: Enable authorization-aware distribution

**Source**: Policy's READ permissions

**Example**:
- Policy grants `subject:device-123` READ on Thing
- Event headers include: `read-subjects: [subject:device-123]`
- Only subscribers with matching subject receive event

### Subscriber Authorization

**Current behavior**:
- Subscriber receives event **only if** they have READ permission for **whole payload**
- No partial events (yet)

**Planned feature**: Partial event support
- If subscriber has READ access to only part of Thing
- They will receive event with only accessible fields
- Other fields filtered out

## PolicyPersistenceActor

**Similar pattern** to ThingPersistenceActor:
- Uses journal and snapshots collections (`policies_journal`, `policies_snaps`)
- Uses MongoDB plugin for Pekko Persistence
- **Key difference**: Does NOT send events to cluster
  - Policy changes not broadcast as events
  - Policy enforcement inline in Concierge

## Event Consumer Examples

### WebSocket Subscriptions

**Flow**:
1. Client subscribes via WebSocket to Thing events
2. Gateway subscribes to Pub/Sub topics (read subjects)
3. Events delivered to Gateway
4. Gateway streams to WebSocket client

### Connection Targets

**Flow**:
1. Connection target configured with filters
2. Connectivity subscribes to matching events
3. Events delivered via Pub/Sub
4. Connectivity applies payload mapping
5. Events published to external broker

### Search Service

**Flow**:
1. Search subscribes to all Thing and Policy events
2. Events delivered via Pub/Sub
3. ThingUpdater actor processes event
4. Search index updated in MongoDB

## Configuration

**Location**: `things/service/src/main/resources/things.conf`

**Key settings**:
- Pekko Persistence configuration
- Snapshot intervals
- Journal settings
- MongoDB connection

## Key Insights

### Event vs Command

**Commands**:
- Intent to change state
- Can fail
- Require authorization
- Generate events

**Events**:
- State changes that occurred
- Immutable facts
- Already authorized
- Stored permanently

### Eventual Consistency

**Challenge**: Event distribution is eventually consistent
- Subscribers may receive events out of order (different Things)
- Per-Thing ordering guaranteed
- Network delays possible

### Performance Optimization

**Snapshots**:
- Avoid replaying thousands of events
- Balance: Too frequent = wasted storage, Too rare = slow recovery

**Pub/Sub subscriber checking**:
- Avoid publishing when no subscribers
- Significant reduction in cluster traffic

## References

- Ditto things events package: `org.eclipse.ditto.things.model.signals.events`
- ThingPersistenceActor implementation
- Pekko Persistence MongoDB Plugin: https://github.com/scullxbones/pekko-persistence-mongo
- Things service configuration: `things.conf`
