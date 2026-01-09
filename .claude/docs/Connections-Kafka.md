# Kafka Connections Implementation Deep Dive

## Overview

This deep dive explains message processing in Ditto connections using Kafka as the example protocol.  
The architecture and flow apply generally to all connection types, with Kafka-specific details highlighted.

## Goals

Understanding the complete message processing lifecycle of a connection:
- Actor hierarchy
- Message consumption
- Payload mapping
- Signal publishing
- Acknowledgement handling

## Connection Actor Hierarchy

### ConnectionPersistenceActor

**Role**: Top-level actor managing connection lifecycle

**Responsibilities**:
- Persists creation, modification, and deletion of connections
- Uses event sourcing for connection state
- Manages connection client actor instances
- Routes messages to client actors via client actor router

**Pattern**: One persistence actor per connection

### Connection Client Actors (e.g., KafkaClientActor)

**Role**: Protocol-specific connection management

**Structure**: Each connection has **one or more** client actors

**Client actor manages**:
- **Consumer actors** (one per source)
- **Publisher actor** (one for all targets)

**Scaling**: Multiple clients enable horizontal scaling across Connectivity service instances

### Consumer Actors

**Role**: Consume messages from specific external addresses

**Pattern**: One consumer actor per source

**Reason**: Each source defines a specific address/topic to consume from

### Publisher Actor

**Role**: Publish outbound messages to external systems

**Pattern**: One publisher actor per connection (handles all targets)

**Publishes**:
- Live events
- Twin events
- Live messages
- Live commands
- Policy announcements
- Connection announcements

## Inbound Message Flow

### Complete Flow

1. **Connection Client consumes message**
   - Example: KafkaClient consumes Kafka record

2. **Optional: Inbound throttling**
   - Limit number of messages consumed per connection client
   - Not implemented for all connection types

3. **External Message transformation**
   - Protocol-specific message → ExternalMessage
   - ExternalMessage is generic intermediate type
   - Passed to InboundMappingSink

4. **Payload Mapping (InboundMappingSink)**
   - ExternalMessage → Signal(s) using configured mappers
   - **One ExternalMessage can result in many signals**
   - **Header mapping happens in parallel** with payload mapping
   - **Header mapping has precedence** over payload mapping results

5. **Mapping failure handling**
   - If transformation fails → DittoRuntimeException forwarded to mapping sink
   - Exception sent as response to source
   - If successful → ExternalMessage forwarded to mapping

6. **Optional: Signal throttling**
   - Limit number of signals consumed per connection client
   - Not implemented for all connection types

7. **Acknowledgement Aggregator started**
   - Awaits all requested acknowledgements for the signal

8. **Signal published to cluster**
   - Mapped signal distributed via Ditto Pub/Sub

9. **Message acknowledgement**
   - When all requested acknowledgements arrived
   - Message acknowledged to message broker
   - **Kafka-specific**: Message committed
   - **Important**: Only committed if all preceding messages already acknowledged

## Key Characteristics

### Ordering Guarantees

**Kafka commit behavior**:
- Messages committed in order
- Message N only committed if messages 0..N-1 already committed
- Ensures ordering preservation

### Acknowledgement

**Purpose**: Reliable message delivery

**Flow**:
- Acknowledgement aggregator tracks requested acks
- Waits for all acks before committing
- If acks not received, message not committed (will be re-delivered)

### Parallel Processing

**Header and Payload Mapping**:
- Execute in **parallel** (not sequential)
- Header mapping **has precedence**
- Cannot use results from one in the other

### Multi-Signal Generation

**Powerful feature**:
- One external message can generate multiple Ditto signals
- Useful for batch processing
- Example: Single Kafka message containing updates for multiple Things

## Throttling

### Inbound Message Throttling

**Level**: Message level (before mapping)
**Purpose**: Limit consumption rate from external broker
**Optional**: Not all connection types implement this

### Inbound Signal Throttling

**Level**: Signal level (after mapping)
**Purpose**: Limit rate of signals entering Ditto cluster
**Optional**: Not all connection types implement this

**Effect**: Backpressure to external systems

## Error Handling

### Mapping Failures

**When**: Payload mapping cannot transform to valid Ditto Protocol

**Behavior**:
1. DittoRuntimeException thrown
2. Forwarded to mapping sink
3. Sent as response back to source (if reply target configured)

**Result**: Message not acknowledged, will be re-delivered

### Acknowledgement Timeout

**When**: Requested acknowledgements don't arrive in time

**Behavior**: Depends on acknowledgement configuration
- May retry
- May fail and not acknowledge message

## Kafka-Specific Details

### Kafka Consumer

**Implementation**: Uses Pekko Streams Kafka connector

**Consumer groups**: Configurable per source

**Offset management**: Committed after acknowledgements

### Kafka Producer

**Implementation**: Uses Pekko Streams Kafka connector

**Producer settings**: Configurable per connection

**Delivery guarantees**: At-least-once by default

## Connection Client Router

**Purpose**: Distribute work across multiple client actors

**Pattern**: Pekko router

**Routing strategy**: Typically round-robin or consistent hashing

**Benefit**: Load distribution and fault tolerance

## Code References

- ConnectionPersistenceActor: Manages connection lifecycle
- KafkaClientActor: Kafka-specific client implementation
- InboundMappingSink: Payload mapping execution
- Consumer actors: Protocol-specific message consumption
- Publisher actor: Outbound message publishing

## Performance Considerations

### Scaling Strategies

**Horizontal scaling**:
- Increase number of connection client actors
- Add more Connectivity service instances
- Distribute load across cluster

**Throttling**:
- Protects Ditto cluster from overload
- Provides backpressure to external systems

### Resource Management

**Per-connection resources**:
- Connection clients
- Consumer actors (one per source)
- Publisher actor
- Pekko Streams

**Memory**: Primarily buffering and mapping

## Comparison to Other Protocols

**MQTT**:
- Similar actor hierarchy
- Different consumer implementation (MQTT client)
- Different acknowledgement semantics

**AMQP**:
- Similar flow
- Different protocol specifics
- Different delivery guarantees

**HTTP**:
- Outbound only (webhooks)
- No consumer actors
- Simpler flow

## References

- Connectivity service architecture
- Pekko Streams Kafka connector
- Ditto Protocol documentation
- InboundMappingSink implementation
- ConnectionPersistenceActor implementation
