---
title: Thing Metadata
keywords: metadata, things, model, semantic
tags: [model]
permalink: basic-metadata.html
---

A Thing in Ditto is also able to store metadata information, e.g. about single 
[feature properties](basic-feature.html#feature-properties), complete features and also [attributes](basic-thing.html#attributes)
or other data stored in a digital twin ([thing](basic-thing.html)).

This metadata can contain additional information which shall not be treated as part of the twin's value, however may
be useful to provide some context of the twin's data.

Metadata has not its own API but can only be modified, retrieved or deleted while modifying the state of a twin as a side effect.<br/>
By default, metadata is not returned on API requests, but must be [asked for explicitly](#reading-metadata-information).

An example is the timestamp when the current value of e.g. a feature property was updated for the last time.
Or the metadata information of a feature property may contain information about its type or its semantics (e.g. a unit 
of measurement).


## Modifying metadata

Modifying arbitrary `metadata` is possible by using the `put-metadata` header 
(e.g. for HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the 
protocol message), see [here for an overview of the available headers](protocol-specification.html#headers).

The value of the `put-metadata` is a JSON array containing JSON objects with `"key"` and `"value"` parts:
* `"key"`: describes the hierarchical position in the Thing where the metadata should be placed
* `"value"`: is an arbitrary JSON value to set as metadata (could also be a nested JSON object)

The `put-metadata` header can be specified at all API levels and will then modify the metadata object at the same level,
relative to the request. For example a `PUT` on a feature property `color` with a `put-metadata` header `"key"`
`/r/changeLog` will only set the metadata for this sub property.

### Example for setting metadata

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
        },
        "status": {
          "on": "true"
        }
      } 
    } 
  }
}
```

In case you want to set metadata on all properties of a feature (`"r"`, `"g"` and `"b"`) plus some 
extra metadata to only set for the `"color"` property.<br/>
The content of the `put-metadata` in order to do that would look like this:

```json
[
  {
    "key": "*/changeLog",
    "value": {
      "changedAt": "2022-08-02T04:30:07",
      "changedBy": {
        "name":"ditto",
        "mail":"ditto@mail.com"
      }
    }
  },
  {
    "key": "/features/lamp/properties/color/description",
    "value": "Color represented with RGB values"
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
          },
          "status": {
            "on": "true"
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
                "changeLog": {
                  "changedAt": "2022-08-02T04:30:07",
                  "changedBy": {
                    "name": "ditto",
                    "mail": "ditto@mail.com"
                  }
                }
              },
              "g": {
                "changeLog": {
                  "changedAt": "2022-08-02T04:30:07",
                  "changedBy": {
                    "name": "ditto",
                    "mail": "ditto@mail.com"
                  }
                }
              },
              "b": {
                "changeLog": {
                  "changedAt": "2022-08-02T04:30:07",
                  "changedBy": {
                    "name": "ditto",
                    "mail": "ditto@mail.com"
                  }
                }
              },
              "description": "Color represented with RGB values"
            }
          }
        }
      }
    }
}
```

Another example for the `put-metadata` header can look like this where we want to add a "description" to both feature properties.

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

The resulting Thing JSON with the previously added metadata would look like this:
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
          },
          "status": {
            "on": "true"
          }
        }
      }
    },
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

### Modifying metadata on all JSON leaves

A special syntax for the key is `*/{key}` which means that all JSON leaves of the modify operation will
get the metadata key `{key}` with the given value. So if, for example, only the affected JSON leaves should 
get the timestamp where the changed values were recorded, one would set the `put-metadata` header as shown in the 
following example: 

```json
[
  {
    "key": "*/changeLog",
    "value": {
      "changedAt": "2022-08-02T04:30:07",
      "changedBy": {
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
        },
        "status": {
          "on": "true"
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
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07",
                "changedBy": {
                  "name": "ditto",
                  "mail": "ditto@mail.com"
                }
              }
            },
            "g": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07",
                "changedBy": {
                  "name": "ditto",
                  "mail": "ditto@mail.com"
                }
              }
            },
            "b": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07",
                "changedBy": {
                  "name": "ditto",
                  "mail": "ditto@mail.com"
                }
              }
            },
            "status": {
              "on": {
                "changeLog": {
                  "changedAt": "2022-08-02T04:30:07", 
                  "changedBy": {
                    "name": "ditto", 
                    "mail": "ditto@mail.com"
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

## Reading metadata information

Metadata of a Thing can be retrieved by querying a full thing, e.g. via the [HTTP API](http-api-doc.html), and 
specifying an (additional) [field selector](httpapi-concepts.html#with-field-selector) `_metadata`, 
e.g.: `?fields=thingId,attributes,_metadata`.

Metadata can also be queried by using the `get-metadata` header
(e.g. for HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the
protocol message), see [here for an overview of the available headers](protocol-specification.html#headers).
The `get-metadata` header expects a comma separated list of metadata `{key}`. This will return the relative metadata 
in the `ditto-metadata` header.

### Example for reading metadata with field selector

For example a `GET` request to 
`https://{ditto-instance}/api/2/things/{namespace}:{name}?fields=thingId,policyId,features,_created,_modified,_revision,_metadata`
will yield the metadata stored for the given Thing, in the following format:

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
        },
        "status": {
          "on": "true"
        }
      }
    }
  },
  "_created": "2022-06-01T10:00:00Z",
  "_modified": "2022-06-09T14:30:00Z",
  "_revision": 42,
  "_metadata": {
    "features": {
      "lamp": {
        "properties": {
          "color": {
            "r": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07", 
                "changedBy": {
                  "name": "ditto", 
                  "mail": "ditto@mail.com"
                }
              }
            },
            "g": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07",
                "changedBy": {
                  "name": "ditto",
                  "mail": "ditto@mail.com"
                }
              }
            },
            "b": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07", 
                "changedBy": {
                  "name": "ditto", 
                  "mail": "ditto@mail.com"
                }
              }
            },
            "description": "Color represented with RGB values"
          },
          "status": {
            "on": {
              "changeLog": {
                "changedAt": "2022-08-02T04:30:07",
                "changedBy": {
                  "name": "ditto",
                  "mail": "ditto@mail.com"
                }
              }
            },
            "description": "Status of the Lamp"
          }
        }
      }
    }
  }
}
```

### Example for reading metadata with header

For example a `GET` request to `https://{ditto-instance}/api/2/things/{namespace}:{name}` with HTTP header `get-metadata`
and value `features/lamp/properties/color/r` will return the following content in the `ditto-metadata` header:

