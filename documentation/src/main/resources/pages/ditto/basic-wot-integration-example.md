---
title: WoT integration example
keywords: WoT, TD, TM, ThingDescription, ThingModel, W3C, Semantic, Model, definition, ThingDefinition, FeatureDefinition, example
tags: [wot]
permalink: basic-wot-integration-example.html
---

Wrapping up the [WoT integration](basic-wot-integration.html) with a practical example.

{% include tip.html content="To experiment with Thing Models and having them exposed as HTTP resources, simply create them as a [GitHub Gist](https://gist.github.com).<br/>
    Each revision of the file will get a unique HTTP endpoint which you can use as endpoint for your Thing Model." %}

## Thing Model

You can provide a WoT Thing Model via any HTTP(s) URL addressable endpoint, for example simply put your WoT TMs into
a GitHub repository.  
For this example, Ditto added a model into its `ditto-examples` GitHub Repo:  
[floor-lamp-1.0.0.tm.jsonld](https://github.com/eclipse-ditto/ditto-examples/blob/master/wot/models/floor-lamp-1.0.0.tm.jsonld)

This file is available as HTTP served file at:  
[https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld)

The example model is composed of the following submodels:
* [dimmable-colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld), instanceName: "Spot1"
    * which `tm:extends` [colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld)
        * which `tm:extends` [switchable-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld)
* [dimmable-colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld), instanceName: "Spot2"
    * which `tm:extends` [colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld)
        * which `tm:extends` [switchable-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld)
* [dimmable-colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld), instanceName: "Spot3"
    * which `tm:extends` [colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld)
        * which `tm:extends` [switchable-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld)
* [connection-status-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/connection-status-1.0.0.tm.jsonld), instanceName: "ConnectionStatus"
* [power-consumption-aware-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/power-consumption-aware-1.0.0.tm.jsonld), instanceName: "PowerConsumptionAwareness"
* [smoke-detector-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/smoke-detector-1.0.0.tm.jsonld), instanceName: "SmokeDetection"
* [colored-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld), instanceName: "Status-LED"
    * which `tm:extends` [switchable-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld)

Summarizing, our example model is the model of a "Floor Lamp" with:
* 3 (dimmable, colored, switchable) Spots
* a connection status indicating whether the lamp is currently connected
* awareness of its current power consumption
* an included smoke detector
* and a (colored, switchable) status LED


## Creating a new Thing based on the TM

To create a Thing (instance) and create the Thing JSON skeleton following the WoT Thing Model, simply create 
a Thing via the Ditto HTTP API (e.g. `PUT /api/2/things/<thingId>`):

```bash
curl --location --request PUT -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
--header 'Content-Type: application/json' \
--data-raw '{
    "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld"
}'
```

That should result in an HTTP status code `201` (Created) and return the following body:
```json
{
  "thingId": "io.eclipseprojects.ditto:floor-lamp-0815",
  "policyId": "io.eclipseprojects.ditto:floor-lamp-0815",
  "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
  "attributes": {
    "manufacturer": "",
    "serialNo": ""
  },
  "features": {
    "Spot1": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
      ],
      "properties": {
        "dimmer-level": 0.0,
        "color": {
          "r": 0,
          "g": 0,
          "b": 0
        },
        "on": false
      }
    },
    "Spot2": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
      ],
      "properties": {
        "dimmer-level": 0.0,
        "color": {
          "r": 0,
          "g": 0,
          "b": 0
        },
        "on": false
      }
    },
    "Spot3": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
      ],
      "properties": {
        "dimmer-level": 0.0,
        "color": {
          "r": 0,
          "g": 0,
          "b": 0
        },
        "on": false
      }
    },
    "ConnectionStatus": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/connection-status-1.0.0.tm.jsonld"
      ],
      "properties": {
        "readySince": "",
        "readyUntil": ""
      }
    },
    "PowerConsumptionAwareness": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/power-consumption-aware-1.0.0.tm.jsonld"
      ],
      "properties": {
        "reportPowerConsumption": {}
      }
    },
    "SmokeDetection": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/smoke-detector-1.0.0.tm.jsonld"
      ]
    },
    "Status-LED": {
      "definition": [
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
        "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
      ],
      "properties": {
        "color": {
          "r": 0,
          "g": 0,
          "b": 0
        },
        "on": false
      }
    }
  }
}
```

You see that:
* all of the `properties` defined in the `floor-lamp` itself were generated (as `attributes` in the Thing) with default values
* all included `tm:submodel`s were generated as Features 
* all submodel `properties` were generated (as `properties` in the Feature) with default values
* Feature `definition`s were set accordingly, including the extension hierarchy


## Inspecting the Thing Description of the Thing

The Thing we just created can now be asked for its capabilities / interaction affordances by sending the following request:

```bash
curl --location --request GET -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
--header 'Accept: application/td+json'
```

That should result in an HTTP status code `200` (OK) and return the following body:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "om2": "http://www.ontology-of-units-of-measure.org/resource/om-2/",
      "time": "http://www.w3.org/2006/time#"
    }
  ],
  "title": "Floor Lamp",
  "description": "A smart floor lamp with 3 dimmable and color changing spots, smoke detection capability and power consumption awareness.",
  "version": {
    "model": "1.0.0",
    "instance": "1.0.0"
  },
  "id": "urn:io.eclipseprojects.ditto:floor-lamp-0815",
  "base": "https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815",
  "links": [
    {
      "rel": "type",
      "href": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
      "type": "application/tm+json"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/Spot1"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/Spot2"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/Spot3"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/ConnectionStatus"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/PowerConsumptionAwareness"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/SmokeDetection"
    },
    {
      "rel": "item",
      "type": "application/td+json",
      "href": "/features/Status-LED"
    }
  ],
  "security": "basic_sc",
  "securityDefinitions": {
    "basic_sc": {
      "in": "header",
      "scheme": "basic"
    }
  },
  "support": "https://www.eclipse.org/ditto/",
  "created": "2022-02-16T11:48:22.192286Z",
  "forms": [
    {
      "op": "readallproperties",
      "href": "/attributes{?channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "readmultipleproperties",
      "href": "/attributes{?fields,channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "writeallproperties",
      "href": "/attributes{?channel,timeout,response-required}",
      "htv:methodName": "PUT",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "writemultipleproperties",
      "href": "/attributes{?channel,timeout,response-required}",
      "htv:methodName": "PATCH",
      "contentType": "application/merge-patch+json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": [
        "observeallproperties",
        "unobserveallproperties"
      ],
      "href": "/attributes",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": [
        "subscribeallevents",
        "unsubscribeallevents"
      ],
      "href": "/outbox/messages",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    }
  ],
  "properties": {
    "manufacturer": {
      "tile": "Manufacturer",
      "type": "string",
      "readOnly": true,
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/attributes/manufacturer{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": [
            "observeproperty",
            "unobserveproperty"
          ],
          "href": "/attributes/manufacturer",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    },
    "serialNo": {
      "tile": "Serial number",
      "type": "string",
      "readOnly": true,
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/attributes/serialNo{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": [
            "observeproperty",
            "unobserveproperty"
          ],
          "href": "/attributes/serialNo",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    }
  },
  "actions": {
    "switch-all-spots": {
      "title": "Switch all spots",
      "description": "Switches all spots (1-3) on/off based on the passed in boolean.",
      "type": "boolean",
      "forms": [
        {
          "op": "invokeaction",
          "href": "/inbox/messages/switch-all-spots{?timeout,response-required}",
          "htv:methodName": "POST",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    },
    "switch-all-spots-on-for-duration": {
      "title": "Switch all spots on for duration",
      "description": "Switches all spots (1-3) on for a given duration, then switches back to their previous state.",
      "input": {
        "@type": "time:Duration",
        "title": "Duration in seconds",
        "type": "integer",
        "unit": "time:seconds"
      },
      "forms": [
        {
          "op": "invokeaction",
          "href": "/inbox/messages/switch-all-spots-on-for-duration{?timeout,response-required}",
          "htv:methodName": "POST",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    }
  },
  "uriVariables": {
    "channel": {
      "type": "string",
      "title": "The Ditto channel to interact with.",
      "description": "Defines to which channel to route the command: 'twin' (digital twin) or 'live' (the device).",
      "enum": [
        "twin",
        "live"
      ],
      "default": "twin"
    },
    "timeout": {
      "type": "integer",
      "title": "The timeout to apply.",
      "description": "Defines how long the backend should wait (in seconds) for completion of the request. A value of '0' applies fire and forget semantics for the command.",
      "minimum": 0,
      "maximum": 60,
      "default": 60
    },
    "response-required": {
      "type": "boolean",
      "title": "If a response is required.",
      "description": "Defines whether a response is required to the API call or not.",
      "default": true
    },
    "fields": {
      "type": "string",
      "title": "Fields to select.",
      "description": "Contains a comma-separated list of fields (e.g. property names) to be included in the returned JSON."
    }
  },
  "schemaDefinitions": {
    "dittoError": {
      "type": "object",
      "title": "Ditto error.",
      "description": "Provides additional information about an occurred error and how to resolve it.",
      "properties": {
        "description": {
          "type": "string",
          "title": "Error description.",
          "description": "Contains further information about the error e.g. a hint what caused the problem and how to solve it."
        },
        "error": {
          "type": "string",
          "title": "Error code identifier.",
          "description": "The error code or identifier that uniquely identifies the error."
        },
        "message": {
          "type": "string",
          "title": "Error message.",
          "description": "The human readable message that explains what went wrong during the execution of a command/message."
        },
        "href": {
          "type": "string",
          "title": "Error link.",
          "description": "A link to further information about the error and how to fix it.",
          "format": "uri"
        },
        "status": {
          "type": "integer",
          "title": "Status code.",
          "description": "The status code of the error with HTTP status code semantics (e.g.: 4xx for user errors, 5xx for server errors).",
          "minimum": 400,
          "maximum": 599
        }
      },
      "required": [
        "status",
        "error",
        "message"
      ]
    }
  }
}
```

You see that:
* a complete and valid WoT Thing Description was generated and returned
* you can inspect which `properties`, `actions` and `events` the Thing level TD supports
* in the `forms` section you have concrete API endpoints (defined relatively to the top-level `base`) which describe how to e.g. read a single property, or to observe properties, or how to invoke actions 
* you see in the `links` section that this TD contains other `"item"` relations with relative `"href"` link to the Thing's Features
* you get also possible `uriVariables` and a `dittoError` `schemaDefinition` which may be returned when accessing a `property` or invoking an `action`


## Inspecting the Thing Description of a Feature

In order to inspect which capabilities / interaction affordances now a Feature provides, simply perform such a query:

```bash
curl --location --request GET -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1' \
--header 'Accept: application/td+json'
```

That should result in an HTTP status code `200` (OK) and return the following body:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "om2": "http://www.ontology-of-units-of-measure.org/resource/om-2/"
    }
  ],
  "title": "Dimmable Colored Lamp",
  "version": {
    "model": "1.0.0",
    "instance": "1.0.0"
  },
  "id": "urn:io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1",
  "base": "https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1",
  "links": [
    {
      "rel": "collection",
      "href": "https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815",
      "type": "application/td+json"
    },
    {
      "rel": "type",
      "href": "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
      "type": "application/tm+json"
    }
  ],
  "security": "basic_sc",
  "securityDefinitions": {
    "basic_sc": {
      "in": "header",
      "scheme": "basic"
    }
  },
  "support": "https://www.eclipse.org/ditto/",
  "created": "2022-02-16T11:48:22.192286Z",
  "forms": [
    {
      "op": "readallproperties",
      "href": "/properties{?channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "readmultipleproperties",
      "href": "/properties{?fields,channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "writeallproperties",
      "href": "/properties{?channel,timeout,response-required}",
      "htv:methodName": "PUT",
      "contentType": "application/json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": "writemultipleproperties",
      "href": "/properties{?channel,timeout,response-required}",
      "htv:methodName": "PATCH",
      "contentType": "application/merge-patch+json",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": [
        "observeallproperties",
        "unobserveallproperties"
      ],
      "href": "/properties",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    },
    {
      "op": [
        "subscribeallevents",
        "unsubscribeallevents"
      ],
      "href": "/outbox/messages",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [
        {
          "success": false,
          "schema": "dittoError"
        }
      ]
    }
  ],
  "properties": {
    "dimmer-level": {
      "@type": "om2:Percentage",
      "title": "Dimmer level",
      "type": "number",
      "unit": "om2:percent",
      "minimum": 0.0,
      "maximum": 1.0,
      "multipleOf": 0.01,
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/properties/dimmer-level{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/dimmer-level{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/dimmer-level{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": [
            "observeproperty",
            "unobserveproperty"
          ],
          "href": "/properties/dimmer-level",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    },
    "color": {
      "title": "Color",
      "description": "The current color.",
      "type": "object",
      "properties": {
        "r": {
          "title": "Red",
          "type": "integer",
          "minimum": 0,
          "maximum": 255
        },
        "g": {
          "title": "Green",
          "type": "integer",
          "minimum": 0,
          "maximum": 255
        },
        "b": {
          "title": "Blue",
          "type": "integer",
          "minimum": 0,
          "maximum": 255
        }
      },
      "required": [
        "r",
        "g",
        "b"
      ],
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/properties/color{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/color{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/color{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": [
            "observeproperty",
            "unobserveproperty"
          ],
          "href": "/properties/color",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    },
    "on": {
      "title": "On",
      "description": "Whether the switch is on or off.",
      "type": "boolean",
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/properties/on{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/on{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": "writeproperty",
          "href": "/properties/on{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        },
        {
          "op": [
            "observeproperty",
            "unobserveproperty"
          ],
          "href": "/properties/on",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    }
  },
  "actions": {
    "toggle": {
      "title": "Toggle",
      "description": "Toggles/inverts the current 'on' state.",
      "output": {
        "title": "New 'on' state",
        "type": "boolean"
      },
      "forms": [
        {
          "op": "invokeaction",
          "href": "/inbox/messages/toggle{?timeout,response-required}",
          "htv:methodName": "POST",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    },
    "switch-on-for-duration": {
      "title": "Switch on for duration",
      "description": "Switches the switchable on for a given duration, then switches back to the previous state.",
      "input": {
        "@type": "time:Duration",
        "title": "Duration in seconds",
        "type": "integer",
        "unit": "time:seconds"
      },
      "forms": [
        {
          "op": "invokeaction",
          "href": "/inbox/messages/switch-on-for-duration{?timeout,response-required}",
          "htv:methodName": "POST",
          "contentType": "application/json",
          "additionalResponses": [
            {
              "success": false,
              "schema": "dittoError"
            }
          ]
        }
      ]
    }
  },
  "uriVariables": {
    "channel": {
      "type": "string",
      "title": "The Ditto channel to interact with.",
      "description": "Defines to which channel to route the command: 'twin' (digital twin) or 'live' (the device).",
      "enum": [
        "twin",
        "live"
      ],
      "default": "twin"
    },
    "timeout": {
      "type": "integer",
      "title": "The timeout to apply.",
      "description": "Defines how long the backend should wait (in seconds) for completion of the request. A value of '0' applies fire and forget semantics for the command.",
      "minimum": 0,
      "maximum": 60,
      "default": 60
    },
    "response-required": {
      "type": "boolean",
      "title": "If a response is required.",
      "description": "Defines whether a response is required to the API call or not.",
      "default": true
    },
    "fields": {
      "type": "string",
      "title": "Fields to select.",
      "description": "Contains a comma-separated list of fields (e.g. property names) to be included in the returned JSON."
    }
  },
  "schemaDefinitions": {
    "dittoError": {
      "type": "object",
      "title": "Ditto error.",
      "description": "Provides additional information about an occurred error and how to resolve it.",
      "properties": {
        "description": {
          "type": "string",
          "title": "Error description.",
          "description": "Contains further information about the error e.g. a hint what caused the problem and how to solve it."
        },
        "error": {
          "type": "string",
          "title": "Error code identifier.",
          "description": "The error code or identifier that uniquely identifies the error."
        },
        "message": {
          "type": "string",
          "title": "Error message.",
          "description": "The human readable message that explains what went wrong during the execution of a command/message."
        },
        "href": {
          "type": "string",
          "title": "Error link.",
          "description": "A link to further information about the error and how to fix it.",
          "format": "uri"
        },
        "status": {
          "type": "integer",
          "title": "Status code.",
          "description": "The status code of the error with HTTP status code semantics (e.g.: 4xx for user errors, 5xx for server errors).",
          "minimum": 400,
          "maximum": 599
        }
      },
      "required": [
        "status",
        "error",
        "message"
      ]
    }
  }
}
```