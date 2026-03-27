---
title: Manage connections via Piggyback commands
keywords: connectivity, connections, piggyback, manage
tags: [connectivity]
permalink: connectivity-manage-connections-piggyback.html
---

You manage connections primarily through the [HTTP API](connectivity-manage-connections.html).
Although not recommended, you can also manage connections via DevOps [Piggyback commands](installation-operating.html#piggyback-commands).

All connection related piggyback commands use the following HTTP endpoint:

```text
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

## Encryption of secrets migration commands

Since Ditto 3.9.0, the following commands are available for managing encryption key rotation:

* [migrate encryption](#migrate-encryption)
* [migration status](#migration-status)
* [abort migration](#abort-migration)

These commands enable safe encryption key rotation and encryption disable workflows without downtime or data loss.
For detailed information about encryption configuration and workflows, refer to
[Encrypt sensitive data in Connections](installation-operating.html#encrypt-sensitive-data-in-connections).

### Migrate encryption

Trigger batch processing of all persisted connection data (snapshots and journal events). The command supports two workflows:

**Migration Logic:**
- **Key Rotation:** `encryption-enabled = true` + both keys set → Decrypt with old key, re-encrypt with new key
- **Disable Encryption:** `encryption-enabled = false` + old key set → Decrypt with old key, write plaintext

The configuration determines which workflow is executed.

**Start a new migration:**

```json
{
  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:migrateEncryption",
    "dryRun": false,
    "resume": false
  }
}
```

**Dry-run migration (count affected documents without making changes):**

```json
{
  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:migrateEncryption",
    "dryRun": true,
    "resume": false
  }
}
```

**Resume a previously started/aborted migration:**

```json
{
  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:migrateEncryption",
    "dryRun": false,
    "resume": true
  }
}
```

If the previous migration already completed, or no previous migration exists (e.g., after a dry run which does
not persist progress), the response will be `200 OK` with `phase: "already_completed"` instead of starting a new
migration.

**Example response when starting/resuming migration:**

```json
{
  "type": "connectivity.responses:migrateEncryption",
  "status": 202,
  "phase": "snapshots",
  "dryRun": false,
  "resumed": true,
  "startedAt": "2026-02-16T10:00:00Z"
}
```

**Example response when there is nothing to resume (already completed or never started):**

```json
{
  "type": "connectivity.responses:migrateEncryption",
  "status": 200,
  "phase": "already_completed",
  "dryRun": false,
  "resumed": true,
  "startedAt": "2026-02-16T10:00:00Z"
}
```

The response indicates:
- **phase**: Starting phase of the migration
- **dryRun**: Whether this is a dry-run (no changes made)
- **resumed**: Whether migration was resumed from previous state or started fresh
- **startedAt**: When migration originally started (for resumed migrations) or now (for new migrations)

### Migration status

Query the current status and progress of an encryption migration.

```json
{
  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:migrateEncryptionStatus"
  }
}
```

**Example response:**

```json
{
  "type": "connectivity.responses:migrateEncryptionStatus",
  "status": 200,
  "phase": "in_progress:snapshots",
  "snapshots": {
    "processed": 150,
    "skipped": 10,
    "failed": 2
  },
  "journalEvents": {
    "processed": 0,
    "skipped": 0,
    "failed": 0
  },
  "progress": {
    "lastProcessedSnapshotId": "507f1f77bcf86cd799439011",
    "lastProcessedSnapshotPid": "connection:mqtt-prod-sensor-01",
    "lastProcessedJournalId": null,
    "lastProcessedJournalPid": null
  },
  "timing": {
    "startedAt": "2026-02-16T10:00:00Z",
    "updatedAt": "2026-02-16T10:30:00Z"
  },
  "migrationActive": true
}
```

The response includes:
- **phase**: Current migration phase (`snapshots`, `journal`, `completed`, or `in_progress:<phase>`)
- **snapshots/journalEvents**: Document counters (processed, skipped, failed)
- **progress**: Last processed document IDs and persistence IDs (connection IDs) for resume tracking
- **timing**: When migration started and was last updated
- **migrationActive**: Whether migration is currently running

### Abort migration

Abort a currently running encryption migration. The migration will stop after the current batch completes,
and progress will be saved to allow resuming later.

```json
{
  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "connectivity.commands:migrateEncryptionAbort"
  }
}
```

**Example response:**

```json
{
  "type": "connectivity.responses:migrateEncryptionAbort",
  "status": 200,
  "phase": "aborted:snapshots",
  "snapshots": {
    "processed": 150,
    "skipped": 10,
    "failed": 2
  },
  "journalEvents": {
    "processed": 0,
    "skipped": 0,
    "failed": 0
  },
  "abortedAt": "2026-02-16T10:35:00Z"
}
```

## Publishing connection logs

Please refer to [Payload mapping configuration](connectivity-manage-connections.html#publishing-connection-logs) in
HTTP API section about managing connections.

## Payload mapping configuration

Please refer to [Payload mapping configuration](connectivity-manage-connections.html#payload-mapping-configuration) in
HTTP API section about managing connections.
