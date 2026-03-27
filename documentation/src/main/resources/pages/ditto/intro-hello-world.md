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

## Step 1: Create a Thing

Send a POST request to create a new Thing. This example creates a floor lamp with some metadata
in `attributes` and a model reference in `definition`:

```bash
curl -u ditto:ditto -X POST -H 'Content-Type: application/json' -d '{
  "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
  "attributes": {
    "manufacturer": "ACME",
    "serialNo": "0815666337"
  }
}' 'http://localhost:8080/api/2/things'
```

**What happens:** Ditto creates the Thing and returns its JSON representation. Because you used
POST, Ditto auto-generates a Thing ID. Every ID contains a namespace prefix before the `:` to
help organize your Things.

## Step 2: Retrieve the Thing

Query your Thing by its ID. Replace the ID below with the one returned in Step 1:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/<NAMESPACE>:<THING-NAME>' | jq
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
}' 'http://localhost:8080/api/2/things/<NAMESPACE>:<THING-NAME>/features/Spot1'
```

**What happens:** Ditto adds a `Spot1` feature to your Thing with the specified properties.

## Step 4: Read a single property

Ditto exposes every attribute and feature property as its own HTTP endpoint. Retrieve the
current on/off state of `Spot1`:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/<NAMESPACE>:<THING-NAME>/features/Spot1/properties/on'
```

**What happens:** Ditto returns the value `false` (or whatever the current value is).

## Step 5: Update a single property

Turn on the lamp spot by updating its `on` property:

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d 'true' \
  'http://localhost:8080/api/2/things/<NAMESPACE>:<THING-NAME>/features/Spot1/properties/on'
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
