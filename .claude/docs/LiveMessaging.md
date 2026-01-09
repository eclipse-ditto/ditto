# Live Messaging Deep Dive

## Overview

This deep dive explains Live Messaging in Ditto/Things - a feature for real-time, bidirectional messaging between devices and 
applications without persisting messages in the digital twin.

## Goals

Understand what Live messages are and how they're handled in the system:
- Message flow through services
- Authorization model
- Response correlation
- Sender and receiver roles

## What is Live Messaging?

**Live Messages** enable direct, real-time communication between entities (devices, applications) through Ditto:
- Messages are NOT persisted (unlike Twin commands)
- Direct peer-to-peer style communication
- Request-response pattern supported
- Useful for commands that shouldn't modify twin state

## Participants

### Who is the Sender?

**Anyone** can send live messages:
- Cloud applications
- Devices
- Other backend services
- WebSocket clients
- Connection sources

### Who is the Receiver?

**A "thing"** which can be:
- A physical device
- A cloud application
- Any entity subscribed to the Thing's live messages

## Message Flow

### Sending a Live Message

**Path**: Edge → Things → Edge(s)

1. **Entry point**: Message arrives at edge service
   - HTTP API in Gateway
   - Kafka/MQTT/AMQP in Connectivity
2. **Authorization**: Things enforces message policy
3. **Distribution**: Things forwards to edges with subscribers
4. **Delivery**: Multiple subscribers possible (fanout)

**Key optimization**: Ditto Pub/Sub used to limit internal network traffic
- Events published once per edge service (not once per subscriber)
- Edge services handle local distribution to subscribers

### Responding to a Live Message

**Response Path**: Edge → Edge (direct if same API) OR Edge → Things → Edge (if different API)

**Important characteristics**:
- **Only first response is forwarded** to original sender
- No `AcknowledgementAggregatorActor` available for live messages
- Initial sender known only in Things
- If different API used for response, must route through Things

### Who Can/Should Answer?

**Authorization for responses**:
- **Every subscriber is allowed to answer** (even if not allowed to send messages to the Thing!)
- **Only first response is forwarded** to original sender
- Subsequent responses are discarded

## Response Correlation

### Correlation Mechanism

**Responses correlated by Correlation ID** in Ditto headers

**Correlation tracking locations**:
- `AcknowledgementAggregatorActor` (for acknowledgements)
- `ResponseReceiverCache` (for responses)

**Process**:
1. Sender includes correlation ID in message
2. Ditto tracks pending correlations
3. Receiver includes same correlation ID in response
4. Ditto matches response to original sender using correlation ID

## Authorization

**Enforcement location**: Things service

**Permission model**: Based on message resource in policy

**Policy grants**:
- **READ**: Grants right to **receive** and **respond to** a message
- **WRITE**: Grants right to **send** a message

**Example policy**:
```json
{
  "entries": {
    "device": {
      "subjects": {
        "device:my-device": { "type": "device" }
      },
      "resources": {
        "message:/": {
          "grant": ["READ"],
          "revoke": []
        }
      }
    },
    "backend": {
      "subjects": {
        "app:my-backend": { "type": "application" }
      },
      "resources": {
        "message:/": {
          "grant": ["WRITE"],
          "revoke": []
        }
      }
    }
  }
}
```

In this example:
- Device can receive and respond to messages (READ)
- Backend can send messages (WRITE)

## Key Characteristics

### Live vs Twin Channel

**Live channel**:
- Not persisted
- Real-time delivery
- Multiple subscribers
- No event sourcing
- Timeout-based delivery

**Twin channel**:
- Persisted as events
- Eventually consistent
- Modifies Thing state
- Event sourced
- Guaranteed delivery

### Timeouts

**Message delivery**:
- Messages have timeout
- If no response within timeout, sender receives timeout error
- Responses after timeout go to dead-letter queue

### Multiple Subscribers

**Fanout behavior**:
- Single message can reach multiple subscribers
- All subscribers receive the message
- Any subscriber can respond
- Only first response forwarded

## Use Cases

**Common scenarios**:
- **Device commands**: Trigger immediate action without persisting to twin
- **Queries**: Ask device for runtime information
- **Diagnostics**: Request logs or status from device
- **Real-time control**: Start/stop processes on device
- **Bidirectional messaging**: Device-to-cloud and cloud-to-device

## References

- Ditto Live Messaging documentation
- Message resource authorization
- Response correlation implementations: `AcknowledgementAggregatorActor`, `ResponseReceiverCache`
