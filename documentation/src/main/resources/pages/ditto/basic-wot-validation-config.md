---
title: WoT Validation Configuration
keywords: WoT, validation, config, API, DData, distribution, recovery, example
tags: [wot]
permalink: basic-wot-validation-config.html
---

You manage WoT Thing Model validation rules at runtime through the Ditto devops API, enabling dynamic control over which validation checks apply to specific users, models, or deployment stages.

{% include callout.html content="**TL;DR**: Use `PUT /devops/wot/config` to set global validation rules, and `PUT /devops/wot/config/dynamicConfigs/{scopeId}` to override rules for specific users or models. This is especially useful during model migrations." type="primary" %}

## Overview

Eclipse Ditto supports both static WoT validation (via config files or environment variables) and dynamic runtime configuration via HTTP API endpoints. Dynamic configuration lets you:

* Temporarily relax validation during model migrations
* Apply different validation rules per user, connection, or model
* Adjust validation without restarting Ditto

## How it works

### Use case: model migration

When you upgrade a WoT Thing Model to a new version, existing Things may not immediately comply with stricter validation rules. The dynamic validation API lets you:

1. Temporarily relax validation for affected Things
2. Migrate data and processes to the new model
3. Restore strict validation once migration is complete

### Configuration layers

Ditto evaluates validation rules in layers:

1. **Static config** -- defined in `things.conf` or environment variables
2. **Global dynamic config** -- set via the `/devops/wot/config` API
3. **Scoped dynamic overrides** -- set via `/devops/wot/config/dynamicConfigs/{scopeId}` for specific contexts

The **merged config** (viewable at `/devops/wot/config/merged`) is what Ditto actually uses for validation.

## API endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/devops/wot/config` | GET | Get the current global WoT validation config |
| `/devops/wot/config` | PUT | Create or update the global config |
| `/devops/wot/config` | DELETE | Delete the global config (revert to static config) |
| `/devops/wot/config/merged` | GET | Get the effective merged config |
| `/devops/wot/config/dynamicConfigs` | GET | List all dynamic config sections |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | GET | Get a specific dynamic config section |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | PUT | Create or update a dynamic config section |
| `/devops/wot/config/dynamicConfigs/{scopeId}` | DELETE | Delete a dynamic config section |

## Examples

### Enable global validation

```http
PUT /devops/wot/config
Content-Type: application/json

{
    "enabled": true,
    "thing": {
        "enforce": {
            "thing-description-modification": false,
            "attributes": true,
            "inbox-messages-input": true,
            "inbox-messages-output": true,
            "outbox-messages": true
        },
        "forbid": {
            "non-modeled-inbox-messages": true,
            "non-modeled-outbox-messages": true
        }
    },
    "feature": {
        "enforce": {
            "feature-description-modification": false,
            "presence-of-modeled-features": false
        },
        "forbid": {
            "feature-description-deletion": false,
            "non-modeled-outbox-messages": false
        }
    }
}
```

### Add a dynamic override for a specific user

This example disables validation and switches to warning-only mode for an admin user:

```http
PUT /devops/wot/config/dynamicConfigs/my-scope
Content-Type: application/json

{
  "scope-id": "my-scope",
  "validation-context": {
    "ditto-headers-patterns": [
      { "ditto-originator": "^user:admin$" }
    ],
    "thing-definition-patterns": [],
    "feature-definition-patterns": []
  },
  "config-overrides": {
    "enabled": false,
    "log-warning-instead-of-failing-api-calls": true
  }
}
```

### How dynamic overrides match

Dynamic config sections match API calls based on three criteria (all must match for an override to apply):

| Criteria | Description |
|----------|-------------|
| `ditto-headers-patterns` | Match Ditto headers (e.g., `ditto-originator` for user or connection identity). Multiple header blocks are OR-combined; fields within a block are AND-combined. |
| `thing-definition-patterns` | Match the Thing's WoT Thing Model URL (OR-combined) |
| `feature-definition-patterns` | Match the Feature's WoT Thing Model URL (OR-combined) |

## Technical details

### Distributed state and recovery

In a clustered Ditto deployment, the WoT validation configuration uses [Pekko Distributed Data (DData)](https://pekko.apache.org/docs/pekko/current/typed/distributed-data.html) for replication:

* **Replication** -- configuration changes propagate to all nodes with the `wot-validation-config-aware` cluster role
* **Consistency** -- DData uses CRDTs (Conflict-free Replicated Data Types) for eventual consistency
* **Persistence** -- configuration is also stored in MongoDB via event sourcing and snapshots
* **Recovery** -- on startup, each node recovers the latest configuration from the database and publishes it to DData
* **Resilience** -- rolling restarts and failover do not result in configuration loss

## Further reading

* [WoT Overview](basic-wot-integration.html) -- WoT integration concepts and static configuration
* [WoT Integration Example](basic-wot-integration-example.html) -- hands-on walkthrough
* [Ditto HTTP API reference](http-api-doc.html) -- full API schema details