```json
{
  "features": {
    "lamp": {
      "properties": {
        "color": {
          "r": {
            "changeLog": {
              "changedAt": "2022-08-02T04:30:07",
              "changedBy": {
                "name": "ditto",
                "mail": "ditto@mail.com"
              }
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
`features/lamp/properties/*/description` will return:

```json
{
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
```

## Deleting metadata information

Metadata can be deleted by using the `delete-metadata` header with modifying requests (`PUT` or `PATCH`).
For HTTP requests, set it as HTTP header, for Ditto Protocol requests, put it in the `"headers"` section of the
protocol message, see [here for an overview of the available headers](protocol-specification.html#headers).
The `delete-metadata` header expects a comma separated list of metadata `{key}`.

For example a `PATCH` request to `https://{ditto-instance}/api/2/things/{namespace}:{name}` with HTTP header `delete-metadata`
and value `features/lamp/properties/color` will remove the complete `color` property from the thing metadata.

<b>Note:</b> When deleting things or parts of a thing, like feature properties or attributes, their relative metadata 
is also deleted.

## Wildcard usage for metadata requests
 
When working with metadata there are some wildcards which can be used to modify, retrieve or delete metadata.
The following table gives an overview which Wildcards can be used on top-level for what requests.

| Wildcard                                | PUT/PATCH                                                              | GET                                                                          | DELETE                                                                     |
|-----------------------------------------|------------------------------------------------------------------------|------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `*`                                     | x                                                                      | retrieve all metadata relative to the path                                   | delete all metadata relative to the path                                   |
| `*/key`                                 | add metadata for the given `key` to all JSON leaves                    | retrieve all metadata with `key`                                             | delete all metadata with `key`                                             |
| `attributes/*/key`                      | add metadata for the given `key` to all attributes                     | retrieve metadata for given `key` from all attributes                        | delete metadata for given `key` from all attributes                        |
| `features/*/properties/*/key`           | add metadata for the given `key` to all feature properties             | retrieve metadata for given `key` from all feature properties                | delete metadata for given `key` from all feature properties                |
| `features/*/properties/{property}/key`  | add metadata for the given `key` to all features with a given property | retrieve metadata for given `key` from all features with a specific property | delete metadata for given `key` from all features with a specific property |
| `features/{feature}/properties/*/key`   | add metadata for the given `key` to all properties of a feature        | retrieve metadata for given `key` from all properties of a feature           | delete metadata for  given `key` from all properties of a feature          |

Wildcards can also be used on the /features resource:

| Wildcard                      | PUT/PATCH                                                              | GET                                                                          | DELETE                                                                     |
|-------------------------------|------------------------------------------------------------------------|------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `*/properties/*/key`          | add metadata for the given `key` to all feature properties             | retrieve metadata for given `key` from all feature properties                | delete metadata for given `key` from all feature properties                |
| `*/properties/{property}/key` | add metadata for the given `key` to all features with a given property | retrieve metadata for given `key` from all features with a specific property | delete metadata for given `key` from all features with a specific property |
| `{feature}/properties/*/key`  | add metadata for the given `key` to all properties of a feature        | retrieve metadata for given `key` from all properties of a feature           | delete metadata for  given `key` from all properties of a feature          |

<b>Note:</b> With the `*` wildcard it is only possible to skip one level in the resource path. You can not use the wildcard `*` other than in the examples above.

## Multiple metadata header
It is not possible to use multiple metadata headers in one request. For GET requests it is only possible to use the `get-metadata`header.
For PUT/PATCH requests it is only possible to use either the `put-metadata` or `delete-metadata` header.
In case multiple headers are used in a request an exception will be the result.