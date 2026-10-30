# Connections Deep Dive

## Overview

This deep dive covers the Eclipse Ditto Connectivity service, explaining how connections integrate Ditto with external messaging systems.  
Understanding connections is essential for IoT device integration and event routing.

## Goals

Understand the components and behavior of connections:
- Sources and targets
- Supported protocols
- Header and payload mapping
- Scaling and performance characteristics

## Connection Components

### Sources (Inbound Traffic)

**Purpose**: Define how messages flow INTO Ditto from external systems

**Characteristics**:
- Consume messages from external message brokers/endpoints
- Can specify authorization subjects for incoming messages
- Each source can have reply targets for request-response patterns

**Reply Targets**:
- Bound to a specific source
- Publish responses to inbound requests
- Enable bidirectional communication

### Targets (Outbound Traffic)

**Purpose**: Define how messages flow OUT OF Ditto to external systems

**Characteristics**:
- Publish events and messages to external destinations
- Can specify authorization subjects for outbound messages
- Support filtering which events to publish

## Supported Protocol Types

Ditto supports multiple IoT and messaging protocols:

1. **AMQP 0.9.1** (RabbitMQ)
2. **AMQP 1.0** (Apache Qpid, Azure Service Bus)
3. **MQTT 3.1.1** (Mosquitto, HiveMQ)
4. **MQTT 5** (Latest MQTT version)
5. **HTTP** (outbound only - webhooks)
6. **Kafka 2.x** (Apache Kafka)

## Header Mapping

Header mapping allows bridging between Ditto headers and protocol-specific headers.

### Inbound Header Mapping

**Purpose**: Map external protocol headers to Ditto headers

**Use case**: Forward custom headers through Ditto/Things processing pipeline

**Example**: Map MQTT user properties or HTTP headers to Ditto headers for downstream processing

### Outbound Header Mapping

**Purpose**: Map Ditto context to external protocol headers

**Available sources for mapping**:
- Ditto header values
- Entity ID (Thing ID) related to signal
- Feature ID related to signal
- Topic path of published signal
- Current timestamp
- Connection ID
- Authenticated subject ID

**Example**: Include Thing ID as custom HTTP header when publishing to webhook

## Payload Mapping

Payload mapping transforms between external formats and Ditto Protocol.

### Inbound Payload Mapping

**Purpose**: Transform external payload format to Ditto Protocol message

**Example use cases**:
- Convert proprietary device JSON to Ditto Protocol
- Parse binary protocols to Ditto messages
- Transform industry-standard formats (LwM2M, etc.)

### Outbound Payload Mapping

**Purpose**: Transform Ditto Protocol message to external payload format

**Example use cases**:
- Convert Ditto events to cloud-specific formats
- Generate webhook payloads in custom JSON structure
- Produce binary protocols for downstream systems

### Available Built-in Mappers

See: https://www.eclipse.dev/ditto/connectivity-mapping.html#builtin-mappers

**Built-in options**:
- Ditto Protocol mapper (pass-through)
- JavaScript mapper (custom transformation)
- Normalized mapper
- Connection status mapper

### Parallel Execution

⚠️ **Important**: Header mapping and payload mapping execute **in parallel**, not sequentially

**Implication**: You cannot use results from header mapping in payload mapping or vice versa

### Multi-Message Generation

**Multiple payload mappers** can generate **multiple messages** from a single consumed message

**Example**: One MQTT message could generate separate signals for multiple Things

## Connection Metrics

Metrics are exposed in the UI and API:

**Inbound metrics**:
- Messages consumed
- Messages mapped
- Messages enforced
- Mapping failures
- Enforcement failures

**Outbound metrics**:
- Messages published
- Messages acknowledged
- Publishing failures

## Persistence and Modifications

### Persistence Mechanism

- Uses Pekko persistence plugin
- Connection state persisted to MongoDB journal
- Event sourcing for all connection changes

### Modification Handling (Downtime)

When a connection is modified:

1. Modify command arrives at persistence actor
2. Connection configuration is updated
3. Modification is persisted (event sourced)
4. **Connection is stopped** (brief downtime)
5. Connection is reopened with modified settings

**Impact**: Brief interruption during reconfiguration

## Scaling and Performance

### Connection Clients

- Each connection has **one or more connection clients**
- Each client manages a completely separate physical connection
- Clients distribute across available Connectivity service instances

### Horizontal Scaling

**Inbound (Consuming)**:
- At most one connection client per Connectivity instance
- Consumption distributed across instances
- **Scale horizontally** by adding Connectivity service instances

**Outbound (Publishing)**:
- Signals always sent to same connection client (sticky routing)
- Publishing distributed across instances
- **Scale horizontally** by adding Connectivity service instances

### Throttling and Backpressure

**Inbound throttling**:
- Default limit: 100 messages/second per client
- Configurable per connection

**Outbound queue**:
- Each client has outbound message queue
- **Drops messages on overflow** (no blocking)
- Prevents cascading failures

### Connection Isolation

**Goal**: Minimize impact between connections

**Reality**:
- Connections share Connectivity service instances
- Some shared resources (CPU, memory)
- **Best effort isolation** but rest risk remains (e.g., CPU saturation)

## Connection Independence

**Question**: Do connections influence each other?

**Answer**: Designed for isolation, but considerations:
- Shared instance resources (CPU, network)
- One misbehaving connection could impact others
- Mitigation: Throttling, queue limits, resource management

## References

- Eclipse Ditto connectivity mapping documentation: https://www.eclipse.dev/ditto/connectivity-mapping.html
- Built-in mappers documentation: https://www.eclipse.dev/ditto/connectivity-mapping.html#builtin-mappers
