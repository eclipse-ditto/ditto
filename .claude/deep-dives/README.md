# Deep Dives - Architecture & Core Concepts

This directory contains **core architectural deep-dives** that explain Ditto's fundamental patterns, cross-cutting concerns, and design decisions. These documents provide context that's difficult to extract from code alone.

## Directory Structure

- **`.claude/deep-dives/`** (this directory): Core architecture and cross-service patterns - **Loaded by default**
- **`.claude/docs/`**: Feature-specific documentation and tutorials - **Excluded by default** (see `.claudeignore`)
  - To access these docs, explicitly read them with the Read tool when needed

## What's in This Directory

### Architecture & Major Changes

**[Concierge-Removal.md](Concierge-Removal.md)** - CRITICAL
- Major architectural change: authorization moved from Concierge to entity services
- Explains current vs. outdated patterns in codebase
- DittoExtensionPoint mechanism (pre-enforcers, signal transformers)
- **Read this first** to understand enforcement architecture

**[GeneralArchitecture.md](GeneralArchitecture.md)**
- High-level service overview
- Microservices and their responsibilities
- Good starting point for new developers

### End-to-End Flows

**[TwinUpdates.md](TwinUpdates.md)**
- Complete flow: Entry → Gateway → Things (Authorization + Persistence) → MongoDB
- Actor hierarchy for authorization and persistence
- Signal transformers and pre-enforcers
- Ordering guarantees and consistency model

**[TwinEvents.md](TwinEvents.md)**
- Event sourcing and persistence foundation
- Event publishing and distribution flow
- MongoDB collections (things_journal, things_snaps)
- Authorization during publishing

### Cross-Cutting Concerns

**[Ditto-PubSub.md](Ditto-PubSub.md)**
- Why Ditto uses custom pub/sub (not standard Pekko)
- Subscriber tracking and optimization
- Topic-based routing with authorization

**[Serialization.md](Serialization.md)**
- Pekko serialization strategies
- ClassIndex usage for mapper registration
- Versioning and migration patterns
- **Important** for avoiding serialization mistakes

**[Authentication-Authorization.md](Authentication-Authorization.md)**
- Policy enforcement locations in current architecture
- Common authorization issues and debugging
- ThingCommandEnforcement, PolicyEnforcer patterns

### Complex Features

**[Acknowledgements.md](Acknowledgements.md)**
- Business-level reliability in "at most once" system
- Built-in acknowledgements (twin-persisted, live-response, search-persisted)
- Custom acknowledgement implementation
- ACK/NACK/WACK semantics

**[Enrichment.md](Enrichment.md)**
- Why enrichment happens at edges (not in persistence actors)
- Timing and performance implications
- Filtering with enriched fields

**[Placeholder.md](Placeholder.md)**
- Comprehensive placeholder reference
- Categories: thing, feature, header, topic, entity, request, connection
- Pipeline functions for transformation
- Used extensively in connections and policies

### Infrastructure

**[Connections.md](Connections.md)**
- General connection architecture
- Sources, targets, reply targets
- Header and payload mapping (parallel execution)
- Supported protocols (MQTT, AMQP, Kafka, HTTP)

## When to Use These Deep Dives

### Starting a New Task?
1. **Working on authorization/enforcement?** → Read [Concierge-Removal.md](Concierge-Removal.md) and [TwinUpdates.md](TwinUpdates.md)
2. **Working on events/pub-sub?** → Read [TwinEvents.md](TwinEvents.md) and [Ditto-PubSub.md](Ditto-PubSub.md)
3. **Working on connections?** → Read [Connections.md](Connections.md) and [Placeholder.md](Placeholder.md)
4. **Adding a new signal type?** → Read [Serialization.md](Serialization.md)
5. **Implementing reliability?** → Read [Acknowledgements.md](Acknowledgements.md)

### Debugging an Issue?
- **403 Forbidden errors** → [Authentication-Authorization.md](Authentication-Authorization.md)
- **Events not received** → [TwinEvents.md](TwinEvents.md) and [Ditto-PubSub.md](Ditto-PubSub.md)
- **Connection mapping issues** → [Placeholder.md](Placeholder.md)
- **Serialization errors** → [Serialization.md](Serialization.md)

### Learning the System?
Start with this order:
1. [GeneralArchitecture.md](GeneralArchitecture.md) - Overview
2. [Concierge-Removal.md](Concierge-Removal.md) - Current architecture
3. [TwinUpdates.md](TwinUpdates.md) - Update flow
4. [TwinEvents.md](TwinEvents.md) - Event flow
5. Dive into specific features as needed

## Feature-Specific Documentation

For documentation on specific features, see **`.claude/docs/`**:
- **RQL.md**: Query language syntax reference
- **Search.md**: Search service architecture
- **LiveMessaging.md**: Live channel messaging
- **Ditto-W3C-WoT.md**: Web of Things integration
- **Connections-Kafka.md**: Kafka-specific implementation
- **Connections-PayloadMapping.md**: Payload mapper details
- **Connections-LogForwarding.md**: Log forwarding to external systems
- **Ditto-OSS-Process.md**: Open source contribution process

## Maintaining These Docs

- **Keep concise**: Remove duplication, reference other docs instead of repeating
- **Cross-reference**: Link to related deep-dives rather than duplicating content
- **Update on major changes**: When architecture changes, update or create a new deep dive
- **Prune outdated content**: Remove sections that no longer reflect current code
