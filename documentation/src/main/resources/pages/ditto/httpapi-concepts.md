---
title: HTTP API Concepts
keywords: http, api, concepts, partial, conditional, optimistic locking, ETag, If-Match, If-None-Match
tags: [http]
permalink: httpapi-concepts.html
---

The Ditto HTTP API follows REST conventions and maps the JSON structure of digital twins directly to API endpoints, giving you fine-grained access to every piece of twin data.

{% include callout.html content="**TL;DR**: The HTTP API auto-generates endpoints from your Thing's JSON structure, supports partial reads/writes, merge updates via `PATCH`, and conditional requests with ETags for optimistic locking." type="primary" %}

## Overview

The HTTP API entry point is:

```
http://localhost:8080/api/<apiVersion>
```

Explore the full API interactively in the [HTTP API documentation](http-api-doc.html).

## API versioning

Ditto versions its HTTP API in the URL path: `/api/<apiVersion>`. Currently, only API version `2` is available (version 1 was removed in Ditto 2.0.0).

The version guarantee means that no existing HTTP resources or JSON structures change in breaking ways within the same API version. In API 2, each [Thing](basic-thing.html) references a [Policy](basic-policy.html) for authorization.

## Endpoints

The HTTP API has two types of endpoints: **static** endpoints that mirror the Thing/Policy/Connection data model, and **dynamic** endpoints that Ditto generates automatically from your Thing's JSON structure.

### Static endpoints

#### `/things` endpoints

A `Thing` in API 2 has this JSON structure:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {},
  "features": {}
}
```

This maps to these API endpoints:

* `/things/{thingId}` -- the complete Thing
* `/things/{thingId}/policyId` -- the Policy ID
* `/things/{thingId}/definition` -- the Thing definition
* `/things/{thingId}/attributes` -- all attributes
* `/things/{thingId}/features` -- all features

#### `/policies` endpoints

A `Policy` has this JSON structure:

```json
{
  "policyId": "{policyId}",
  "entries": {
    "{entryLabel}": {
      "subjects": { "{subjectId}": {} },
      "resources": { "{resource}": {} }
    }
  }
}
```

This maps to:

* `/policies/{policyId}` -- the complete `Policy`
* `/policies/{policyId}/entries` -- all `Policy` entries
* `/policies/{policyId}/entries/{entryLabel}` -- a single entry
* `/policies/{policyId}/entries/{entryLabel}/subjects` -- subjects of an entry
* `/policies/{policyId}/entries/{entryLabel}/resources` -- resources of an entry

#### `/connections` endpoints

* `/connections` -- list all connections or create a new one
* `/connections/{connectionId}` -- a specific connection
* `/connections/{connectionId}/command` -- send a command
* `/connections/{connectionId}/status` -- retrieve status
* `/connections/{connectionId}/metrics` -- retrieve metrics
* `/connections/{connectionId}/logs` -- retrieve logs

### Dynamic endpoints

{% include note.html content="This automatically turns each small aspect of a **digital twin** into an API endpoint." %}

Ditto generates additional endpoints based on your `Thing`'s actual JSON content. For example, given this `Thing`:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": { "some": false, "serialNo": 4711 }
  },
  "features": {
    "lamp": {
      "properties": { "on": false, "color": "blue" }
    }
  }
}
```

These additional endpoints become available automatically:

* `/things/{thingId}/attributes/manufacturer`
* `/things/{thingId}/attributes/complex`
* `/things/{thingId}/attributes/complex/some`
* `/things/{thingId}/attributes/complex/serialNo`
* `/things/{thingId}/features/lamp`
* `/things/{thingId}/features/lamp/properties`
* `/things/{thingId}/features/lamp/properties/on`
* `/things/{thingId}/features/lamp/properties/color`

### Migrate Thing definitions

Use `POST /things/{thingId}/migrateDefinition` to migrate a Thing to a new model version, optionally updating attributes and features.

```json
{
  "thingDefinitionUrl": "https://models.example.com/thing-definition-1.0.0.tm.jsonld",
  "migrationPayload": {
    "attributes": {
      "manufacturer": "New Corp",
      "location": "Berlin, main floor"
    },
    "features": {
      "thermostat": {
        "properties": {
          "status": {
            "temperature": { "value": 23.5, "unit": "DEGREE_CELSIUS" }
          }
        }
      }
    }
  },
  "patchConditions": {
    "thing:/features/thermostat": "not(exists(/features/thermostat))"
  },
  "initializeMissingPropertiesFromDefaults": true
}
```

