---
title: Managing Connections
keywords:
tags: [connectivity]
permalink: connectivity-manage-connections.html
---

You create, modify, retrieve, and delete connections using the Ditto HTTP API or DevOps piggyback commands.

{% include callout.html content="**TL;DR**: Use the HTTP API at `/api/2/connections` to manage connections. Authenticate as the `devops` user. You can also test connections with a dry run before persisting them." type="primary" %}

## Overview

Ditto provides two approaches to manage connections:

1. **HTTP API** (recommended) -- RESTful endpoints under `/api/2/connections`
2. **Piggyback commands** (legacy) -- DevOps commands sent to the connectivity service

Both approaches require [devops authentication](operating-devops.html#devops-user).

## Authorization

When you create a connection, you must specify an `authorizationContext` with authorization subjects
(for example, user IDs). These subjects determine the permissions applied when the connection
executes commands such as modifying a thing.

If you use basic auth from the [HTTP API](connectivity-protocol-bindings-http.html), prefix the
subject with `nginx:` (for example, `nginx:ditto`).
See [Basic Authentication](basic-auth.html#authorization-context-in-devops-commands) for details.

## Encrypting sensitive data

You can enable encryption of credentials and other sensitive fields before they are persisted.
See [Connections sensitive data encryption](operating-configuration.html#encrypting-sensitive-connection-data)
for configuration details.

## Managing connections via HTTP API

### Create a connection

Send an HTTP `POST` request:

```
POST /api/2/connections
```

```json
{
  "name": "",
  "connectionType": "",
  "connectionStatus": "",
  "uri": "",
  "sources": [],
  "targets": []
}
```

The connection configuration format is described in the [Connections](basic-connections.html) section.
For protocol-specific examples, see the relevant binding page:
* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
* [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* [HTTP 1.1](connectivity-protocol-bindings-http.html)
* [Kafka 2.x](connectivity-protocol-bindings-kafka2.html)

#### Dry run

Test a connection before persisting it:

```
POST /api/2/connections?dry-run=true
```

This validates the configuration and establishes a temporary connection to verify credentials.
The connection is closed afterward and not persisted.

### Modify a connection

Send an HTTP `PUT` request:

```
PUT /api/2/connections/{connectionId}
```

The connection must already exist before you can modify it.

### Retrieve a connection

```
GET /api/2/connections/{connectionId}
```

### Retrieve all connections

```
GET /api/2/connections
```

To retrieve only connection IDs:

```
GET /api/2/connections?ids-only=true
```

### Delete a connection

```
DELETE /api/2/connections/{connectionId}
```

## Connection commands

Send commands to a connection via:

```
POST /api/2/connections/{connectionId}/command
```

Send the command as `text/plain` payload:

| Command | Description |
|---------|-------------|
| `connectivity.commands:openConnection` | Opens the connection. Publishes a [ConnectionOpenedAnnouncement](protocol-specification-connections-announcement.html). |
| `connectivity.commands:closeConnection` | Closes the connection gracefully. Publishes a [ConnectionClosedAnnouncement](protocol-specification-connections-announcement.html). |
| `connectivity.commands:resetConnectionMetrics` | Resets all metrics to zero. |
| `connectivity.commands:enableConnectionLogs` | Enables logging for 24 hours. |
| `connectivity.commands:resetConnectionLogs` | Clears all stored connection logs. |

## Monitoring connections

### Connection status

```
GET /api/2/connections/{connectionId}/status
```

Shows whether a connection is enabled/disabled and whether it is successfully established.

### Connection metrics

```
GET /api/2/connections/{connectionId}/metrics
```

Returns counts of messages `consumed`, `filtered`, `mapped`, `published`, and `dropped` across
three time intervals: last minute, last hour, and last 24 hours.

### Connection logs

```
GET /api/2/connections/{connectionId}/logs
```

Returns success and failure log entries for messages processed by the connection. Logs have a
maximum capacity; older entries are dropped when the limit is reached.

{% include note.html content="When creating or opening a connection the logging is enabled per default. This allows
to log possible errors on connection establishing." %}

## Publishing connection logs to Fluentd

In addition to in-memory logs, you can publish connection logs to a
[Fluentd](https://www.fluentd.org) or [Fluent Bit](https://fluentbit.io) endpoint.

Configure this via environment variables in the [connectivity](architecture-services-connectivity.html) service:

| Variable | Description | Default |
|----------|-------------|---------|
| `CONNECTIVITY_LOGGER_PUBLISHER_ENABLED` | Enable log publishing | `false` |
| `CONNECTIVITY_LOGGER_PUBLISHER_LOG_TAG` | Log tag (uses `connection:<id>` if unset) | -- |
| `CONNECTIVITY_LOGGER_PUBLISHER_FLUENCY_HOST` | Fluentd/Fluent Bit hostname | `localhost` |
| `CONNECTIVITY_LOGGER_PUBLISHER_FLUENCY_PORT` | Fluentd/Fluent Bit port | `24224` |

Each log entry contains:

| Field | Values |
|-------|--------|
| `level` | `success`, `failure` |
| `category` | `connection`, `source`, `target`, `response` |
| `type` | `consumed`, `dispatched`, `filtered`, `mapped`, `dropped`, `enforced`, `published`, `acknowledged` |
| `connectionId` | ID of the connection which produced the log entry |
| `correlationId` | Correlation ID (if available) |
| `address` | Source/target address (if available) |
| `entityId` | Thing or policy ID |
| `entityType` | `"thing"` or `"policy"` |
| `instanceId` | ID of the connectivity instance (helpful when `clientCount > 1`) |
| `message` | The actual log message |

See [connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf)
at path `ditto.connectivity.monitoring.logger.publisher` for all options.

## Payload mapping configuration

To use a custom [payload mapping](connectivity-mapping.html) for a source or target, define a
`mappingDefinitions` entry in the connection and reference it by ID:

```json
{
  "mappingDefinitions": {
    "customJs": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "..",
        "outgoingScript": ".."
      }
    }
  },
  "sources": [{
    "addresses": "source",
    "payloadMapping": ["customJs"]
  }]
}
```

If no mapping is specified, the [Ditto mapper](connectivity-mapping.html#ditto-mapper) is used by default.

## Managing connections via piggyback commands

Although the HTTP API is recommended, you can still manage connections via DevOps
[piggyback commands](operating-devops.html#piggyback-commands).

All piggyback commands use this endpoint:

```
POST /devops/piggyback/connectivity
```

### Create connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:createConnection",
    "connection": {}
  }
}
```

### Modify connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:modifyConnection",
    "connection": {}
  }
}
```

### Retrieve connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Open connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:openConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Close connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:closeConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Delete connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:deleteConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Test connection (piggyback)

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:testConnection",
    "connection": {}
  }
}
```

### Retrieve all connection IDs (piggyback)

```json
{
  "targetActorSelection": "/user/connectivityRoot/connectionIdsRetrieval",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveAllConnectionIds"
  }
}
```

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [Installation and operation](installation-operating.html) -- DevOps configuration
