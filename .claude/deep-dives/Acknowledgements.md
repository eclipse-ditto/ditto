# Acknowledgements Deep Dive

## Overview

This deep dive covers Ditto's Acknowledgements feature, which enables end-to-end reliable messaging in an inherently "at most once" distributed system.  
Understanding acknowledgements is crucial for implementing reliable message delivery patterns and ensuring that commands and events are properly received and processed.

## Goals

Understanding:
- The problem Ditto faced before introducing acknowledgements
- What acknowledgements are and which problems they solve
- Built-in acknowledgement types
- The meaning of ACK, NACK, and WACK
- How to request acknowledgements
- How to issue custom acknowledgements

## The Reliability Problem

### Pekko's "At Most Once" Semantics

**Core issue**: Pekko toolkit provides by default "at most once" message processing

**Pekko documentation quote**:
> "The only meaningful way for a sender to know whether an interaction was successful is by receiving a business-level acknowledgement message, which is not something Pekko could make up on its own (neither are we writing a 'do what I mean' framework nor would you want us to)."

**Article**: "Nobody Needs Reliable Messaging"
- URL: https://www.infoq.com/articles/no-reliable-messaging/
- Core thesis: Reliability must be implemented at business level

### Same Reasoning Applies to Ditto

**Problem scenarios**:
- During scaling events
- During deployments
- During failure scenarios
- Commands/messages might "get lost"
- Results in timeout or server error

### Why Not Provide "At Least Once" Automatically?

**Challenges with automatic retry**:

**Decision complexity**:
- Which commands/messages should be automatically retried if failed?
- How often to retry?
- When to give up?

**Implementation implications**:
- Potentially apply "command sourcing"
- Persist each incoming message to real persistence before processing
- Significant performance and complexity overhead

**Conclusion**: Ditto cannot make these decisions - they're business logic

## Ditto Acknowledgements Solution

### Concept

**New feature**: Solves reliability problem with "business level" acknowledgements

**Documentation**: https://www.eclipse.dev/ditto/basic-acknowledgements.html

**E2E Big Picture**: E2E Ack big picture diagram

### What Gets Acknowledged

**Ditto Acknowledgements "ack" the receiving or processing of**:
- An event (e.g., `ThingUpdated`)
- A message command (e.g., `SendFeatureMessage`)

### User Capabilities

**The user of Ditto can**:

**Request ACKs**:
- Provide a list of Acknowledgement "labels"
- Specify which confirmations are needed
- Set timeout for ACK collection

**Provide ACKs**:
- Manually send an ACK via Ditto Protocol
- Configure a connection to automatically send ACK
- When message broker technically confirms processing

**Handle missing ACKs**:
- Get aware when not all requested ACKs were given
- Choose a retry strategy
- Decide if and how often to retry

**Note**: Quite a complex topic to explain/describe, but very powerful

## Use Cases for Acknowledgements

### Announcement

**Blog post**: https://www.eclipse.dev/ditto/2020-10-23-end-2-endacknowledgment.html

### Common Scenarios

#### Twin Persistence
**Ensure that command was persisted in "twin" (DB)**
- Use built-in `twin-persisted` ACK
- Command successfully stored
- Durable persistence confirmed

#### Strong Consistency for Search
**Ensure that command was processed by search**
- Use built-in `search-persisted` ACK
- Provides strong consistency
- Search index up-to-date

#### Fire & Forget
**Make "fire & forget" more explicit**
- Technically acknowledge message to broker immediately
- For Kafka, AMQP, MQTT brokers
- Don't wait for processing completion

#### Message Delivery Confirmation
**Ensure that a sent "message" was received**

**Options**:
- By the messaging layer (e.g., the broker)
- Actually by the device (by sending a custom ACK upon receiving)

#### Event Subscriber Confirmation
**Ensure that the event was received by special "subscriber"**
- Event emitted as result of command
- Received by specific subscriber
- Resend command if not received

#### Internal Reliability
**Provide Ditto internal "at least once" approach**
- Reliably publishing "Policy announcements"
- Until confirmed by receiver (e.g., HTTP connection)
- Internal guaranteed delivery

