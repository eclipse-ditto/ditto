---
title: Operating - Configuration
tags: [installation]
keywords: operating, configuration, environment variables, system properties, config files, rate limiting, entity creation
permalink: operating-configuration.html
---

You configure Ditto services through config files, environment variables, or Java system properties.

{% include callout.html content="**TL;DR**: Override any Ditto config value by setting an environment variable (when the config uses `${?ENV_NAME}` syntax) or by passing a Java system property (`-Dkey=value`) to the service process." type="primary" %}

## Overview

Each Ditto microservice ships with a default [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) configuration file. You can customize behavior without modifying source code by using environment variables, Java system properties, or extension config files.

## How it works

### Config file structure

Each microservice has its own configuration file with sensible defaults:

* Policies: [policies.conf](https://github.com/eclipse-ditto/ditto/blob/master/policies/service/src/main/resources/policies.conf)
* Things: [things.conf](https://github.com/eclipse-ditto/ditto/blob/master/things/service/src/main/resources/things.conf)
* Things-Search: [things-search.conf](https://github.com/eclipse-ditto/ditto/blob/master/thingsearch/service/src/main/resources/search.conf)
* Connectivity: [connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf)
* Gateway: [gateway.conf](https://github.com/eclipse-ditto/ditto/blob/master/gateway/service/src/main/resources/gateway.conf)

### Environment variables

When you find the syntax `${?UPPER_CASE_ENV_NAME}` in a config file, you can override that value by setting the corresponding environment variable in the container.

### Java system properties

When no environment variable is defined for a config option, you can still change the default by passing a Java system property to the process.

The following example sets the DevOps password for the gateway service in a `docker-compose.yml` file:

```yaml
environment:
  - JAVA_TOOL_OPTIONS=-Dditto.gateway.authentication.devops.password=foobar
```

The microservice executable is called `starter.jar`. Place all system properties before the `-jar` option.

## Configuration topics

### Restricting entity creation

By default, Ditto allows any authenticated user to create policies or things in any namespace. You can restrict this by editing the `ditto-entity-creation.conf` file.

The basic schema uses `grant` and `revoke` lists:

```hocon
ditto.entity-creation {
  grant = [
    {
      resource-types = []
      namespaces = []
      auth-subjects = []
      thing-definitions = []
    }
  ]
  revoke = []
}
```

The enforcement logic works as follows:

1. Find a matching entry in the `grant` list.
2. Check that no matching entry exists in the `revoke` list.
3. Accept the request if both conditions pass; otherwise deny it.

An entry matches when **all** of these conditions are true:

* The `resource-types` list is empty or contains the requested resource type (`policy` or `thing`).
* The `namespaces` list is empty or contains a wildcard matching the requested namespace (`*` matches any number of characters, `?` matches exactly one).
* The `auth-subjects` list is empty or contains at least one matching wildcard for the request's auth subjects.
* For `thing` resources only: the `thing-definitions` list is empty or contains a matching wildcard.

An entry with all empty lists matches everything. So the simplest "allow all" configuration is:

```hocon
ditto.entity-creation {
  grant = [{}]
}
```

To restrict entity creation to specific subjects via system properties:

```bash
-Dditto.entity-creation.grant.0.auth-subjects.0=pre:admin
-Dditto.entity-creation.grant.0.auth-subjects.1=integration:some-connection
```

Configure these properties on both the "things" and "policies" services.

### Encrypting sensitive connection data

Since Ditto 3.1.0, you can encrypt sensitive fields in [connections](basic-connections.html) before they reach the database. This encryption is transparent -- retrieval endpoints return decrypted data automatically.

Ditto uses 256-bit AES with AES/GCM/NoPadding. You can generate a key with:

```bash
openssl rand 32 | basenc --base64url
```

The key must be 256-bit, [Base64-encoded with URL-safe alphabet](https://www.rfc-editor.org/rfc/rfc4648#section-5) using UTF-8.

The default fields that get encrypted are:

* `/uri`
* `/credentials/key`
* `/sshTunnel/credentials/password`
* `/sshTunnel/credentials/privateKey`
* `/credentials/parameters/accessKey`
* `/credentials/parameters/secretKey`
* `/credentials/parameters/sharedKey`
* `/credentials/clientSecret`

Only string values are supported. URI values receive special treatment -- only the password portion of the user info is encrypted.

Find the full encryption configuration in [connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf) at the `ditto.connectivity.connection.encryption` section.

{% include note.html content="If you disable encryption later, keep the symmetric key in the configuration. Without it, previously encrypted values cannot be decrypted, and you would need to manually re-enter the encrypted connection fields." %}

### Rate limiting

Since Ditto 2.4.0, [connections](basic-connections.html) and [WebSockets](httpapi-protocol-bindings-websocket.html) are not artificially throttled when consuming messages by default. You can enable per-connection or per-WebSocket throttling through the `throttling` sections in the service configuration files.

### Pre-defined extra fields

Starting with Ditto 3.7.0, you can statically configure [enrichment of `extraFields`](basic-enrichment.html) in the "things" service configuration. This avoids an internal roundtrip from edge services to the things service for each event or message.

Configure pre-defined extra fields for events and messages independently:

```hocon
ditto {
  things {
    thing {
      event {
        pre-defined-extra-fields = [
          {
            namespaces = []
            condition = "exists(definition)"
            extra-fields = ["definition"]
          },
          {
            namespaces = ["org.eclipse.ditto.lamps"]
            extra-fields = [
              "attributes/manufacturer",
              "attributes/serial"
            ]
          }
        ]
      }
    }
  }
}
```

Each entry supports:
* `namespaces`: restrict to specific namespaces (empty means all; supports `*` and `?` wildcards)
* `condition`: an [RQL condition](basic-rql.html) to check before adding extra fields
* `extra-fields`: list of JSON pointers to include proactively

### Limiting indexed fields

Since Ditto 3.5.0, you can control which thing fields get indexed in the search database per namespace pattern. This reduces search database load when you only search on a few fields.

```hocon
ditto {
  caching-signal-enrichment-facade-provider = org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchIndexingSignalEnrichmentFacadeProvider
  search {
    namespace-indexed-fields = [
      {
        namespace-pattern = "org.eclipse.test"
        indexed-fields = [
          "attributes",
          "features/info/properties"
        ]
      }
    ]
  }
}
```

Ditto matches the thing's namespace against the **first** matching `namespace-pattern`, so order your patterns from most specific to least specific. Ditto automatically adds system-level fields it needs to operate.

### Configuring additional search indexes

Since Ditto 3.9.0, you can define custom MongoDB indexes for the search collection to optimize specific query patterns.

```hocon
ditto {
  search {
    index-initialization {
      custom-indexes {
        my_custom_idx {
          fields = [
            { name = "t.attributes/region" }
            { name = "t.attributes/timestamp", direction = "DESC" }
          ]
        }
      }
    }
  }
}
```

Field naming conventions for custom indexes:

| Field | Path |
|-------|------|
| Namespace | `_namespace` |
| Thing ID | `_id` |
| Policy ID | `t.policyId` |
| Attributes | `t.attributes.<path>` |
| Feature properties | `t.features.<featureId>.properties.<path>` |
| Last modified | `_modified` |
| Created | `_created` |

Custom indexes activate automatically when defined, and Ditto drops them if you remove them from configuration.

### Merge operations configuration

Starting with Ditto 3.8.0, the Things service supports configuration for merge operations with patch conditions. Set `MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING` to `true` to remove empty JSON objects that result from patch condition filtering:

```hocon
ditto {
  things {
    thing {
      merge {
        remove-empty-objects-after-patch-condition-filtering = true
        remove-empty-objects-after-patch-condition-filtering = ${?MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING}
      }
    }
  }
}
```

## Further reading

* [Operating - MongoDB](operating-mongodb.html)
* [Operating - Authentication](operating-authentication.html)
* [Operating - DevOps Commands](operating-devops.html)
* [Operating - Monitoring & Tracing](operating-monitoring.html)
* [Extending Ditto](installation-extending.html)
