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
      "definition": ["...same as Spot1..."],
      "properties": {
        "dimmer-level": 0.0,
        "color": { "r": 0, "g": 0, "b": 0 },
        "on": false
      }
    },
    "Spot3": {
      "definition": ["...same as Spot1..."],
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

Ditto returns a complete WoT Thing Description (`200 OK`). Key sections of the response:

### Properties

The TD describes each property with its data type, read/write capabilities, and API endpoints:

```json
{
  "properties": {
    "manufacturer": {
      "title": "Manufacturer",
      "type": "string",
      "readOnly": true,
      "observable": true,
      "forms": [
        {
          "op": "readproperty",
          "href": "/attributes/manufacturer{?channel,timeout}",
          "htv:methodName": "GET",
          "contentType": "application/json"
        }
      ]
    }
  }
}
```

### Actions

Actions map to Thing inbox messages and include the HTTP endpoint for invocation:

```json
{
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
          "contentType": "application/json"
        }
      ]
    }
  }
}
```

### Links to Features

The `links` section points to the Thing Descriptions of each Feature:

```json
{
  "links": [
    {
      "rel": "type",
      "href": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
      "type": "application/tm+json"
    },
    { "rel": "item", "type": "application/td+json", "href": "/features/Spot1" },
    { "rel": "item", "type": "application/td+json", "href": "/features/Spot2" },
    { "rel": "item", "type": "application/td+json", "href": "/features/Spot3" },
    { "rel": "item", "type": "application/td+json", "href": "/features/ConnectionStatus" },
    { "rel": "item", "type": "application/td+json", "href": "/features/PowerConsumptionAwareness" },
    { "rel": "item", "type": "application/td+json", "href": "/features/SmokeDetection" },
    { "rel": "item", "type": "application/td+json", "href": "/features/Status-LED" }
  ]
}
```

### URI variables and error schema

The TD also includes `uriVariables` (like `channel`, `timeout`, `response-required`, `fields`) and a `dittoError` schema definition that documents the error format.

## Step 4: Inspect a Feature's Thing Description

Request the TD for a specific Feature:

```bash
curl --location --request GET -u ditto:ditto \
  'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1' \
  --header 'Accept: application/td+json'
```

The Feature TD includes:

* **Properties** with full type information and API endpoints (e.g., `dimmer-level` with min/max/step values, `color` as an RGB object, `on` as a boolean)
* **Actions** like `toggle` and `switch-on-for-duration` with input/output schemas
* **Forms** for reading, writing, and observing each property
* **Links** back to the parent Thing TD and to the source Thing Model

Example property from the Spot1 TD:

```json
{
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
        "htv:methodName": "GET"
      },
      {
        "op": "writeproperty",
        "href": "/properties/dimmer-level{?channel,timeout,response-required}",
        "htv:methodName": "PUT"
      }
    ]
  }
}
```

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
