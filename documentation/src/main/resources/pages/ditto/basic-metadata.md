---
title: Metadata
keywords: metadata, things, model, semantic
tags: [model]
permalink: basic-metadata.html
---

Metadata lets you attach contextual information to any part of a Thing -- for example, recording
when a value was last updated, who changed it, or what unit of measurement it uses.

{% include callout.html content="**TL;DR**: Metadata is extra information attached to Thing attributes and feature
properties. You set it via the `put-metadata` header, read it via field selectors or the `get-metadata`
header, and delete it via the `delete-metadata` header." type="primary" %}

## What is metadata for?

Metadata provides context about your twin's data without becoming part of the data itself. Common
use cases include:

* **Timestamps** -- when a property was last updated
* **Audit trails** -- who changed a value
* **Semantic annotations** -- units of measurement, data type descriptions
* **Change tracking** -- recording the source system that provided a value

Metadata is **not** returned by default. You must request it explicitly.

## How it works

Metadata mirrors the structure of the Thing it describes. If your Thing has a property at
`features/lamp/properties/color/r`, the metadata for that property lives at the same path
inside the `_metadata` object.

Metadata has no dedicated API endpoint. Instead, you modify, read, and delete it using HTTP headers
as a side effect of regular Thing operations.

## Setting metadata

Use the `put-metadata` header on any modifying request (`PUT`, `PATCH`, or via the Ditto Protocol
`"headers"` section). The value is a JSON array of objects, each with a `"key"` and `"value"`:

```json
[
  {
    "key": "/features/lamp/properties/color/description",
    "value": "Color represented with RGB values"
  },
  {
    "key": "/features/lamp/properties/status/description",
    "value": "Status of the lamp"
  }
]
```

### Example: setting metadata on a Thing update

When you update your twin's lamp color:

```json
{
  "thingId": "org.eclipse.ditto:my-lamp-1",
  "features": {
    "lamp": {
      "properties": {
        "color": { "r": 100, "g": 0, "b": 255 },
        "status": { "on": "true" }
      }
    }
  }
}
```

Send the `put-metadata` header with a description for each property group:

```json
[
  {
    "key": "features/lamp/properties/color/description",
    "value": "Color represented with RGB values"
  },
  {
    "key": "features/lamp/properties/status/description",
    "value": "Status of the lamp"
  }
]
```

The resulting `_metadata` on the Thing:

```json
{
  "_metadata": {
    "features": {
      "lamp": {
        "properties": {
          "color": {
            "description": "Color represented with RGB values"
          },
          "status": {
            "description": "Status of the lamp"
          }
        }
      }
    }
  }
}
```

### Setting metadata on all JSON leaves

Use the special key syntax `*/{key}` to set the same metadata on every leaf value affected by your
update. For example, to record a change log on all modified properties:

```json
[
  {
    "key": "*/changeLog",
    "value": {
      "changedAt": "2022-08-02T04:30:07",
      "changedBy": { "name": "ditto", "mail": "ditto@mail.com" }
    }
  }
]
```

This adds a `changeLog` entry to each individual leaf property (`r`, `g`, `b`, `on`) rather than
to the parent objects.

## Reading metadata

You have two ways to retrieve metadata.

### Using field selectors

Add `_metadata` to the `fields` query parameter:

```bash
GET /api/2/things/org.eclipse.ditto:my-lamp-1?fields=thingId,features,_metadata
```

This returns the Thing along with its full `_metadata` object.

### Using the get-metadata header

Set the `get-metadata` HTTP header to a comma-separated list of metadata paths. Ditto returns
the matching metadata in the `ditto-metadata` response header.

For example, to get metadata for a specific property:

```
get-metadata: features/lamp/properties/color/r
```

You can use wildcards to expand one level. For example,
`features/lamp/properties/*/description` returns the `description` metadata for all properties
of the `lamp` feature.

## Deleting metadata

Use the `delete-metadata` header on a modifying request (`PUT` or `PATCH`). Provide a
comma-separated list of metadata paths to remove.

For example, to remove all color metadata:

```
delete-metadata: features/lamp/properties/color
```

When you delete a Thing or part of a Thing (like a feature or attribute), the associated metadata
is also deleted.

## Wildcard reference

| Wildcard | PUT/PATCH | GET | DELETE |
|----------|-----------|-----|--------|
| `*` | -- | Retrieve all metadata relative to the path | Delete all metadata relative to the path |
| `*/key` | Add `key` metadata to all JSON leaves | Retrieve all metadata with `key` | Delete all metadata with `key` |
| `attributes/*/key` | Add `key` to all attributes | Retrieve `key` from all attributes | Delete `key` from all attributes |
| `features/*/properties/*/key` | Add `key` to all feature properties | Retrieve `key` from all feature properties | Delete `key` from all feature properties |
| `features/*/properties/{prop}/key` | Add `key` to `{prop}` in all features | Retrieve `key` from `{prop}` in all features | Delete `key` from `{prop}` in all features |
| `features/{feat}/properties/*/key` | Add `key` to all properties of `{feat}` | Retrieve `key` from all properties of `{feat}` | Delete `key` from all properties of `{feat}` |

The same wildcards work at the `/features` resource level, using relative paths
(`*/properties/*/key`, `{feature}/properties/*/key`, etc.).

The `*` wildcard skips exactly one level in the path. It cannot be used in other positions.

## Constraints

You can use only one metadata header per request:
* For GET requests: `get-metadata` only
* For PUT/PATCH requests: either `put-metadata` or `delete-metadata`, but not both

Using multiple metadata headers in one request results in an error.

## Further reading

* [Protocol headers reference](protocol-specification.html#headers) -- all available Ditto headers
* [Things](basic-thing.html) -- the entity that metadata attaches to
* [HTTP API concepts](httpapi-concepts.html#field-selectors) -- field selectors for partial
  retrieval
