---
title: WoT Integration Example
keywords: WoT, TD, TM, ThingDescription, ThingModel, W3C, Semantic, Model, definition, ThingDefinition, FeatureDefinition, example
tags: [wot]
permalink: basic-wot-integration-example.html
---

This page walks you through a complete WoT integration example -- from creating a Thing with a WoT Thing Model to inspecting the generated Thing Descriptions for both the Thing and its Features.

{% include callout.html content="**TL;DR**: Create a Thing with a `definition` pointing to a WoT Thing Model URL. Ditto generates the full JSON skeleton automatically. Then request `Accept: application/td+json` to get a complete Thing Description with API endpoints." type="primary" %}

{% include tip.html content="To experiment with Thing Models and having them exposed as HTTP resources, simply create them as a [GitHub Gist](https://gist.github.com).<br/>
    Each revision of the file will get a unique HTTP endpoint which you can use as endpoint for your Thing Model." %}

## Overview

This example uses a "Floor Lamp" model that demonstrates WoT composition with multiple sub-models. You will:

1. Understand the Thing Model structure
2. Create a Thing that references the model
3. Inspect the generated Thing Description for the Thing
4. Inspect the generated Thing Description for a Feature

## Step 1: Understand the Thing Model

The example uses a floor lamp model hosted in the Ditto examples repository:
[floor-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld)

Source: [GitHub](https://github.com/eclipse-ditto/ditto-examples/blob/master/wot/models/floor-lamp-1.0.0.tm.jsonld)

This model composes several sub-models:

| Sub-model | Instance name | Inherits from |
|-----------|---------------|---------------|
| [dimmable-colored-lamp](https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld) | Spot1, Spot2, Spot3 | colored-lamp -> switchable |
| [connection-status](https://eclipse-ditto.github.io/ditto-examples/wot/models/connection-status-1.0.0.tm.jsonld) | ConnectionStatus | -- |
| [power-consumption-aware](https://eclipse-ditto.github.io/ditto-examples/wot/models/power-consumption-aware-1.0.0.tm.jsonld) | PowerConsumptionAwareness | -- |
| [smoke-detector](https://eclipse-ditto.github.io/ditto-examples/wot/models/smoke-detector-1.0.0.tm.jsonld) | SmokeDetection | -- |
| [colored-lamp](https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld) | Status-LED | switchable |

The floor lamp has 3 dimmable colored spots, a connection status indicator, power consumption awareness, a smoke detector, and a status LED.

## Step 2: Create a Thing from the model

Send a `PUT` request with just the `definition` field pointing to the Thing Model URL. Ditto generates the entire JSON skeleton for you:

```bash
curl --location --request PUT -u ditto:ditto \
  'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
  --header 'Content-Type: application/json' \
  --data-raw '{
    "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld"
  }'
```

Ditto returns `201 Created` with the generated Thing:

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
        "color": { "r": 0, "g": 0, "b": 0 },
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
        "color": { "r": 0, "g": 0, "b": 0 },
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
        "color": { "r": 0, "g": 0, "b": 0 },
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
        "color": { "r": 0, "g": 0, "b": 0 },
        "on": false
      }
    }
  }
}
```

Notice what Ditto generated automatically:
* **Attributes** from the TM's top-level `properties` (with empty string defaults)
* **Features** from each `tm:submodel` (using `instanceName` as the Feature ID)
* **Feature properties** from each submodel's `properties` (with default or neutral values)
* **Feature definitions** including the full extension hierarchy

## Step 3: Inspect the Thing Description

Request the Thing Description using the `Accept: application/td+json` header:

```bash
curl --location --request GET -u ditto:ditto \
  'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
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
  "support": "https://www.eclipse.dev/ditto/",
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

Key observations:
* A complete and valid WoT Thing Description was generated and returned
* The `properties` section describes each attribute with its data type, read/write capabilities, and API forms
* The `actions` section defines invocable operations with their HTTP endpoints
* The `forms` section at root level provides bulk operations (read/write all properties, observe, subscribe)
* The `links` section contains `"item"` relations pointing to each Feature's TD (relative to the `base` URL)
* The `uriVariables` define query parameters (`channel`, `timeout`, `response-required`, `fields`)
* The `schemaDefinitions` provide a `dittoError` schema for error responses

## Step 4: Inspect a Feature's Thing Description

Request the TD for a specific Feature:

```bash
curl --location --request GET -u ditto:ditto \
  'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1' \
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
  "support": "https://www.eclipse.dev/ditto/",
  "created": "2022-02-16T11:48:22.192286Z",
  "forms": [
    {
      "op": "readallproperties",
      "href": "/properties{?channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
    },
    {
      "op": "readmultipleproperties",
      "href": "/properties{?fields,channel,timeout}",
      "htv:methodName": "GET",
      "contentType": "application/json",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
    },
    {
      "op": "writeallproperties",
      "href": "/properties{?channel,timeout,response-required}",
      "htv:methodName": "PUT",
      "contentType": "application/json",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
    },
    {
      "op": "writemultipleproperties",
      "href": "/properties{?channel,timeout,response-required}",
      "htv:methodName": "PATCH",
      "contentType": "application/merge-patch+json",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
    },
    {
      "op": ["observeallproperties", "unobserveallproperties"],
      "href": "/properties",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
    },
    {
      "op": ["subscribeallevents", "unsubscribeallevents"],
      "href": "/outbox/messages",
      "htv:methodName": "GET",
      "subprotocol": "sse",
      "contentType": "text/event-stream",
      "additionalResponses": [{ "success": false, "schema": "dittoError" }]
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
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/dimmer-level{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/dimmer-level{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": ["observeproperty", "unobserveproperty"],
          "href": "/properties/dimmer-level",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        }
      ]
    },
    "color": {
      "title": "Color",
      "description": "The current color.",
      "type": "object",
      "properties": {
        "r": { "title": "Red", "type": "integer", "minimum": 0, "maximum": 255 },
        "g": { "title": "Green", "type": "integer", "minimum": 0, "maximum": 255 },
        "b": { "title": "Blue", "type": "integer", "minimum": 0, "maximum": 255 }
      },
      "required": ["r", "g", "b"],
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/properties/color{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/color{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/color{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": ["observeproperty", "unobserveproperty"],
          "href": "/properties/color",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
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
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/on{?channel,timeout,response-required}",
          "htv:methodName": "PUT",
          "contentType": "application/json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": "writeproperty",
          "href": "/properties/on{?channel,timeout,response-required}",
          "htv:methodName": "PATCH",
          "contentType": "application/merge-patch+json",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        },
        {
          "op": ["observeproperty", "unobserveproperty"],
          "href": "/properties/on",
          "htv:methodName": "GET",
          "subprotocol": "sse",
          "contentType": "text/event-stream",
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
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
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
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
          "additionalResponses": [{ "success": false, "schema": "dittoError" }]
        }
      ]
    }
  },
  "uriVariables": {
    "channel": {
      "type": "string",
      "title": "The Ditto channel to interact with.",
      "description": "Defines to which channel to route the command: 'twin' (digital twin) or 'live' (the device).",
      "enum": ["twin", "live"],
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
      "required": ["status", "error", "message"]
    }
  }
}
```

Key observations:
* A complete and valid WoT Thing Description was generated and returned
* The `properties` section describes each attribute with its data type, read/write capabilities, and API forms
* The `actions` section defines invocable operations with their HTTP endpoints
* The `forms` section at root level provides bulk operations (read/write all properties, observe, subscribe)
* The `links` section contains `"item"` relations pointing to each Feature's TD (relative to the `base` URL)
* The `uriVariables` define query parameters (`channel`, `timeout`, `response-required`, `fields`)
* The `schemaDefinitions` provide a `dittoError` schema for error responses

## Summary

In this walkthrough you:

1. **Created a Thing** with just a `definition` URL -- Ditto generated all attributes, features, and properties
2. **Retrieved a Thing Description** that documents every property, action, and event with concrete API endpoints
3. **Retrieved a Feature Description** that provides detailed interaction affordances including data types, constraints, and API forms

The generated Thing Descriptions are fully compliant WoT TDs that any WoT-compatible tool or library can consume.

## Further reading

* [WoT Overview](basic-wot-integration.html) -- concepts and configuration
* [WoT Validation Configuration](basic-wot-validation-config.html) -- runtime validation API
* [W3C WoT Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/) -- the specification
* [Eclipse edi{TD}or](https://eclipse.github.io/editdor/) -- online WoT model editor
