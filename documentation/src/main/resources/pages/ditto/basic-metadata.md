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

Metadata has not its own API but can only be modified, retrieved or deleted while modifying the state of a twin as a side effect.<br/>
By default, metadata is not returned at API requests, but must be [asked for explicitly](#reading-metadata-information).

An example is the timestamp when the current value of e.g. a feature property was updated for the last time.
Or the metadata information of a feature property may contain information about its type or its semantics (e.g. a unit 
of measurement).


## Modifying Metadata

Modifying arbitrary `Metadata` is possible by using the `put-metadata` header 
(e.g. for HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the 
protocol message), see [here for an overview of the available headers](protocol-specification.html#headers).

The value of the `put-metadata` is a JSON array containing JSON objects with `"key"` and `"value"` parts:
* `"key"`: describes the hierarchical position in the Thing where the metadata should be placed
* `"value"`: is an arbitrary JSON value to set as metadata (could also be a nested JSON object)

The `put-metadata` header can be specified at all API levels and will then modify the metadata object at the same level,
relative to the request. For example a `PUT` on a feature property `color` with a `put-metadata` header `"key"`
`/r/issuedAt` will only set the metadata for this sub property.

### Example for setting Metadata

Assuming you modify your twin's lamp color with a call: 
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
    "key": "/features/lamp/properties/color/r",
    "value": {
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
        "features": {
            "lamp": {
                "properties": {
                    "color": {
                        "r": {
                            "issuedAt": "someTimestamp",
                            "issuedBy": {
                                "name": "me",
                                "mail": "me@mail.com"
                            }
                        }
                    }
                }
            }
        }
    }
}
```


### Modifying Metadata on all JSON leaves

A special syntax for the key is `*/{key}` which means that all JSON leaves of the modify operation will
get the Metadata key `{key}` with the given value. So if, for example, only the affected JSON leaves should 
get the timestamp where the changed values were recorded, one would set the `put-metadata` header as shown in the 
following example: 

```json
[
  {
    "key": "*/issuedAt",
    "value": {
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
    "features": {
      "lamp": {
        "properties": {
          "color": {
            "r": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            },
            "g": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            },
            "b": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            }
          }
        }
      }
    }
  }
}
```

## Reading Metadata information

Metadata of a Thing can be retrieved is by querying a full thing, e.g. via the [HTTP API](http-api-doc.html), and 
specifying an (additional) [field selector](httpapi-concepts.html#with-field-selector) `_metadata`, 
e.g.: `?fields=thingId,attributes,_metadata`.

Metadata can also be queried by using the `get-metadata` header
(e.g. for HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the
protocol message), see [here for an overview of the available headers](protocol-specification.html#headers).
The `get-metadata` header expects a comma separated list of metadata `{key}`. This will return the relative metadata of
in the header `ditto-metadata`.

### Example for reading Metadata with field selector

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
          "color": {
            "r": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            },
            "g": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            },
            "b": {
              "issuedAt": "someTimestamp",
              "issuedBy": {
                "name": "me",
                "mail": "me@mail.com"
              }
            }
          }
        }
      }
    }
  }
}
```

### Example for reading Metadata with header

For example a `GET` request to `https://{ditto-instance}/api/2/things/{namespace}:{name}` with HTTP header `get-metadata`
and value `features/lamp/properties/color/r` will return:

```json
{
  "features": {
    "lamp": {
      "properties": {
        "color": {
          "r": {
            "issuedAt": "someTimestamp",
            "issuedBy": {
              "name": "me",
              "mail": "me@mail.com"
            }
          }
        }
      }
    }
  }
}
```

Metadata can also be queried using a wildcard which expands the level where it is specified, e.g. a `GET` request
to `https://{ditto-instance}/api/2/things/{namespace}:{name}` with HTTP header `get-metadata` and value
`features/lamp/properties/color/*/issuedAt` will return:

```json
{
  "features": {
    "lamp": {
      "properties": {
        "color": {
          "r": {
            "issuedAt": "someTimestamp"
          },
          "g": {
            "issuedAt": "someTimestamp"
          },
          "b": {
            "issuedAt": "someTimestamp"
          }
        }
      }
    }
  }
}
```

## Deleting Metadata information

Metadata can be deleted by using the `delete-metadata` header with modifying requests (`PUT` or `PATCH`).
For HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the
protocol message, see [here for an overview of the available headers](protocol-specification.html#headers).
The `delete-metadata` header expects a comma separated list of metadata `{key}`.

For example a `PATCH` request to `https://{ditto-instance}/api/2/things/{namespace}:{name}` with HTTP header `delete-metadata`
and value `features/lamp/properties/color` will remove the complete `color` property from the thing metadata.

When deleting things or parts of a thing like feature properties or attributes, their relative metadata is also deleted.
