---
title: Manage connections 
keywords:
tags: [connectivity]
permalink: connectivity-manage-connections.html
---

In order to manage (CRUD) connections in Ditto [DevOps commands](installation-operating.html#devops-commands)
have to be used. There is no separate HTTP API for managing the connections, as this is not a task for a developer 
using the digital twin APIs but more for a "DevOps engineer" creating new connections to external systems.

All connection related piggyback commands use the following HTTP endpoint:

```
POST /devops/piggyback/connectivity
```

## Authorization

When creating new connections, an `authorizationContext` is needed providing the _authorization subjects_ (e.g. IDs of
authorized users) to use in order to determine the permissions for when the connection executes commands (e.g. modifying
a Thing). If you want to use a user for the basic auth (from the [HTTP API](connectivity-protocol-bindings-http.html))
use the prefix `nginx:`, e.g. `nginx:ditto`.
See [Basic Authentication](basic-auth.html#authorization-context-in-devops-commands) for more information.

## CRUD commands

The following commands are available in order to manage connections:

* [create](#create-connection)
* [modify](#modify-connection)
* [retrieve](#retrieve-connection)
* [delete](#delete-connection)

### Create connection

Create a new connection by sending the following DevOps command.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:createConnection",
    "connection": {}
  }
}
```

The content of the connection configuration object is specified in the [Connections section](basic-connections.html).
For protocol specific examples, consult the [AMQP-0.9.1 binding](connectivity-protocol-bindings-amqp091.html),
[AMQP-1.0 binding](connectivity-protocol-bindings-amqp10.html) or
[MQTT-3.1.1 binding](connectivity-protocol-bindings-mqtt.html) respectively.

### Modify connection

Modify an existing connection by sending the following DevOps command.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:modifyConnection",
    "connection": {}
  }
}
```

The connection with the specified ID needs to be created before one can modify it.


### Retrieve connection

The only parameter necessary for retrieving a connection is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Open connection

The only parameter necessary for opening a connection is the `connectionId`. When opening a connection a 
[ConnectionOpenedAnnouncement](protocol-specification-connections-announcement.html) will be published.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:openConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Close connection

The only parameter necessary for closing a connection is the `connectionId`. When gracefully closing a connection a
[ConnectionClosedAnnouncement](protocol-specification-connections-announcement.html) will be published.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false, 
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:closeConnection",
    "connectionId": "<connectionID>"
  }
}
```

### Delete connection

The only parameter necessary for deleting a connection is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:deleteConnection",
    "connectionId": "<connectionID>"
  }
}
```

## Helper commands

The following commands are available to help to create connections and retrieving the status of existing connections:

* [test connection](#test-connection)
* [retrieve ids of all connections](#retrieve-ids-of-all-connections)
* [retrieve connection status](#retrieve-connection-status)
* [retrieve connection metrics](#retrieve-connection-metrics)
* [reset connection metrics](#reset-connection-metrics)
* [enable connection logs](#enable-connection-logs)
* [retrieve connection logs](#retrieve-connection-logs)
* [reset connection logs](#reset-connection-logs)

### Test connection

Run a test connection command before creating a persisted connection to validate the connection configuration. This
command checks the configuration and establishes a connection to the remote endpoint in order to validate the connection
credentials. The test connection is closed afterwards and will not be persisted. Analog to
the [createConnection](#create-connection)
command, it requires a full connection configuration in the piggyback command.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false, 
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:testConnection",
    "connection": {
      ...
      //Define connection configuration
    }
  }
}

```

### Retrieve ids of all connections

This command returns the ids of all connections.

```json
{
  "targetActorSelection": "/user/connectivityRoot/connectionIdsRetrieval/singleton",
  "headers": {
    "aggregate": false,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveAllConnectionIds"
  }
}
```

### Retrieve connection status

This command returns the connection status by showing if a connection is currently enabled/disabled and if it is
successfully established. The only parameter necessary for retrieving the connection status is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true     
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnectionStatus",
    "connectionId": "<connectionID>"
  }
}
```

### Retrieve connection metrics

This command returns the connection metrics showing how many messages have been successfully or failingly `consumed`,
`filtered`, `mapped`, `published`, `dropped`. The metrics are collected and returned in different time intervals:
* the last minute
* the last hour
* the last 24 hours

The only parameter necessary for retrieving the connection metrics is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false, 
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnectionMetrics",
    "connectionId": "<connectionID>"
  }
}
```

### Reset connection metrics

This command resets the connection metrics - all metrics are set to `0` again. The only parameter necessary for
retrieving the connection metrics is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true 
  },
  "piggybackCommand": {
    "type": "connectivity.commands:resetConnectionMetrics",
    "connectionId": "<connectionID>"
  }
}
```

### Enable connection logs

Enables the connection logging feature of a connection for 24 hours. As soon as connection logging is enabled, you will
be able to [retrieve connection logs](#retrieve-connection-logs). The logs will contain a fixed amount of success and
failure logs for each source and target of your connection and correlate with the metrics of the connection. This will
allow you more insight in what goes well, and more importantly, what goes wrong.

The default duration and the maximum amount of logs stored for one connection can be configured in Ditto's connectivity
service configuration.

{% include note.html content="When creating or opening an connection the logging is enabled per default. This allows 
to log possible errors on connection establishing." %}

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true     
  },
  "piggybackCommand": {
    "type": "connectivity.commands:enableConnectionLogs",
    "connectionId": "<connectionID>"
  }
}
```

### Retrieve connection logs

This command will return a list of success and failure log entries containing information on messages processed by the
connection. The logs have a maximum amount of entries that they can hold. If the connection produces more log entries,
the older entries will be dropped. So keep in mind that you might miss some of the log entries.

The response will also provide information on how long the logging feature will still be enabled. Since the timer will
always be reset when retrieving the logs, the timestamp will always be 24 hours from now.

The default duration and the maximum amount of logs stored for one connection can be configured in Ditto's connectivity
service configuration.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true 
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnectionLogs",
    "connectionId": "<connectionID>"
  }
}
```

### Reset connection logs

Clears all currently stored connection logs.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": true 
  },
  "piggybackCommand": {
    "type": "connectivity.commands:resetConnectionLogs",
    "connectionId": "<connectionID>"
  }
}
```


## Publishing connection logs

In addition to [enable collecting in-memory connection logs](#enable-connection-logs), connection logs may also be 
published to a [Fluentd](https://www.fluentd.org) or [Fluent Bit](https://fluentbit.io) endpoint from where they can be 
forwarded into a logging backend of your choice.

This publishing of connection logs is not configured dynamically via a [piggyback helper command](#helper-commands), 
instead this must be enabled via [configuration](installation-operating.html#ditto-configuration) in the 
`connectivity.conf` by setting environment variables or overwriting the configuration via system properties.

For example, set the following environment variables in the [connectivity](architecture-services-connectivity.html) 
service:
* `CONNECTIVITY_LOGGER_PUBLISHER_ENABLED` - set to `true` in order to enable connection log publishing, default: `false`
* `CONNECTIVITY_LOGGER_PUBLISHER_LOG_TAG` - to specify a log tag to use for the published log entries - 
   if this is not set, the default `connection:<connection-id>` will be used and the specific connection-id will be injected
* `CONNECTIVITY_LOGGER_PUBLISHER_FLUENCY_HOST` - the hostname of the Fluentd or Fluent Bit endpoint, default: `"localhost"`
* `CONNECTIVITY_LOGGER_PUBLISHER_FLUENCY_PORT` - the port of the Fluentd or Fluent Bit endpoint, default `24224`

The contained fields in a single log entry are the following:
* `connectionId`:  ID of the connection which produced the log entry
* `level`:         one of: `"success"|"failure"`
* `category`:      one of: `"connection"|"source"|"target"|"response"`
* `type`:          one of: `"consumed"|"dispatched"|"filtered"|"mapped"|"dropped"|"enforced"|"published"|"acknowledged"`
* `correlationId`: correlationId of the command/event, if available
* `address`:       address of the Source/Target (e.g. MQTT topic, HTTP path), if available
* `entityType` :   one of: `"thing"|"policy"`
* `entityId`:      ID of the entity for which e.g. an event/message was processed (e.g. the Thing ID)
* `instanceId`:    ID of the connectivity instance which processed the command/event, helpful if clientCount > 1 was configured
* `message`:       the actual log message

Please inspect the other available configuration options in 
[connectivity.conf](https://github.com/eclipse/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf) 
at path `ditto.connectivity.monitoring.logger.publisher` to learn about other configuration possibilities.


## Payload mapping configuration

To enable a custom [payload mapping](connectivity-mapping.html) for a specific source or target of a connection, you
have to configure a payload mapping definition in the connection configuration object. The following snippet shows an
example `mappingDefinitions`. This configuration must be embedded in the connection configuration as shown in the
[Connections](basic-connections.html) section. These payload mapping definitions are then referenced by its ID
(the key of the JSON object) in the sources and targets of the connection using the field `payloadMapping`. If no
payload mapping or definition is provided, the [Ditto message mapping](connectivity-mapping.html#ditto-mapper)
is used as the default.

```json
{
  ...
  "mappingDefinitions": {
    "customJs": {
      // (1)
      "mappingEngine": "JavaScript",
      // (2)
      "options": {
        // (3)
        "incomingScript": "..",
        "outgoingScript": ".."
      }
    }
  },
  "sources": [
    {
      "addresses": "source",
      "payloadMapping": [
        "customJs"
      ]
    }
  ]
  ...
}
```

- (1) This ID can be used in sources and targets of the connection to reference this payload mapping definition.
- (2) The `mappingEngine` defines the underlying `MessageMapper` implementation.
- (3) The `options` are used to configure the mapper instance to your needs.
