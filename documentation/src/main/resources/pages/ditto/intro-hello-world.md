---
title: Hello World Tutorial
tags: [getting_started]
permalink: intro-hello-world.html
---

This tutorial walks you through creating, querying, and updating your first digital twin using
Ditto's HTTP API.

{% include callout.html content="**TL;DR**: Create a Thing with a `POST` request, read it with `GET`, and update individual
properties with `PUT` -- all using standard HTTP." type="primary" %}

## Prerequisites

* A running Ditto instance (see [Installation & Running](installation-running.html))
* [cURL](https://github.com/curl/curl) or another HTTP client
* The default credentials `ditto:ditto` set up by the Docker deployment's nginx
  (see [Docker deployment README](https://github.com/eclipse-ditto/ditto/blob/master/deployment/docker/README.md))

## What a complete Thing looks like

Before diving into the steps, here is a fully modeled floor lamp Thing with 7 features. This is
what you are building toward:

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
        "dimmer-level": 0,
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
        "dimmer-level": 0,
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
        "dimmer-level": 0,
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

A Thing has three top-level data sections:

* **attributes**: static metadata (manufacturer, serial number) -- any JSON structure
* **features**: dynamic state data -- each feature has a `properties` object and optionally a `definition`
  linking to a [WoT Thing Model](basic-wot-integration.html)
* **definition**: model reference -- a single string linking to a Thing Model describing the Thing's
  capabilities

## Step 1: Create a Thing

Send a PUT request to create a new Thing with a specific ID. This example creates a floor lamp
with metadata in `attributes` and a model reference in `definition`:

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d '{
  "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
  "attributes": {
    "manufacturer": "ACME",
    "serialNo": "0815666337"
  }
}' 'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815'
```

**What happens:** Ditto creates the Thing and returns `201 Created`. The ID
`io.eclipseprojects.ditto:floor-lamp-0815` contains a namespace (`io.eclipseprojects.ditto`)
before the `:` to help organize your Things.

{% include note.html content="You can also use `POST` to `/api/2/things` without specifying an ID -- Ditto will auto-generate one." %}

## Step 2: Retrieve the Thing

Query the Thing by its ID:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' | jq

# if you have python installed, that's an alternative for pretty-printing:
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' | python -m json.tool
```

**What happens:** Ditto returns the full JSON representation of your Thing, including its
`thingId`, `policyId`, `definition`, `attributes`, and `features`.

## Step 3: Add a Feature with state data

Add a feature to represent the lamp's first spot light. Features hold dynamic state data like
sensor readings or device configuration:

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d '{
  "properties": {
    "on": false,
    "dimmer-level": 0,
    "color": { "r": 0, "g": 0, "b": 0 }
  }
}' 'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1'
```

**What happens:** Ditto adds a `Spot1` feature to your Thing with the specified properties.

## Step 4: Read a single property

Ditto exposes every attribute and feature property as its own HTTP endpoint. Retrieve the
current on/off state of `Spot1`:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1/properties/on'
```

**What happens:** Ditto returns the value `false` (or whatever the current value is).

## Step 5: Update a single property

Turn on the lamp spot by updating its `on` property:

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d 'true' \
  'http://localhost:8080/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1/properties/on'
```

**What happens:** Ditto updates the property and returns `204 No Content` on success.

## Step 6: Search for Things

Find all Things from a specific manufacturer using Ditto's search API:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/search/things?filter=eq(attributes/manufacturer,"ACME")'
```

**What happens:** Ditto searches across all Things you have access to and returns those matching
the filter.

## What you learned

In this tutorial you:

1. Created a digital twin (Thing) via the HTTP API
2. Retrieved the full Thing and individual properties
3. Added a Feature with state data
4. Updated a single property value
5. Searched for Things by attribute values

## Further reading

* [Data Model Overview](basic-overview.html) -- understand Things, Features, and Attributes in detail
* [Policies](basic-policy.html) -- control who can read and write your Things
* [Messages](basic-messages.html) -- send commands to actual devices through their twins
