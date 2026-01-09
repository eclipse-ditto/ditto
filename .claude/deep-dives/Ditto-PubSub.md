# Ditto Pub/Sub Deep Dive

## Overview

This deep dive explains Ditto's custom publish/subscribe implementation, which enables scalable event distribution across the microservices cluster.  
Understanding Ditto Pub/Sub is critical for comprehending how events flow through the system.

## Goals

Gain deeper understanding of:
- Use cases for pub/sub in Ditto
- Why Ditto needs its own pub/sub system
- Architecture of Ditto pub/sub
- Key features and design decisions

## Why a Custom Pub/Sub System?

### Pekko Distributed Pub/Sub Limitations

**Problem**: Pekko's built-in Distributed Pub/Sub doesn't scale for Ditto's use case

**Ditto's Requirements**:
- High throughput (thousands of events per second)
- Large number of topics (potentially millions)
- Fine-grained subscriptions (per-Thing, per-Feature)
- Authorization-aware event distribution

**Pekko Pub/Sub limitations**:
- Not designed for millions of topics
- Performance degradation with many subscriptions
- Doesn't align with Ditto's authorization model

### Result: Custom Implementation

Ditto implements its own 2-level pub/sub system optimized for IoT workloads.

## Ditto Pub/Sub Architecture

### 2-Level Architecture

**Level 1: Topic Selection**
- Topics chosen based on **read subjects** and **shard IDs**
- Enables efficient routing based on authorization context
- Reduces unnecessary event fanout

**Level 2: Subscriber Distribution**
- Events delivered to relevant subscribers
- Grouping and filtering at subscriber level

### Topic Types

#### Read Subject Topics

**Purpose**: Route events based on authorization

**How it works**:
- Policies define subjects with READ permissions
- Events published to topics matching those read subjects
- Only subscribers with appropriate authorization receive events

**Example**:
- Policy grants `subject:device-123` READ permission
- Events for that Thing published to `subject:device-123` topic
- Subscribers with `subject:device-123` context receive events

#### Shard ID Topics

**Purpose**: Route events based on entity location in cluster

**How it works**:
- Things distributed across cluster using Pekko Cluster Sharding
- Events published to topic matching shard ID
- Enables efficient in-cluster event distribution

**Example**:
- Thing `namespace:thing-456` resides in shard #7
- Events published to shard #7 topic
- Subscribers interested in that shard receive events

## Key Implementation Details

### Subscriber Grouping

**Problem**: Avoid duplicate processing when multiple actors in same service instance subscribe

**Solution**: Group subscribers by service instance
- Only one delivery per instance
- Internal distribution within instance

### Event Order Preservation

**Critical requirement**: Events for the same entity must arrive in order

**Implementation**:
- Events routed consistently based on entity ID
- Ordering guaranteed per entity
- Different entities may interleave

**Example**:
- Thing A: Event 1 → Event 2 → Event 3 (order preserved)
- Thing B: Event 1 → Event 2 (order preserved)
- Delivery sequence might be: A1, B1, A2, A3, B2 (interleaved but per-entity order preserved)

### Message Path

**Publishing flow**:
1. Event generated in persistence actor (e.g., ThingPersistenceActor)
2. Published to local pub/sub actor
3. Local pub/sub determines topics (read subjects + shard ID)
4. Distributes to cluster via distributed data
5. Remote pub/sub actors receive event
6. Delivered to local subscribers

### Distributed Data Updates

**Mechanism**: Pekko Distributed Data (CRDT-based)

**Updates**:
- Subscription changes propagate via distributed data
- Eventually consistent subscription registry
- Tolerates network partitions

**Topic registry**:
- Which subscribers are interested in which topics
- Replicated across all cluster nodes
- Enables local subscription lookup without remote calls

## Use Cases in Ditto

### Event Sourcing

**Events from persistence**:
- Thing modified → ThingModified event
- Published via pub/sub to interested services

### Search Index Updates

**Search service subscription**:
- Subscribes to Thing and Policy events
- Receives events via pub/sub
- Triggers search index updates

### Connectivity Targets

**Connection subscription**:
- Connections subscribe to events matching target filters
- Receive events via pub/sub for outbound publishing

### WebSocket Subscriptions

**Gateway subscriptions**:
- WebSocket clients subscribe to Thing events
- Gateway receives events via pub/sub
- Streams to WebSocket client

### Live Messages

**Live channel**:
- Real-time messaging between devices
- Messages routed via pub/sub
- Subscription based on Thing ID and read permissions

## Performance Characteristics

### Scalability

**Horizontal scaling**:
- Add more nodes to cluster
- Pub/sub automatically distributes load
- No single bottleneck

**Topic scalability**:
- Designed for millions of topics
- Efficient lookup and routing
- Memory footprint optimized

### Latency

**Low latency**:
- In-memory routing
- Minimal serialization
- Local delivery when possible

## Code References

**Location**: `internal/utils/pubsub` package

**Main classes**:
- Pub/sub core implementation
- Subscriber management
- Topic routing logic
- Distributed data integration

## References

- Runtime View: Scalable event publishing (arc42 documentation)
- Pekko Cluster Sharding documentation
- Pekko Distributed Data documentation
- Ditto pub/sub implementation: `internal/utils/pubsub`