String values in `migrationPayload` may use the [thing-json placeholder](basic-placeholders.html#thing-json-placeholder) (brace or legacy). When the value is exactly one such placeholder (no pipeline), the resolved value preserves its JSON type. Pipelines and multiple placeholders yield a string. Resolution uses the existing Thing. Missing paths cause the request to fail.

## Partial updates

Because every JSON element has its own endpoint, you can update individual values without touching the rest of the `Thing`. This reduces payload size and prevents accidentally overwriting data with stale values.

For example, to change the `on` property of `lamp` to `true`, instead of replacing the entire
Thing with `PUT .../things/{thingId}` and the full JSON body:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": { "some": false, "serialNo": 4711 }
  },
  "features": {
    "lamp": {
      "properties": { "on": true, "color": "blue" }
    }
  }
}
```

You target only the changed value:

`PUT .../things/{thingId}/features/lamp/properties/on`

```json
true
```

## Partial requests

You can also read individual values instead of the full `Thing`:

`GET .../things/{thingId}/features/lamp/properties/on`

```json
true
```

### Field selectors

Use the `fields` query parameter to retrieve specific fields while preserving the JSON structure.
Pass a comma-separated list of field paths. You can also use `*` as a feature ID wildcard to
retrieve a property across multiple features.

Given this Thing:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": { "some": false, "serialNo": 4711, "misc": "foo" }
  },
  "features": {
    "lamp": {
      "properties": { "on": true, "color": "blue" }
    },
    "infrared-lamp": {
      "properties": { "on": false, "color": "red" }
    }
  }
}
```

#### Field selector examples

`GET .../things/{thingId}?fields=attributes`

```json
{
  "attributes": {
    "manufacturer": "ACME corp",
    "complex": { "some": false, "serialNo": 4711, "misc": "foo" }
  }
}
```

`GET .../things/{thingId}?fields=attributes/manufacturer`

```json
{
  "attributes": {
    "manufacturer": "ACME corp"
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/serialNo`

```json
{
  "attributes": {
    "complex": {
      "serialNo": 4711
    }
  }
}
```

`GET .../things/{thingId}?fields=attributes/complex/some,attributes/complex/serialNo`

```json
{
  "attributes": {
    "complex": { "some": false, "serialNo": 4711 }
  }
}
```

The same result using parentheses to group fields under the same parent:

`GET .../things/{thingId}?fields=attributes/complex(some,serialNo)`

```json
{
  "attributes": {
    "complex": { "some": false, "serialNo": 4711 }
  }
}
```

Selecting fields from different branches of the JSON:

`GET .../things/{thingId}?fields=attributes/complex/misc,features/lamp/properties/on`

```json
{
  "attributes": {
    "complex": { "misc": "foo" }
  },
  "features": {
    "lamp": {
      "properties": { "on": true }
    }
  }
}
```

#### Wildcard field selectors

Use `*` as a feature ID wildcard to retrieve a property across all features:

`GET .../things/{thingId}?fields=features/*/properties/on`

```json
{
  "features": {
    "lamp": { "properties": { "on": true } },
    "infrared-lamp": { "properties": { "on": false } }
  }
}
```

## Merge updates

