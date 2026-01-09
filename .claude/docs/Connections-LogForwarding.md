# Connection Log Forwarding Deep Dive

## Overview

Connection Log Forwarding enables customers with Silver+ support plans to forward their connection logs to their own log management systems (AWS CloudWatch, Datadog, New Relic).  
This operational feature provides visibility into connection behavior without accessing Ditto Explorer UI.

## Goals

Understanding:
- What connection log forwarding is
- Prerequisites for customers to enable it
- Technical implementation
- Configuration process
- Monitoring capabilities

## What is Connection Log Forwarding?

**Problem**: Connection logs only visible in Ditto UI

**Solution**: Forward logs to customer's log management system

**Supported destinations**:
- AWS CloudWatch
- Datadog
- New Relic
- (Other Fluent Bit output plugins)

## Architecture

### Two-Stage Forwarding

**Stage 1: Service → Forwarder (Sidecar)**
- Connectivity service → Fluent Bit forwarder (sidecar)
- Uses Fluency library
- Local communication within pod

**Stage 2: Forwarder → Aggregator → Customer**
- Fluent Bit forwarder → Fluent Bit aggregator
- Aggregator → Customer's log management
- Aggregator knows customer endpoint configuration

### Deployment Pattern

**Sidecar pattern**:
- Fluent Bit container runs alongside connectivity service in same pod
- Receives logs on local port
- Forwards to aggregator

**Aggregator**:
- Single Fluent Bit aggregator instance in Kubernetes cluster
- Central forwarding point
- Configured with customer endpoints

## Technology: Fluent Bit

[Fluent Bit](https://docs.fluentbit.io/manual/) is a lightweight log processor with [output plugins](https://docs.fluentbit.io/manual/pipeline/outputs) for AWS CloudWatch, Datadog, New Relic, and other destinations.

## Implementation

### Fluency Library

**Purpose**: Transfer logs from Ditto to Fluent Bit forwarder

**Java library**: Provides API for log shipping

**Integration points**:
- ConnectionLoggerRegistry
- ConnectionLoggerFactory

### Configuration

**Config classes**:
- `FluencyLoggerPublisherConfig` - Interface
- `DefaultFluencyLoggerPublisherConfig` - Implementation

**Settings**:
- Fluent Bit forwarder host/port
- Buffer settings
- Retry configuration
- Timeout values

### Code References

**Registry**: `ConnectionLoggerRegistry`
- Manages logger instances per connection
- Lifecycle management

**Factory**: `ConnectionLoggerFactory`
- Creates logger instances
- Configures Fluency client

## Configuration Options

### Log Levels

**Available levels**:
- `success` - Successful operations
- `failure` - Failed operations
- `info` - Informational messages
- `warning` - Warning messages
- `error` - Error messages
- `debug` - Debug information

**Typical setup**: `["success", "failure"]`

### Log Content

**logHeadersAndPayload**: Boolean

**true**: Include headers and message payload in logs
- More detailed debugging
- Higher data volume
- May contain sensitive data

**false**: Only metadata
- Less detailed
- Lower data volume
- More privacy-friendly

## Monitoring

### Grafana Dashboard

**Purpose**: Monitor log forwarding health

**Dashboard**: Fluent Bit aggregator Dashboard

**Metrics**:
- Messages forwarded
- Forwarding rate
- Error rate
- Buffer usage
- Connection status to customer endpoints

**Access**: Internal monitoring system

### Key Metrics

**Throughput**:
- Logs/second processed
- Bytes/second forwarded

**Reliability**:
- Success rate
- Failure rate
- Retry attempts

**Latency**:
- Processing delay
- Forwarding delay

## Deployment Architecture

### Connectivity Pod Structure

```
+-----------------------------------+
|  Connectivity Pod                 |
|                                   |
|  +--------------------------+     |
|  | Connectivity Service     |     |
|  | (Ditto)                  |     |
|  |                          |     |
|  | Logs via Fluency --------|--+  |
|  +--------------------------+  |  |
|                                |  |
|  +--------------------------+  |  |
|  | Fluent Bit Forwarder    |<-+  |
|  | (Sidecar)                |     |
|  |                          |     |
|  | Forwards to Aggregator   |     |
|  +--------------------------+     |
+-----------------------------------+
           |
           | Network
           v
+-----------------------------------+
|  Fluent Bit Aggregator            |
|  (Single instance in cluster)     |
|                                   |
|  Forwards to:                     |
|  - AWS CloudWatch                 |
|  - Datadog                        |
|  - New Relic                      |
+-----------------------------------+
```

## Log Format

**Structured logging**:
- JSON format
- Timestamp
- Log level
- Connection ID
- Message type
- Headers (if enabled)
- Payload (if enabled)
- Error details (if applicable)

## Use Cases

### Debugging Connection Issues

**Scenario**: Customer reports connection failures

**With log forwarding**:
1. Customer checks own CloudWatch
2. Sees detailed error messages
3. Can diagnose independently

**Without log forwarding**:
1. Customer contacts support
2. Support checks Ditto UI
3. Back-and-forth communication

### Operational Monitoring

**Scenario**: Monitor connection health

**Benefit**: Real-time visibility in customer's monitoring stack
- Integrate with existing alerts
- Custom dashboards
- Correlation with other logs

### Audit and Compliance

**Scenario**: Audit trail required

**Benefit**: Logs in customer's control
- Long-term retention
- Compliance requirements met

## Prerequisites for Customers

### Log Management System

**Required**: Customer must have compatible log management
- AWS CloudWatch account
- Datadog account
- New Relic account
- Or compatible system with Fluent Bit plugin

### Network Configuration

**Required**: Fluent Bit aggregator must reach customer endpoint
- Firewall rules
- Network connectivity
- Authentication credentials

## Security Considerations

### Sensitive Data

**Headers and payload** may contain:
- Authentication tokens
- Personal information
- Business data

**Recommendation**: Carefully consider `logHeadersAndPayload` setting

### Access Control

**Customer responsibility**: Secure access to forwarded logs
- IAM policies (AWS)
- Role-based access control
- Encryption at rest

### Network Security

**In-transit encryption**: HTTPS/TLS for forwarding

**Authentication**: API keys or IAM roles

## Troubleshooting

### Logs Not Appearing

**Check**:
1. Fluent Bit aggregator configured for customer
2. Network connectivity
3. Customer endpoint credentials valid
4. Grafana dashboard for errors

### High Latency

**Possible causes**:
- Network issues
- Customer endpoint slow
- High log volume
- Buffer overflow

### Missing Logs

**Possible causes**:
- Log level filtering
- Sampling enabled
- Buffer overflow (logs dropped)
- Aggregator issues

## Documentation References

- Fluent Bit: https://docs.fluentbit.io/manual/
- Output plugins: https://docs.fluentbit.io/manual/pipeline/outputs
- Solution overwrites: Log Publishing for connections
- Deployment overview: Deployment of Fluent Bit forwarder & aggregator
- Monitoring: Fluent Bit aggregator Dashboard
