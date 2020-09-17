---
title: Metadata Support
keywords: metadata, things
tags: [model]
permalink: basic-metadata.html
---

Besides [features](basic-feature.html) a Thing in Ditto is also able to store Metadata information. 
These informations can be additional information for describing specific features or properties of the thing.
A very simple example is the timestamp when the current value of the feature or property was set.

Metadata can be manipulated by using the `metadata` header, see [here](protocol-specification.html#headers) for more information.

## Predefined Metadata 

Currently, there are no pre-defined Metadata keys.

<!--
Right now, the only fixed pre-defined Metadata key is `issuedAt`.

### The `issuedAt` Metadata key
 
The Metadata key `issuedAt` describes the timestamp when the current value of a feature property was _recorded_ (_not when it was set_).
This means, that a client _can_ send this value by using the `metadata` header. If the client does not send
this header the `issuedAt` field will be set by Ditto with the timestamp when the value was written.
-->

## Setting custom Metadata

The only way to set arbitrary `Metadata` is by using the `put-metadata` header, see [here](protocol-specification.html#headers) for more information.
The Format of the Json Array is 

```
[
  {
    "key":"/features/lamp/properties/color/r",
    "value":{"issuedAt":someTimestamp,"issuedBy":{"name":"me","mail":"me@mail.com"}}
  },
  {
    "key":"*/foo",
    "value": "bar"
  },
  ...
]
```

where `key` describes the hierarchical position in the Thing where the metadata is placed and 
`value` is a map of Metadata keys to their respective values.

A special Syntax for the key is `*/{key}` which means that all affected Json Leafs of the Modify Operation will
get the Metadata Key `{key}` with the given value. So if, for example, only the affected Json Leafes should 
get the timestamp where the changed values were recorded, one would set the `metadata` header as shown in the
following example: 

```json
[
  {
    "key":"*/timestamp",
    "value": someTimestamp
  }
]
```

## Reading Metadata Information

Currently, the only way to retrieve stored Metadata is by a full thing query, e.g. via the [HTTP API](http-api-doc.html).
But, `get-metadata` has to be added to the queried fields together with `thingId,attributes,_metadata`.

For example a `GET` request to `https://{ditto-instance}/api/2/things/{namespace}:{thing}?fields=thingId,attributes,_metadata`
will yield the Metadata stored for the given Thing, in the following format:

```json
{
  "thingId": "org.eclipse.ditto:thing-1",
  "policyId": "...",
  "features": {
    "lamp": {
      "properties": {
        "on": true,
        "color": {
          "r": 0,
          "g": 255,          
          "b": 255,
        }
      }
    }
  },
  "_created": "2020-06-01T10:00:00Z",
  "_modified": "2020-06-09T14:30:00Z",
  "_revision": 42,
  "_metadata": {
    "features": {
      "lamp": {
        "properties": {
          "on": {
            "issuedAt": "2020-06-09T14:30:00Z"
          },
          "color": {
            "r": {
              "issuedAt": "2020-06-09T14:15:00Z"
            },
            "g": {
              "issuedAt": "2020-06-09T14:15:00Z"
            },
            "b": {
              "issuedAt": "2020-06-09T14:15:00Z"
            }
          }
        }
      }
    }
  }
}
``` 