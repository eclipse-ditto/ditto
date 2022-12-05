---
title: Manage connections via Piggyback commands
keywords:
tags: [connectivity]
permalink: connectivity-manage-connections-piggyback.html
---

The recommended way of connection management (CRUD) is by using the [HTTP API to manage connections](connectivity-manage-connections.html).
Although not the recommended way, it's still possible to manage connections in Ditto 
via DevOps [Piggyback commands](installation-operating.html#piggyback-commands) as well. 

All connection related piggyback commands use the following HTTP endpoint:

```
POST /devops/piggyback/connectivity
```

## Authorization

Please refer to [authorization when managing connections via HTTP API](connectivity-manage-connections.html#authorization).

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
    "is-group-topic": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:createConnection",
    "connection": {}
  }
}
```

The content of the connection configuration object is specified in the [Connections section](basic-connections.html).
For protocol specific examples, consult the specific connection type binding respectively:
* [AMQP-0.9.1 binding](connectivity-protocol-bindings-amqp091.html),
* [AMQP-1.0 binding](connectivity-protocol-bindings-amqp10.html),
* [MQTT-3.1.1 binding](connectivity-protocol-bindings-mqtt.html),
* [MQTT-5 binding](connectivity-protocol-bindings-mqtt5.html),
* [HTTP 1.1 binding](connectivity-protocol-bindings-http.html),
* [Apache Kafka 2.x binding](connectivity-protocol-bindings-kafka2.html)

### Modify connection

Modify an existing connection by sending the following DevOps command.

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

The connection with the specified ID needs to be created before one can modify it.

### Retrieve connection

The only parameter necessary for retrieving a connection is the `connectionId`.

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

### Retrieve connection tags

The only parameter necessary for retrieving a connection is the `connectionId`.

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false
  },
  "piggybackCommand": {
    "type": "connectivity.sudo.commands:sudoRetrieveConnectionTags",
    "connectionId": "{{connection.id}}"
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
    "is-group-topic": false,
    "ditto-sudo": true
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
    "is-group-topic": false,
    "ditto-sudo": true
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
    "is-group-topic": false,
    "ditto-sudo": true
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
    "is-group-topic": false,
    "ditto-sudo": true
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

### Retrieve ids of all connections by tag
This command returns the ids of all connections, filtered by a specific tag.

```json
{
  "targetActorSelection": "/user/connectivityRoot/connectionIdsRetrieval",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "connectivity.sudo.commands:sudoRetrieveConnectionIdsByTag",
    "tag": "someTagValue"
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
    "is-group-topic": false,
    "ditto-sudo": true     
  },
  "piggybackCommand": {
    "type": "connectivity.commands:retrieveConnectionStatus",
    "connectionId": "<connectionID>"
  }
}
```

### Retrieve connection metrics

For details about the response of this command, please refer to
[Retrieve connection logs using HTTP API](connectivity-manage-connections.html#retrieve-connection-metrics).

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false, 
    "is-group-topic": false,
    "ditto-sudo": true
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
    "is-group-topic": false,
    "ditto-sudo": true 
  },
  "piggybackCommand": {
    "type": "connectivity.commands:resetConnectionMetrics",
    "connectionId": "<connectionID>"
  }
}
```

### Enable connection logs

For details about the this command, please refer to
[Retrieve connection logs using HTTP API](connectivity-manage-connections.html#enable-connection-logs).

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true   
  },
  "piggybackCommand": {
    "type": "connectivity.commands:enableConnectionLogs",
    "connectionId": "<connectionID>"
  }
}
```

### Retrieve connection logs

For details about the response of this command, please refer to 
[Retrieve connection logs using HTTP API](connectivity-manage-connections.html#retrieve-connection-logs).

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "ditto-sudo": true 
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
    "is-group-topic": false,
    "ditto-sudo": true 
  },
  "piggybackCommand": {
    "type": "connectivity.commands:resetConnectionLogs",
    "connectionId": "<connectionID>"
  }
}
```


## Publishing connection logs

Please refer to [Payload mapping configuration](connectivity-manage-connections.html#publishing-connection-logs) in
HTTP API section about managing connections.

## Payload mapping configuration

Please refer to [Payload mapping configuration](connectivity-manage-connections.html#payload-mapping-configuration) in 
HTTP API section about managing connections.
