# Extended Documentation

This directory contains **feature-specific documentation** and tutorials that are valuable for reference but not essential for general Claude Code context.

**⚠️ Note**: This directory is **excluded from default Claude Code context** via `.claudeignore`. To access these documents:
- Use the Read tool to explicitly read individual files when needed
- Example: `Read .claude/docs/RQL.md` when working on search queries

## What's in This Directory

### Query & Search

**[RQL.md](RQL.md)**
- Complete RQL (Resource Query Language) syntax reference
- Filter operators table
- Property paths and sorting
- Used in search, enrichment, and filtering

**[Search.md](Search.md)**
- Search service architecture
- Index synchronization (Thing/Policy updates, background sync)
- MongoDB search index structure
- Query processing flow

### Messaging & Communication

**[LiveMessaging.md](LiveMessaging.md)**
- Live channel for real-time messaging
- Message flow (not persisted in twin)
- Request-response patterns
- Authorization for live messages

### Connections (Protocol-Specific)

**[Connections-Kafka.md](Connections-Kafka.md)**
- Kafka connection implementation details
- Consumer/publisher actor hierarchy
- Inbound/outbound message flow
- Acknowledgement handling

**[Connections-PayloadMapping.md](Connections-PayloadMapping.md)**
- Payload mapper discovery and registration
- JavaScript mapper execution
- Custom mapper implementation
- Mapper lifecycle

**[Connections-LogForwarding.md](Connections-LogForwarding.md)**
- Connection log forwarding architecture
- Fluent Bit integration
- Sidecar pattern for log shipping
- Customer log destinations

### Specialized Features

**[Ditto-W3C-WoT.md](Ditto-W3C-WoT.md)**
- Web of Things (WoT) integration
- Thing Models vs Thing Descriptions
- Skeleton generation from WoT models
- TD generation from Ditto Things
- WoT validation

### Process & Governance

**[Ditto-OSS-Process.md](Ditto-OSS-Process.md)**
- Eclipse Foundation contribution process
- Release procedures
- Issue management
- Community engagement
- Versioning strategy

## When to Use These Docs

These documents are **not loaded by default** in Claude Code context. Reference them when:

- **Working on search functionality** → [RQL.md](RQL.md), [Search.md](Search.md)
- **Implementing live messaging** → [LiveMessaging.md](LiveMessaging.md)
- **Working on Kafka connections** → [Connections-Kafka.md](Connections-Kafka.md)
- **Creating custom payload mappers** → [Connections-PayloadMapping.md](Connections-PayloadMapping.md)
- **Setting up log forwarding** → [Connections-LogForwarding.md](Connections-LogForwarding.md)
- **Integrating WoT models** → [Ditto-W3C-WoT.md](Ditto-W3C-WoT.md)
- **Contributing to OSS** → [Ditto-OSS-Process.md](Ditto-OSS-Process.md)

## Core Architecture Docs

For architectural deep-dives and cross-cutting concerns, see **`.claude/deep-dives/`** which contains:
- Concierge-Removal.md (authorization architecture)
- TwinUpdates.md / TwinEvents.md (update/event flows)
- Ditto-PubSub.md (pub/sub system)
- Serialization.md (serialization patterns)
- Acknowledgements.md (reliability)
- And more...

See `.claude/deep-dives/README.md` for the complete list.
