---
title: Operating - DevOps Commands
tags: [installation]
keywords: operating, devops, piggyback, commands, logging, configuration, namespace, cleanup
permalink: operating-devops.html
---

You use DevOps commands to manage a running Ditto installation without restarts -- adjusting log levels, inspecting configuration, and sending piggyback commands to internal actors.

{% include callout.html content="**TL;DR**: Access the `/devops` API with the DevOps user credentials to dynamically change log levels, retrieve runtime configuration, manage background cleanup, and execute piggyback commands against internal services." type="primary" %}

## Overview

The DevOps commands API lets you:

* Dynamically retrieve and change log levels
* Retrieve service configuration at runtime
* Send piggyback commands to internal actors
* Manage background cleanup and search synchronization
* Erase data within a namespace

## DevOps user

[pubsubmediator]: https://pekko.apache.org/docs/pekko/current/distributed-pub-sub.html

The DevOps user authenticates requests to these endpoints:

```text
/devops
/api/2/connections
```

{% include note.html content="The default devops credentials are username: `devops`, password: `foobar`. The password can be changed by setting the environment variable `DEVOPS_PASSWORD` in the gateway service." %}

## Dynamically adjust log levels

Changing log levels at runtime is useful for debugging problems without restarting services.

### Retrieve all log levels

`GET /devops/logging`

```json
{
  "gateway": {
    "10.0.0.1": {
      "type": "devops.responses:retrieveLoggerConfig",
      "status": 200,
      "serviceName": "gateway",
      "instance": "10.0.0.1",
      "loggerConfigs": [
        { "level": "info", "logger": "ROOT" },
        { "level": "info", "logger": "org.eclipse.ditto" },
        { "level": "warn", "logger": "org.mongodb.driver" }
      ]
    }
  }
}
```

### Change a log level for all services

`PUT /devops/logging`

```json
{
  "logger": "org.eclipse.ditto",
  "level": "debug"
}
```

### Retrieve log levels for one service

`GET /devops/logging/gateway`

### Change a log level for one service

`PUT /devops/logging/gateway`

```json
{
  "logger": "org.eclipse.ditto",
  "level": "debug"
}
```

## Dynamically retrieve configurations

Access runtime configurations at `/devops/config/` with optional filters by service name, instance ID, and configuration path.

### Retrieve configuration with a path filter

`GET /devops/config?path=ditto.info`

Always include the `path` parameter. Omitting it returns the full configuration of all services, which can be megabytes in size and may exceed the 250 kB cluster message limit.

The path `ditto.info` returns service name, instance index, JVM arguments, and environment variables:

```json
{
  "gateway": {
    "10.0.0.1": {
      "type": "common.responses:retrieveConfig",
      "status": 200,
      "config": {
        "env": { "PATH": "/usr/games:/usr/local/games" },
        "service": { "instance-id": "10.0.0.1", "name": "gateway" },
        "vm-args": ["-Dfile.encoding=UTF-8"]
      }
    }
  }
}
```

### Retrieve configuration for a specific instance

`GET /devops/config/gateway/1?path=ditto`

This is faster than retrieving from all instances because the response is not aggregated.

## Piggyback commands

Piggyback commands let you send a command to any actor in the Ditto cluster. A piggyback command conforms to this schema:

{% include docson.html schema="jsonschema/piggyback-command.json" %}

### Headers

| Header | Values | Default | Purpose |
|--------|--------|---------|---------|
| `is-group-topic` | `true` / `false` | `false` | Send to all actors in a group vs. one actor |
| `aggregate` | `true` / `false` | `true` | Aggregate responses from multiple actors |
| `ditto-sudo` | `true` / `false` | `false` | Bypass enforcement/authorization |

### Managing policies