**... and many more use cases**

## Built-in Acknowledgements

### Three Built-in ACK Labels

Documentation: https://www.eclipse.dev/ditto/basic-acknowledgements.html#builtin-acknowledgement-labels

#### twin-persisted

**Purpose**: Confirm persistence of twin changes

**Automatically issued when**:
- An Event (e.g., `ThingUpdated`) was persisted
- As a result of a "twin" command (e.g., `UpdateThing`)
- And a response was requested

**Use for**: Ensuring durability of changes

#### live-response

**Purpose**: Confirm live message delivery

**Automatically issued when**:
- A correlated message Command Response received
- Example: `SendFeatureMessageResponse`
- Response from actual device/live channel

**Use for**: Device communication confirmation

#### search-persisted

**Purpose**: Confirm search index update

**Automatically issued when**:
- A "twin" modifying command has successfully updated search index
- Search is synchronized
- Strong consistency achieved

**Use for**: Ensuring searchability of changes

## Types of Acknowledgements

### ACK: Acknowledgement

**Meaning**: Positive acknowledgement

**When sent**:
- In the "happy path"
- To confirm message was received/processed successfully
- Normal success case

**Status code**: 2xx (success)

### NACK: Negative Acknowledgement

**Meaning**: Negative acknowledgement - something went wrong

**When issued**:

**Error during processing**:
- Ditto `RuntimeException` occurred
- Authentication error
- Authorization error
- Internal error

**Timeout**:
- Waiting for ACK times out
- Defined by "timeout" header
- No response within specified time

**Status code**: 4xx or 5xx (error)

### WACK: Weak Acknowledgement

**Documentation**:
- Concept: Weak acknowledgements (WACK)
- Blog post: https://www.eclipse.dev/ditto/2020-11-16-weakacknowledgements.html
- Ditto docs: https://www.eclipse.dev/ditto/basic-acknowledgements.html#weak-acknowledgements-wacks

**Problem solved**: Subscriber no longer at liberty to filter for signals

**Automatically issued by Ditto**:

**When issued**:
- Connection/WebSocket session declared it issues certain ACK label
- But message was "filtered out" before delivery

**Reasons for filtering**:

**Missing policy permissions**:
- Subscriber doesn't have permission to receive message
- Authorization filter applied

**Namespace/RQL filtering**:
- Message filtered by namespace filter
- RQL query didn't match
- Subscriber's filter criteria not met

**Identification**: Header `ditto-weak-ack` set to `true`

**Status code**: Usually 2xx but indicates "didn't process"

## Requesting Acknowledgements

### Setting the Header

**Documentation**: https://www.eclipse.dev/ditto/basic-acknowledgements.html#requesting-acks

**Header name**: `requested-acks`

**Value**: JSON Array of acknowledgement labels

**Can be set on**:
- Commands
- Message Commands

**Example**:
```json
{
  "requested-acks": ["twin-persisted", "search-persisted", "my-custom-ack"]
}
```

### Aggregated Response

**Documentation**: https://www.eclipse.dev/ditto/protocol-specification-acks.html#acknowledgements-aggregating

**When requesting more than 1 ACK**:

**Response format changes**:
- Combines status codes of all requested ACKs
- Includes potential payloads from each ACK
- Aggregated view of all acknowledgements

**Overall status**:
- Success if all ACKs successful
- Failure if any ACK failed
- Includes details per ACK

## Issuing Custom Acknowledgements

### Via Ditto Protocol

**Documentation**: https://www.eclipse.dev/ditto/basic-acknowledgements.html#issuing-acknowledgements

**Protocol specification**: https://www.eclipse.dev/ditto/protocol-specification-acks.html#acknowledgement

**Methods**:

**Ditto Protocol message**:
- Consumed via WebSocket
- Consumed by managed connection
- Manual ACK sending

**Example use case**: Application sends custom ACK after processing

### Via Connection Configuration

**Automatic issuance**:
- Ditto managed connection converts technical confirmation
- Message broker confirmation → custom ACK
- HTTP backend response → custom ACK
- Sent back automatically

