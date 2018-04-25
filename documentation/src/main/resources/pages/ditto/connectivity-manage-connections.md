---
title: Manage connections
keywords: 
tags: [connectivity]
permalink: connectivity-manage-connections.html
---

In order to manage (CRUD) connections in Ditto [DevOps commands](installation-operating.html#devops-commands)
have to be used. There is no separate HTTP API for managing the connections, as this is not a task for a developer using 
the digital twin APIs but more for a "DevOps engineer" creating new connections to external systems.

All connection related piggyback commands use the following HTTP endpoint:

```
POST /devops/piggyback/connectivity
```

## CRUD commands

The following commands are available in order to manage connections:


* [create](#create-connection)
* [modify](#modify-connection)
* [retrieve](#retrieve-connection)
* [delete](#delete-connection)

### Create connection

Create a new connection by sending the following DevOps command:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:createConnection",
        "connection": {}
    }
}
```

The content of the connection configuration object is specified in the [Connections section](basic-connections.html).
For protocol specific examples consolidate the [AMQP-0.9.1 binding](connectivity-protocol-bindings-amqp091.html) and
the [AMQP-1.0 binding](connectivity-protocol-bindings-amqp10.html) respectively.

### Modify connection

Modify an existing connection by sending the following DevOps command:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:modifyConnection",
        "connection": {}
    }
}
```

The connection with the specified id has to be created before being able to modify it.


### Retrieve connection

The only parameter necessary for connection retrieval is the `connectionId`:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:retrieveConnection",
        "connectionId":"<connectionID>"
    }
}
```

### Delete connection

The only parameter necessary for connection deletion is the `connectionId`:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:deleteConnection",
        "connectionId":"<connectionID>"
    }
}
```

## Helper commands

The following commands are available to help creating connections and retrieving the status of existing connections:


* [test](#test-connection)
* [retrieve desired connection status](#retrieve-connection-status)
* [retrieve actual connection status + metrics](#retrieve-connection-metrics)

### Test connection

Run a test connection command before creating a persisted connection to validate the connection configuration. This
command checks the configuration and establishes a connection to the remote endpoint in order to validate the connection
credentials. The test connection is closed afterwards and will not be persisted. Analog to the [createConnection](#create-connection)
command, it requires a full connection configuration in the piggyback command.

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:testConnection",
        "connection": {}
    }
}
```

### Retrieve connection status

This command returns the connection status. It shows if a connection is currently enabled/disabled. The only parameter
necessary for retrieving the connection status is the `connectionId`:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:retrieveConnectionStatus",
        "connectionId":"<connectionID>"
    }
}
```

### Retrieve connection metrics

This command returns the connection activity. It shows if a connection is currently established. The only parameter
necessary for retrieving the connection metrics is the `connectionId`:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {},
    "piggybackCommand": {
        "type": "connectivity.commands:retrieveConnectionMetrics",
        "connectionId":"<connectionID>"
    }
}
```


## Payload mapping configuration

To enable a custom [payload mapping](/connectivity-mapping.html) for a specific connection, you have to configure a
mapping context in the connection configuration object. The following snippet shows an example `mappingContext`. This
configuration must be embedded in the connection configuration as shown in the [Connections](/basic-connections.html) section.

```json
"mappingContext": {
    "mappingEngine": "JavaScript",
    "options": {
      "incomingScript": "..",
      "outgoingScript": ".."
    }
  }
```
