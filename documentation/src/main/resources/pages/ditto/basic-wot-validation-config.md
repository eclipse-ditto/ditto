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

### Accept writes matching an alternative Thing model during a migration

Relaxing validation with `log-warning-instead-of-failing-api-calls` accepts *any* payload. To instead accept only
payloads which fully match another published Thing model, list it in `alternative-thing-definitions`.
This example lets Things still on version `8.0.0` already accept writes shaped like version `9.1.0`:

```http
PUT /devops/wot/config/dynamicConfigs/lora-gateway-8.0.0
Content-Type: application/json

{
  "scope-id": "lora-gateway-8.0.0",
  "validation-context": {
    "thing-definition-patterns": [
      "^https://models\\.example\\.com/gateway/lora-gateway-8\\.0\\.0\\.tm\\.jsonld$"
    ]
  },
  "config-overrides": {
    "alternative-thing-definitions": [
      "https://models.example.com/gateway/lora-gateway-9.1.0.tm.jsonld"
    ]
  }
}
```

A write is first validated against the Thing's own definition. Only if that fails with a payload validation error,
it is validated against each alternative in turn and accepted as soon as one of them fully passes.
If all of them fail, the error of the Thing's own definition is returned.

* **Exact source version** -- a section must define `thing-definition-patterns`. Use one section per source version,
  so that each entry can be removed as soon as no Thing uses that version anymore.
* **No chaining** -- alternatives are only looked up for the Thing's own definition: `8.0.0 → 9.0.0` and
  `9.0.0 → 9.1.0` do not let a Thing on `8.0.0` accept `9.1.0`.
* **Forward only** -- a Thing already migrated to `9.1.0` does not accept `8.0.0`-shaped writes unless a section
  for `9.1.0` lists `8.0.0` as alternative.
* **Combined** -- if several sections match, their alternatives are combined.
* **Features** -- Feature-level writes are validated against the alternative Thing model's submodel whose instance
  name equals the Feature ID. If the alternative does not model that Feature, it does not accept the write.
* **Merge (PATCH)** -- against an alternative, only the attributes and Feature properties changed by the patch are
  validated, not the complete merged Thing.
* Failures to resolve a model are not retried against alternatives.

Every write accepted via an alternative is logged at `INFO` with its correlation ID and counted in the metric
`wot_validation_accepted_by_alternative`, tagged with `thing_definition` and `alternative_thing_definition`.

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