**Example**: HTTP connection sends 200 OK → custom ACK issued

## Architecture Insights

### ACK Flow

**Request path**:
1. Client sends command with `requested-acks` header
2. Ditto processes command
3. Ditto waits for all requested ACKs
4. Built-in ACKs issued automatically
5. Custom ACKs issued by connections or manually
6. Ditto aggregates all ACKs
7. Aggregated response sent to client

### Timeout Handling

**Timeout header**: `timeout` (e.g., "5s", "100ms")

**Behavior**:
- Ditto waits maximum specified time
- If ACK not received: NACK issued with timeout reason
- Allows client to retry or handle accordingly

### Connection Role

**Connections as ACK issuers**:
- Receive messages from Ditto
- Publish to external systems
- Wait for technical confirmation
- Convert confirmation to Ditto ACK
- Send ACK back to Ditto

**Benefits**:
- Bridges Ditto and external systems
- Provides end-to-end confirmation
- No manual ACK implementation needed

## Best Practices

### Choosing ACK Labels

**Built-in ACKs**:
- Use when available
- Well-tested and reliable
- No configuration needed

**Custom ACKs**:
- Use for business-specific confirmations
- Descriptive label names
- Document meaning clearly

### Setting Timeouts

**Consider**:
- Network latency
- Processing time
- External system response time
- Retry strategy

**Recommendations**:
- Start with generous timeouts
- Adjust based on monitoring
- Different timeouts for different ACK types

### Error Handling

**Plan for failures**:
- Not all ACKs may be received
- Decide on retry strategy
- Log failures for analysis
- Monitor ACK success rates

### Testing

**Test scenarios**:
- All ACKs successful
- One ACK fails
- Timeout scenarios
- Custom ACK behavior
- Network failures

## Common Patterns

### Strong Consistency

**Pattern**: Request twin-persisted and search-persisted

**Use case**: Ensure data written and searchable

**Example**:
```json
{
  "requested-acks": ["twin-persisted", "search-persisted"]
}
```

### Device Confirmation

**Pattern**: Request live-response ACK

**Use case**: Ensure device received message

**Example**:
```json
{
  "requested-acks": ["live-response"]
}
```

### Multi-System Confirmation

**Pattern**: Request multiple custom ACKs

**Use case**: Ensure processing by multiple systems

**Example**:
```json
{
  "requested-acks": ["system-a-processed", "system-b-processed", "system-c-processed"]
}
```

### Fire and Forget

**Pattern**: Request immediate broker ACK

**Use case**: Don't wait for processing, just broker receipt

**Example**:
```json
{
  "requested-acks": ["broker-received"]
}
```

## Key Takeaways

### Solves Real Problem

**At most once → At least once**:
- Pekko/Ditto natively "at most once"
- Acknowledgements enable "at least once"
- Business-level reliability

### Flexible and Powerful

**Multiple ACK types**:
- Built-in for common cases
- Custom for specific needs
- ACK, NACK, WACK for different scenarios

**User control**:
- Request which ACKs needed
- Set timeouts
- Handle failures
- Implement retry logic

### Integration Friendly

**Connection support**:
- Automatic ACK issuance
- Technical → business ACK conversion
- No manual implementation needed

### Complex but Worthwhile

**Complexity**:
- Non-trivial concept
- Requires understanding
- Multiple moving parts

**Value**:
- Very powerful
- Enables reliable patterns
- Essential for production systems

## References

- Pekko message delivery reliability: https://doc.akka.io/docs/akka/current/general/message-deliveryreliability.html
- "Nobody Needs Reliable Messaging": https://www.infoq.com/articles/no-reliable-messaging/
- Ditto Acknowledgements concept: https://www.eclipse.dev/ditto/basic-acknowledgements.html
- Ditto ACK protocol: https://www.eclipse.dev/ditto/protocol-specification-acks.html
- Weak acknowledgements blog: https://www.eclipse.dev/ditto/2020-11-16-weakacknowledgements.html
- Webhook.site for testing: https://webhook.site
