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
openssl rand -base64 32
```

or using the Java standard library:

```java
javax.crypto.KeyGenerator keyGen = KeyGenerator.getInstance("AES");
keyGen.init(256);
javax.crypto.SecretKey aes256SymmetricKey = keyGen.generateKey();
```

or use the convenience method [EncryptorAesGcm.generateAESKeyAsString()](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/util/EncryptorAesGcm.java#L100).

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

#### Encryption key rotation

Since Ditto 3.9.0, you can rotate encryption keys without downtime or data loss using a dual-key configuration
and a migration command.

##### Dual-key configuration

The encryption configuration supports both a current key and an optional old key for fallback decryption:

```hocon
ditto.connectivity.connection.encryption {
  encryption-enabled = true
  symmetrical-key = "YOUR_NEW_KEY_HERE"           # Current key for encrypting new data
  old-symmetrical-key = "YOUR_OLD_KEY_HERE"       # Optional fallback key for decrypting old data
  json-pointers = [...]
}
```

**Behavior:**
- **Encryption:** Always uses `symmetrical-key` for encrypting new data
- **Decryption:** Tries `symmetrical-key` first, falls back to `old-symmetrical-key` if decryption fails
- **Migration:** Explicit DevOps command re-encrypts existing data from old key to new key

**Migration Decision Logic:**

The migration command automatically detects the intended workflow based on configuration:

- **Encryption enabled + both keys set** → Key rotation (decrypt with old, encrypt with new)
- **Encryption enabled + only current key** → Error (nothing to migrate)
- **Encryption disabled + old key set** → Disable workflow (decrypt with old, write plaintext)
- **Encryption disabled + no keys** → Error (cannot migrate)

##### Key rotation workflow

To rotate an encryption key:

1. **Generate a new encryption key** using the methods described above

2. **Update configuration** with both keys:
   ```hocon
   ditto.connectivity.connection.encryption {
     encryption-enabled = true
     symmetrical-key = "NEW_KEY"      # New key
     old-symmetrical-key = "OLD_KEY"  # Current key becomes old key
   }
   ```

3. **Restart connectivity service** to load the new configuration

4. **Run dry-run migration** to verify affected documents:
   ```bash
   curl -X POST http://localhost:8080/devops/piggyback/connectivity \
     -u devops:devopsPw1! \
     -H 'Content-Type: application/json' \
     -d '{
     "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
     "headers": {
       "aggregate": false
     },
     "piggybackCommand": {
       "type": "connectivity.commands:migrateEncryption",
       "dryRun": true,
       "resume": false
     }
   }'
   ```

5. **Start actual migration** to re-encrypt all persisted data:
   ```bash
   curl -X POST http://localhost:8080/devops/piggyback/connectivity \
     -u devops:devopsPw1! \
     -H 'Content-Type: application/json' \
     -d '{
     "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
     "headers": {
       "aggregate": false
     },
     "piggybackCommand": {
       "type": "connectivity.commands:migrateEncryption",
       "dryRun": false,
       "resume": false
     }
   }'
   ```

6. **Monitor migration progress**:
   ```bash
   curl -X POST http://localhost:8080/devops/piggyback/connectivity \
     -u devops:devopsPw1! \
     -H 'Content-Type: application/json' \
     -d '{
     "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
     "headers": {
       "aggregate": false
     },
     "piggybackCommand": {
       "type": "connectivity.commands:migrateEncryptionStatus"
     }
   }'
   ```

7. **After successful migration**, remove the old key from configuration and restart the service

**Additional migration commands:**

- **Abort running migration:**
  ```bash
  curl -X POST http://localhost:8080/devops/piggyback/connectivity \
    -u devops:devopsPw1! \
    -H 'Content-Type: application/json' \
    -d '{
    "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
    "headers": {
      "aggregate": false
    },
    "piggybackCommand": {
      "type": "connectivity.commands:migrateEncryptionAbort"
    }
  }'
  ```

- **Resume aborted migration:**
  ```bash
  curl -X POST http://localhost:8080/devops/piggyback/connectivity \
    -u devops:devopsPw1! \
    -H 'Content-Type: application/json' \
    -d '{
    "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
    "headers": {
      "aggregate": false
    },
    "piggybackCommand": {
      "type": "connectivity.commands:migrateEncryption",
      "dryRun": false,
      "resume": true
    }
  }'
  ```

  {% include note.html content="If the previous migration already completed, was never started, or only ran as a dry run (which does not persist progress), the resume command returns `200 OK` with `phase: \"already_completed\"` instead of starting a new migration. This makes resume safe to call idempotently." %}

**Migration details:**
- The migration processes both connection snapshots and journal events in MongoDB
- Progress is persisted to allow resuming after abort or service restart
- Migration runs in batches to avoid overwhelming the database
- The batch size can be configured via `ditto.connectivity.connection.encryption.migration.batch-size`
- Migration is throttled to prevent database overload (default: 200 documents/minute)
- Throttling rate can be configured via `ditto.connectivity.connection.encryption.migration.max-documents-per-minute`
- Set throttling to 0 to disable (not recommended for production)

##### Disabling encryption

