---
title: WoT Validation Config API
keywords: WoT, validation, config, API, DData, distribution, recovery, example
tags: [wot]
permalink: basic-wot-validation-config.html
---

# WoT Validation Config API

This page documents the management of WoT (Web of Things) validation configuration in Eclipse Ditto, including API endpoints, dynamic configuration, distributed state, recovery, and practical examples.

## Introduction

Eclipse Ditto allows you to configure WoT Thing Model validation both statically (via config files or environment variables) and dynamically at runtime via HTTP API endpoints. This enables flexible, fine-grained control over validation behavior in distributed deployments.

## Use case: Migrating to a new WoT model version

When you update your WoT Thing Model to a new version, some existing Things or updates may not immediately comply with the new, stricter validation rules. Ditto's dynamic validation config endpoint allows you to temporarily relax validation for specific Things. This means you can:

- Allow updates that would otherwise be rejected as invalid under the new model, enabling a smooth transition period.
- Gradually adapt your data or processes to the new model requirements without downtime or disruption.
- Once migration is complete and all data is compliant, you can restore strict validation to enforce the new model for all future updates.

This approach helps ensure a seamless migration to new WoT model versions, minimizing interruptions and validation errors during the transition.

## API Endpoints

| Endpoint | Method | Purpose                                                           |
|----------|--------|-------------------------------------------------------------------|
| `/devops/wot/config` | GET | Get the current global WoT validation config                      |
| `/devops/wot/config` | PUT | Upsert (create or update) the global WoT validation config        |
| `/devops/wot/config` | DELETE | Delete the global WoT validation config (revert to static config) |
| `/devops/wot/config/merged` | GET | Get the merged (static + dynamic) config                          |
| `/devops/wot/config/dynamicConfigs` | GET | List all dynamic config sections                                  |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | GET | Get a specific dynamic config section                             |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | PUT | Upsert (create or update) a dynamic config section                |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | DELETE | Delete a dynamic config section                                   |

## Example: Enable WoT Validation Globally

```http
PUT /devops/wot/config
Content-Type: application/json

{
    "enabled": true,
    "thing": {
        "enforce": {
            "thingDescriptionModification": false,
            "attributes": true,
            "inboxMessagesInput": true,
            "inboxMessagesOutput": true,
            "outboxMessages": true
        },
        "forbid": {
            "nonModeledInboxMessages": true,
            "nonModeledOutboxMessages": true
        }
    },
    "feature": {
        "enforce": {
            "featureDescriptionModification": false,
            "presenceOfModeledFeatures": false
        },
        "forbid": {
            "featureDescriptionDeletion": false,
            "nonModeledOutboxMessages": false
        }
    }
}
```

## Example: Add a Dynamic Override for a Specific User

```http
PUT /devops/wot/config/dynamicConfigs/my-scope
Content-Type: application/json

{
  "scopeId": "my-scope",
  "validationContext": {
    "dittoHeadersPatterns": [
      { "ditto-originator": "^user:admin$" }
    ],
    "thingDefinitionPatterns": [],
    "featureDefinitionPatterns": []
  },
  "configOverrides": {
    "enabled": false,
    "logWarningInsteadOfFailingApiCalls": true
  }
}
```

## How Dynamic Config Works

- **Dynamic config sections** allow you to override the global validation config for specific API calls, based on:
  - Ditto headers (e.g., user or connection)
  - Thing or Feature model URLs
- The **merged config** is what Ditto actually uses for validation, combining static, global, and dynamic settings.

## API Schema

For details on the request/response structure, see the [Ditto HTTP API reference](/http-api-doc.html) or the Ditto API documentation.

## Technical details

### Distributed State and Recovery

In a clustered Ditto deployment, the WoT validation configuration is managed as distributed state using [Pekko Distributed Data (DData)](https://pekko.apache.org/docs/pekko/current/typed/distributed-data.html). This ensures that configuration changes are automatically replicated across all nodes in the cluster, providing consistency, high availability, and resilience.

#### DData-based Distribution

- **Replication:** Whenever the WoT validation config is created, updated, or deleted (including dynamic config sections), the change is published to the DData replicator. All Ditto nodes with the `wot-validation-config-aware` cluster role receive and apply the update.
- **Consistency:** DData uses CRDTs (Conflict-free Replicated Data Types) to ensure eventual consistency across the cluster, even in the presence of network partitions or node failures.
- **Immediate Effect:** API changes to the config (via the endpoints described above) are visible to all nodes almost immediately, ensuring that validation behavior is consistent regardless of which node handles a request.

#### Recovery and Database Synchronization

- **Persistence:** The WoT validation config is also persisted in the database (MongoDB) using event sourcing and snapshots. This ensures that the config survives full cluster restarts or catastrophic failures.
- **Startup Recovery:** On startup, each node recovers the latest WoT validation config from the database. Once recovery is complete, the config is published to DData, ensuring that all nodes are synchronized with the most recent state.
- **Rolling Restarts and Failover:** Because the config is both persisted and distributed, rolling restarts or failover scenarios do not result in loss of configuration. Any node joining the cluster will receive the current config from DData or recover it from the database if needed.

This architecture ensures that the WoT validation configuration is always up-to-date, consistent, and highly available, even in the face of failures, restarts, or network issues. 