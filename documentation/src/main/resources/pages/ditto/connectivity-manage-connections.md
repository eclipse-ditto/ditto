---
title: Manage connections in connectivity
keywords: 
tags: [connectivity]
permalink: connectivity-manage-connections.html
---

In order to manage (CRUD) connections in Ditto [DevOps commands](installation-operating.html#connectivity-service-commands)
have to be used. There is no separate HTTP API for managing the connections as this is not a task for a developer using 
the digital twin APIs but more for a "devops engineer" creating new connections to external systems very seldom.

TODO move the example from the DevOps commands page to here - only describe the concept of piggyback commands there.

## CRUD commands

The following commands are available in order to manage connections:
* [create](#create-connection)
* [retrieve](#retrieve-connection)
* [delete](#delete-connection)

A "modify" is currently not available, use delete + create in order to modify existing connections.

### Create connection

TODO describe command

### Retrieve connection

TODO describe command

### Delete connection

TODO describe command

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
