---
title: Manage connections in connectivity
keywords: 
tags: [connectivity]
permalink: connectivity-manage-connections.html
---

In order to manage (CRUD) connections in Ditto [DevOps commands](installation-operating.html#connectivity-service-commands)
have to be used. There is no separate HTTP API for managing the connections as this is not a task for a developer using 
the digital twin APIs but more for a "devops engineer" creating new connections to external systems very seldom.

All connection related piggyback commands use the following HTTP endpoint:

```
POST /devops/piggyback/connectivity
```

## CRUD commands

The following commands are available in order to manage connections:


* [create](#create-connection)
* [retrieve](#retrieve-connection)
* [delete](#delete-connection)

A "modify" is currently not available, use delete + create in order to modify existing connections.

### Create connection

You can create a new connection by sending the following DevOps command:

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

The content of the connection configuration object is specified in the [Connections section](/basic-connections.html).
For protocol specific examples consolidate the [AMQP-0.9.1 binding](/connectivity-protocol-bindings-amqp091.html) and
the [AMQP-1.0 binding](/connectivity-protocol-bindings-amqp10.html) respectively.




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

The following commands are available in help creating connections + retrieve the status of existing connections:


* [test](#test-connection)
* [retrieve desired connection status](#retrieve-connection-status)
* [retrieve actual connection status + metrics](#retrieve-connection-metrics)

### Test connection

TODO describe command

### Retrieve connection status

TODO describe command

### Retrieve connection metrics

TODO describe command