To disable encryption while preserving access to already encrypted data:

1. **Update configuration** with encryption disabled but old key present:
   ```hocon
   ditto.connectivity.connection.encryption {
     encryption-enabled = false
     symmetrical-key = ""                       # Empty - no new encryption
     old-symmetrical-key = "YOUR_CURRENT_KEY"   # Keep for decryption
   }
   ```

2. **Restart connectivity service**

3. **Run migration** to decrypt all existing encrypted data:
   ```bash
   curl -X POST http://localhost:8080/devops/piggyback/connectivity \
     -u devops:devopsPw1! \
     -H 'Content-Type: application/json' \
     -d '{
     "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
     "headers": {
       "aggregate": false
     },
     "piggybackCommand": {
       "type": "connectivity.commands:migrateEncryption",
       "dryRun": false,
       "resume": false
     }
   }'
   ```

4. **After migration completes**, remove the old key from configuration and restart

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

      message {
        pre-defined-extra-fields = [
          {
            namespaces = []
            condition = "exists(definition)"
            extra-fields = [
              "definition"
            ]
          }
        ]
      }
    }
  }
}
```

Configure pre-defined extra fields for `event` and `message` independently. Each entry supports:
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

To configure via system properties:

```shell
-Dditto.search.namespace-indexed-fields.0.namespace-pattern=org.eclipse.test
-Dditto.search.namespace-indexed-fields.0.indexed-fields.0=attributes
-Dditto.search.namespace-indexed-fields.0.indexed-fields.1=features/info/properties
-Dditto.search.namespace-indexed-fields.0.indexed-fields.2=features/info/other
-Dditto.search.namespace-indexed-fields.1.namespace-pattern=org.eclipse*
-Dditto.search.namespace-indexed-fields.1.indexed-fields.0=attributes
-Dditto.search.namespace-indexed-fields.1.indexed-fields.1=features/info
```

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

To configure via system properties:

```shell
-Dditto.search.index-initialization.custom-indexes.my_custom_idx.fields.0.name=t.attributes/region
-Dditto.search.index-initialization.custom-indexes.my_custom_idx.fields.1.name=t.attributes/timestamp
-Dditto.search.index-initialization.custom-indexes.my_custom_idx.fields.1.direction=DESC
```

When deploying via Helm, configure in `values.yaml`:

```yaml
thingsSearch:
  config:
    indexInitialization:
      enabled: true
      customIndexes:
        my_custom_idx:
          fields:
            - name: "t.attributes/region"
              direction: "ASC"
            - name: "t.attributes/timestamp"
              direction: "DESC"
```

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

When deploying via Helm:

```yaml
things:
  config:
    merge:
      removeEmptyObjectsAfterPatchConditionFiltering: true
```

Or as a Kubernetes environment variable:

```yaml
env:
- name: MERGE_REMOVE_EMPTY_OBJECTS_AFTER_PATCH_CONDITION_FILTERING
  value: "true"
