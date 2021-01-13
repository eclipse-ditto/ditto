---
title: Thing Metadata
keywords: metadata, things, model, semantic
tags: [model]
permalink: basic-metadata.html
---

A Thing in Ditto is also able to store Metadata information, e.g. about single 
[feature properties](basic-feature.html#feature-properties), complete features and also [attributes](basic-thing.html#attributes)
or other data stored in a digital twin ([thing](basic-thing.html)).

This metadata can contain additionally information which shall not be treated as part of the twin's value, however may
be useful to provide some context of the twin's data.

Metadata has not its own API but can only be updated/set while modifying the state of a twin as a side effect.<br/>
By default, metadata is not returned at API requests, but must be [asked for explicitly](#reading-metadata-information).

An example is the timestamp when the current value of e.g. a feature property was updated for the last time.
Or the metadata information of a feature property may contain information about its type or its semantics (e.g. a unit 
of measurement).


## Setting Metadata

Setting arbitrary `Metadata` is possible by using the `put-metadata` header 
(e.g. for HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the 
protocol message), see [here for an overview of the available headers](protocol-specification.html#headers).

The value of the `put-metadata` is a JSON array containing JSON objects with `"key"` and `"value"` parts:
* `"key"`: describes the hierarchical position in the Thing where the metadata should be placed
* `"value"`: is an arbitrary JSON value to set as metadata (could also be a nested JSON object)

### Example for setting Metadata

Assume you modify your twin's lamp color with a call: 
```json
{
  "thingId": "org.eclipse.ditto:my-lamp-1",
  "features": {
    "lamp": {
      "properties": {
        "color": {
          "r": 100,
          "g": 0,
          "b": 255
        }     
      } 
    } 
  }
}
```

You want to specify to set metadata which affects all the changed properties (`"r"`, `"g"` and `"b"`) plus some 
extra metadata to only set for the `"r"` property.<br/>
The content of the `put-metadata` in order to do that would look like this:

```json
[
  {
    "key": "*/foo",
    "value": "bar"
  },
  {
    "key": "/features/lamp/properties/color/r",
    "value": {
      "foo": "bumlux",
      "issuedAt": "someTimestamp",
      "issuedBy": {
        "name":"ditto",
        "mail":"ditto@mail.com"
      }
    }
  }
]
```

The resulting Thing JSON including its `_metadata` would look like this:
```json
{
    "thingId": "org.eclipse.ditto:my-lamp-1",
    "features": {
        "lamp": {
            "properties": {
                "color": {
                    "r": 100,
                    "g": 0,
                    "b": 255
                }
            }
        }
    },
    "_metadata": {
        "thingId" : {
          "foo": "bar"
        },
        "policyId" : {
          "foo": "bar"
        },
        "features": {
            "lamp": {
                "properties": {
                    "color": {
                        "r": {
                            "foo": "bumlux",
                            "issuedAt": "someTimestamp",
                            "issuedBy": {
                                "name": "me",
                                "mail": "me@mail.com"
                            }
                        },
                        "g": {
                            "foo": "bar"
                        },
                        "b": {
                            "foo": "bar"
                        }
                    }
                }
            }
        }
    }
}
```


### Setting Metadata to all affected JSON leaves

A special syntax for the key is `*/{key}` which means that all affected JSON leaves of the modify operation will
get the Metadata key `{key}` with the given value. So if, for example, only the affected JSON leaves should 
get the timestamp where the changed values were recorded, one would set the `put-metadata` header as shown in the 
following example: 

```json
[
  {
    "key": "*/issuedAt",
    "value": "someTimestamp"
  }
]
```


## Reading Metadata information

Metadata of a Thing can be retrieved is by querying a full thing, e.g. via the [HTTP API](http-api-doc.html), and 
specifying an (additional) [field selector](httpapi-concepts.html#with-field-selector) `_metadata`, 
e.g.: `?fields=thingId,attributes,_metadata`.

### Example for reading Metadata

For example a `GET` request to 
`https://{ditto-instance}/api/2/things/{namespace}:{name}?fields=thingId,policyId,features,_created,_modified,_revision,_metadata`
will yield the Metadata stored for the given Thing, in the following format:

```json
{
  "thingId": "org.eclipse.ditto:my-lamp-1",
  "policyId": "...",
  "features": {
    "lamp": {
      "properties": {
        "on": true,
        "color": {
          "r": 0,
          "g": 255,          
          "b": 255
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