Use `PATCH` with [JSON Merge Patch (RFC 7396)](https://tools.ietf.org/html/rfc7396) to update multiple parts of a Thing in a single request. Set the content type to `application/merge-patch+json`.

The merge patch rules are:
* New members are added
* Existing members are replaced with the new value
* Members set to `null` are removed

{% include note.html content="Please note the special meaning of `null` values. When using `PATCH` a `null` value is
interpreted as delete in contrast to `PUT` requests where `null` values have no special meaning. " %}

You can apply `PATCH` at any level of the JSON structure.

### Removing fields with a regex

{% include note.html content="This is an addition to the JSON merge patch (RFC-7396), enhancing using `null` values
    for deleting certain parts of JSON objects specified with a regular expression before applying new fields to it." %}

Ditto extends RFC 7396 with regex-based field removal. Use the syntax `{%raw%}{{ ~<regex>~ }}{%endraw%}` with a `null` value to delete matching keys before applying the rest of the patch:

```json
{
  "features": {
    "aggregated-history": {
      "properties": {
        "{%raw%}{{ ~2022-.*~ }}{%endraw%}": null,
        "2023-03": 105.21
      }
    }
  }
}
```

This removes all keys matching `2022-.*` from `properties`, then adds the new value. The recommended delimiter is `~`. The `/` delimiter is also supported but discouraged due to its special meaning in HTTP paths.

### Merge update permissions

You need `WRITE` permission on **all** resources affected by the merge patch. If permission is missing for any affected resource, the entire patch is rejected.

### Merge update example

Given an existing Thing with this JSON:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
    "location": { "longitude": 47.682170, "latitude": 9.386372 },
    "serialNo": "0000000"
  },
  "features": {
    "temperature": {
      "properties": { "value": 25.43, "unit": "°C" }
    },
    "pressure": {
      "properties": { "value": 1013.25, "unit": "hPa" }
    }
  }
}
```

A single `PATCH .../things/{thingId}` can add, update, and remove multiple fields:

```json
{
  "attributes": {
    "location": null,
    "manufacturer": "Bosch",
    "serialNo": "23091861"
  },
  "features": {
    "temperature": {
      "properties": { "value": 26.89 }
    },
    "pressure": {
      "properties": { "unit": null }
    },
    "humidity": {
      "properties": { "value": 55, "unit": "%" }
    }
  }
}
```

The resulting Thing after applying the patch:

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "attributes": {
    "manufacturer": "Bosch",
    "serialNo": "23091861"
  },
  "features": {
    "temperature": {
      "properties": { "value": 26.89, "unit": "°C" }
    },
    "pressure": {
      "properties": { "value": 1013.25 }
    },
    "humidity": {
      "properties": { "value": 55, "unit": "%" }
    }
  }
}
```

This patch removes `location`, adds `manufacturer`, updates `serialNo`, changes the temperature
value, removes the pressure unit, and adds a new `humidity` feature -- all in one request.

## Conditional requests

The HTTP API for `Things` and `Policies` partially supports [Conditional Requests (RFC 7232)](https://tools.ietf.org/html/rfc7232).

### ETag

Successful responses include an `ETag` header:

* **Top-level resources** (e.g., `.../things/{thingId}`): `"rev:<revision>"` (e.g., `"rev:2"`)
* **Sub-resources** (e.g., `.../things/{thingId}/features/{featureId}`): `"hash:<calculated-hash>"`

### Conditional headers

| Header | Behavior |
|--------|----------|
| `If-Match` | Proceed only if the current entity-tag matches. Use `*` to require the entity exists. Returns `412` on mismatch. |
| `If-None-Match` | Proceed only if the current entity-tag does NOT match. Use `*` to require the entity does NOT exist. Returns `412` for writes or `304` for reads on mismatch. |
| `if-equal` | Controls update behavior: `update` (default) always updates; `skip` returns `412` if the entity is unchanged; `skip-minimizing-merge` does the same but also reduces merge commands to only changed fields. |

The `skip-minimizing-merge` option for `if-equal` is particularly useful for reducing MongoDB storage and event payload when redundant data is sent frequently.

Ditto always provides strong entity-tags. See [Conditional Requests](basic-conditional-requests.html) for using the `condition` header with RQL expressions.

### Exempted fields

When querying a Thing with:

```
GET .../things/{thingId}?fields=_policy
```

you get the Thing with its associated policy. If you modify the associated policy, the Thing's
revision does not change, so `If-None-Match` checks based on the Thing's ETag would not detect
the policy change. Ditto exempts `_policy` from precondition checks to prevent inconsistencies.

### Examples

#### Create only if the Thing does not exist

```
PUT .../things/{thingId}
If-None-Match: *
```

```json
{
  "policyId": "{policyId}",
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

* `201 Created` -- the Thing was created successfully
* `412 Precondition Failed` -- a Thing with this ID already exists

#### Update only if the Thing exists

```
PUT .../things/{thingId}
If-Match: *
```

```json
{
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

* `204 No Content` -- the Thing was updated successfully
* `412 Precondition Failed` -- the Thing does not exist

#### Optimistic locking

First, retrieve the Thing and its ETag:

`GET .../things/{thingId}`:

Response:

`ETag: "rev:2"`

```json
{
  "thingId": "{thingId}",
  "policyId": "{policyId}",
  "definition": "{definition}",
  "attributes": {
    "manufacturer": "ACME crop",
    "otherData": 4711
  }
}
```

Then update with the ETag in `If-Match` to fix the typo without overwriting concurrent changes:

```
PUT .../things/{thingId}
If-Match: "rev:2"
```

```json
{
  "attributes": {
    "manufacturer": "ACME corp",
    "otherData": 4711
  }
}
```

* `204 No Content` -- no one else changed the Thing since your `GET`
* `412 Precondition Failed` -- someone else modified the Thing in the meantime

## Further reading

* [HTTP API documentation](http-api-doc.html) -- interactive API reference
* [HTTP API search](httpapi-search.html) -- querying Things with RQL
* [HTTP API messages](httpapi-messages.html) -- sending messages to/from Things
* [Conditional requests](basic-conditional-requests.html) -- RQL-based conditions