```

### Gateway namespace access control

Since Ditto *3.9.0*, the Ditto **gateway** service supports restricting which namespaces a client can access based on
the JWT claims or HTTP headers present in the request. This provides a cheap enforcement layer at the API gateway level,
before policy-based access control is evaluated.

Namespace access control is configured via `ditto.gateway.authentication.namespace-access` in
[gateway.conf](https://github.com/eclipse/ditto/blob/master/gateway/service/src/main/resources/gateway.conf).

#### How it works

A list of **rules** is defined. Each rule can specify:
- **`conditions`** (AND semantics): a list of placeholder expressions that must all evaluate to a non-empty value for
  the rule to apply. Placeholders: `{%raw%}{{ jwt:claim }}{%endraw%}` for JWT claims, `{%raw%}{{ header:name }}{%endraw%}` for HTTP headers.
  Functions `fn:filter` and `fn:default` can be used (see below).
- **`resource-types`**: list of resource types this rule applies to (`"thing"`, `"policy"`).
  An empty list means the rule applies to all resource types.
- **`allowed-namespaces`**: list of exact namespace names or wildcard patterns (`*` = any chars, `?` = single char).
  An empty list means all namespaces are allowed (unless blocked).
- **`blocked-namespaces`**: list of exact namespace names or wildcard patterns that are explicitly blocked (takes precedence over allowed).

Multiple rules are evaluated with **OR semantics**: a namespace is accessible if it is allowed by *any* matching rule.
**Fail-closed**: if namespace-access rules are configured but none of them match the current request (i.e. no rule's
conditions are satisfied), access is **denied**. If no rules are configured at all, access is allowed (backward compatible).
This means a request from an unrecognized issuer or with unexpected headers will be denied rather than silently granted full access.

#### Search behavior

For `GET /search/things` requests without an explicit `namespaces` parameter, Ditto automatically injects the
allowed namespaces from the applicable rule. If only wildcard patterns are configured (e.g. `"org.eclipse.*"`),
or if no rule conditions match (fail-closed), Ditto injects an **empty namespaces set**, returning no results.
In this case, clients should provide explicit namespace values in the `namespaces` query parameter.

#### WebSocket and SSE behavior

Namespace access rules are evaluated once at **connection time** using the JWT present when the WebSocket or SSE
session is established. The validator is **not updated** when a JWT is refreshed mid-session; namespace access
continues to reflect the access granted at connect time.

Namespace enforcement applies to incoming commands (things and policies) sent over WebSocket. Search commands
(`QueryThings`) are not blocked at the namespace level since they carry no entity ID; namespace filtering for
search is handled via the `namespaces` parameter instead.

#### Configuration example

```hocon
ditto.gateway.authentication {
  namespace-access = [
    {
      # Rule applies only when the JWT issuer matches
      conditions = [
        "{%raw%}{{ jwt:iss | fn:filter('like','https://my-idp.example.com*') }}{%endraw%}"
      ]
      resource-types = ["thing", "policy"]
      allowed-namespaces = [
        "org.example.*"
        "concrete.namespace"
      ]
      blocked-namespaces = [
        "forbidden.namespace"
      ]
    }
  ]
}
```

#### Using `fn:default` before `fn:filter` for optional headers

When a condition references an HTTP header that may be absent, always add `fn:default` before `fn:filter`:

```
"{%raw%}{{ header:someheader | fn:default('safe') | fn:filter('ne','dangerous') }}{%endraw%}"
```

Without `fn:default`, an absent header produces an empty pipeline result which causes the entire condition to fail.
This would silently bypass the rule rather than enforcing it, which is a security footgun.

#### Invalid namespace patterns

If a namespace pattern (in `allowed-namespaces` or `blocked-namespaces`) is syntactically invalid, Ditto will fail
at startup with a `DittoConfigError`. This prevents operators from inadvertently deploying a configuration where
access control rules are silently skipped.

### Namespace root policies

Since Ditto *3.9.0*, operators can configure **namespace root policies** — pre-existing policies whose
`importable: implicit` entries are automatically merged into every policy in a matching namespace at enforcer-build
time. This enables cross-cutting access grants (e.g. a tenant-wide read subject) without modifying any stored
policy.

For the end-user concept, see [basic-policy.html#namespace-root-policies](basic-policy.html#namespace-root-policies).

#### Configuration

The mapping is defined under `ditto.namespace-policies` in the `policies.conf`, `things.conf`, and `search.conf`
service configuration files (or overridden via a `-dev.conf` / extension config for a given deployment):

```hocon
ditto.namespace-policies {
  # Exact namespace match
  "org.eclipse.ditto.devices" = ["org.eclipse.ditto.devices:devices-root"]

  # Prefix wildcard — applies to any sub-namespace of org.eclipse.ditto
  # Does NOT match org.eclipse.ditto itself (requires at least one sub-segment)
  "org.eclipse.ditto.*" = ["org.eclipse.ditto:tenant-root"]

  # Catch-all — applies to every namespace
  # "*" = ["root:global-policy"]
}
```

Multiple patterns can match a single namespace. When they do, patterns are applied in deterministic precedence
order: exact match first, then prefix wildcards from most specific to least specific, then `"*"`.

Multiple root policy IDs can be listed per pattern. They are applied left-to-right.

#### Pattern syntax

| Pattern | Matches |
|---|---|
| `"org.example.devices"` | Only the exact namespace `org.example.devices` |
| `"org.example.*"` | Any namespace starting with `org.example.` (requires at least one sub-segment) |
| `"*"` | Every namespace |

Unsupported patterns (e.g. `"org.*.devices"` or `"foo*"`) are rejected at startup with a `DittoConfigError`.

#### Behaviour details

**Label conflict — local wins:** If a local policy already has an entry with the same label as a root policy
entry, the local entry is preserved unchanged. The root policy entry is silently skipped for that label.

**Self-referential guard:** A root policy is never merged into itself. If `org.eclipse.ditto:tenant-root` is
configured for `"org.eclipse.ditto.*"`, it is not merged when building the enforcer for `tenant-root` itself.

**Missing root policy:** If a configured root policy does not exist or cannot be loaded, its entries are skipped
and an ERROR is logged. The child policy's enforcer is still built successfully from its own entries.

**Cache invalidation:** When a root policy is modified, Ditto automatically invalidates all cached enforcers for
policies in matching namespaces. This is an O(n) scan over the enforcer cache and happens transparently.
For very large deployments with frequently-changing root policies, consider keeping the root policies stable
and pushing changes via individual child policies instead.

**Search consistency:** Root policy changes are reflected in the things-search index. The search service tracks
root policy revisions as part of the resolved-policy cache key, so a root policy update triggers re-indexing
of all affected things.

#### Helm configuration

For Helm deployments, configure namespace root policies in `values.yaml` via the shared `global` section:

```yaml
global:
  namespacePolicies:
    "org.eclipse.ditto.*":
      - "org.eclipse.ditto:tenant-root"
```

This shared mapping is rendered into the respective service extension config files under `ditto.namespace-policies`.

## Further reading

* [Operating - MongoDB](operating-mongodb.html)
* [Operating - Authentication](operating-authentication.html)
* [Operating - DevOps Commands](operating-devops.html)
* [Operating - Monitoring & Tracing](operating-monitoring.html)
* [Extending Ditto](installation-extending.html)