Send any [PolicyCommand](https://github.com/eclipse-ditto/ditto/blob/master/policies/model/src/main/java/org/eclipse/ditto/policies/model/signals/commands/PolicyCommand.java) as a piggyback. Use the **internal JSON representation** (from the `fromJson` methods), not the [Ditto Protocol](protocol-specification-policies.html) format.

**Create a policy:**

```json
{
  "targetActorSelection": "/system/sharding/policy",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "policies.commands:createPolicy",
    "policy": {
      "policyId": "<policy-id>",
      "entries": {}
    }
  }
}
```

**Retrieve a policy:**

```json
{
  "targetActorSelection": "/system/sharding/policy",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "policies.commands:retrievePolicy",
    "policyId": "<policy-id>"
  }
}
```

### Managing things

Send any [ThingCommand](https://github.com/eclipse-ditto/ditto/blob/master/things/model/src/main/java/org/eclipse/ditto/things/model/signals/commands/ThingCommand.java) as a piggyback. Use the **internal JSON representation**.

**Create a thing:**

```json
{
  "targetActorSelection": "/system/sharding/thing",
  "headers": {
    "aggregate": false,
    "is-group-topic": true,
    "ditto-sudo": true
  },
  "piggybackCommand": {
    "type": "things.commands:createThing",
    "thing": {
      "thingId": "<thing-id>",
      "policyId": "<policy-id>"
    }
  }
}
```

### Managing connections

Use the [HTTP API](connectivity-manage-connections.html) for connection management. [Piggyback-based management](connectivity-manage-connections-piggyback.html) is also available.

### Managing background cleanup

Each Things, Policies, and Connectivity instance has a cleanup coordinator actor at `/user/<SERVICE_NAME>Root/persistenceCleanup`.

**Query cleanup state:**

`POST /devops/piggyback/<SERVICE_NAME>?timeout=10s`

```json
{
  "targetActorSelection": "/user/<SERVICE_NAME>Root/persistenceCleanup",
  "headers": {},
  "piggybackCommand": {
    "type": "status.commands:retrieveHealth"
  }
}
```

**Query cleanup configuration:**

```json
{
  "targetActorSelection": "/user/<SERVICE_NAME>Root/persistenceCleanup",
  "headers": {},
  "piggybackCommand": {
    "type": "common.commands:retrieveConfig"
  }
}
```

**Modify cleanup configuration:**

```json
{
  "targetActorSelection": "/user/<SERVICE_NAME>Root/persistenceCleanup",
  "headers": {
    "aggregate": false,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "common.commands:modifyConfig",
    "config": {
      "quiet-period": "240d",
      "last-pid": "thing:namespace:PID-lower-bound"
    }
  }
}
```

**Clean up a specific entity:**

`POST /devops/piggyback/things/<INSTANCE_INDEX>?timeout=10s`

```json
{
  "targetActorSelection": "/system/sharding/thing",
  "headers": { "aggregate": false },
  "piggybackCommand": {
    "type": "cleanup.sudo.commands:cleanupPersistence",
    "entityId": "ditto:thing1"
  }
}
```

### Managing background synchronization

The background sync actor ensures eventual consistency of the search index. It responds to the same command types as the cleanup coordinator.

`POST /devops/piggyback/search/<INSTANCE_INDEX>?timeout=10s`

```json
{
  "targetActorSelection": "/user/thingsWildcardSearchRoot/searchUpdaterRoot/backgroundSync/singleton",
  "headers": {
    "aggregate": false,
    "is-group-topic": false
  },
  "piggybackCommand": {
    "type": "<COMMAND-TYPE>"
  }
}
```

Supported `COMMAND-TYPE` values: `common.commands:shutdown`, `common.commands:retrieveConfig`, `common.commands:modifyConfig`, `status.commands:retrieveHealth`.

{% include note.html content="Only a subset of the configuration has an effect when changed via `common.commands:modifyConfig` command: `enabled`, `quiet-period` and `keep.events`. Refer to [Ditto configuration](operating-configuration.html) for instructions how to modify the other configuration settings." %}

### Force search index update

**All things:**

`POST /devops/piggyback/search?timeout=10s`

```json
{
  "targetActorSelection": "/user/thingsWildcardSearchRoot/searchUpdaterRoot/backgroundSyncProxy",
  "headers": {
    "aggregate": false,
    "is-group-topic": false,
    "force-update": true
  },
  "piggybackCommand": {
    "type": "common.commands:shutdown"
  }
}
```

**Things in specific namespaces:** Add a `"namespaces": ["namespace1", "namespace2"]` header.

**A single thing:**

`POST /devops/piggyback/search/<INSTANCE_INDEX>?timeout=0`

```json
{
  "targetActorSelection": "/user/thingsWildcardSearchRoot/searchUpdaterRoot/thingsUpdater",
  "headers": {
    "aggregate": false,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "thing-search.sudo.commands:sudoUpdateThing",
    "thingId": "<THING-ID>"
  }
}
```

## Erasing data within a namespace

You can erase all data within a namespace during live operations by following these four steps in sequence.

### Step 1: Block the namespace

`PUT /devops/piggyback?timeout=10s`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": { "aggregate": false },
  "piggybackCommand": {
    "type": "namespaces.commands:blockNamespace",
    "namespace": "namespaceToBlock"
  }
}
```

The namespace stays blocked for the lifetime of the cluster or until you unblock it in step 4.

### Step 2: Shut down actors in the namespace

`PUT /devops/piggyback?timeout=0`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "piggybackCommand": {
    "type": "common.commands:shutdown",
    "reason": {
      "type": "purge-namespace",
      "details": "namespaceToShutdown"
    }
  }
}
```

This command has no response (always returns `408` timeout). You can send it multiple times to ensure completion.

### Step 3: Purge data from persistence

`PUT /devops/piggyback?timeout=10s`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": {
    "aggregate": true,
    "is-group-topic": true
  },
  "piggybackCommand": {
    "type": "namespaces.commands:purgeNamespace",
    "namespace": "namespaceToPurge"
  }
}
```

Set the timeout to a safe margin above the estimated erasure time. The response reports results per resource type (`thing`, `policy`, `thing-search`).

### Step 4: Unblock the namespace

`PUT /devops/piggyback?timeout=10s`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": { "aggregate": false },
  "piggybackCommand": {
    "type": "namespaces.commands:unblockNamespace",
    "namespace": "namespaceToUnblock"
  }
}
```

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Operating - MongoDB](operating-mongodb.html)
* [Operating - Monitoring & Tracing](operating-monitoring.html)
* [Manage connections via HTTP](connectivity-manage-connections.html)
