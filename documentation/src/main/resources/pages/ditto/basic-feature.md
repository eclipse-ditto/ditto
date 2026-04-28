---
title: Features
keywords: definition, properties, desiredProperties, entity, feature, functionblock, informationmodel, model
tags: [model]
permalink: basic-feature.html
---

A Feature groups related state data and capabilities of a [Thing](basic-thing.html) under a named
identifier -- for example, a "temperature" feature on a weather station or a "lamp" feature on a
smart light.

{% include callout.html content="**TL;DR**: A Feature has an ID, properties (current state), optional desired
properties (target state), and an optional definition that links it to a type model." type="primary" %}

## How Features work

Each Feature within a Thing represents one functional aspect of the device. A smart thermostat
might have these Features:

* `temperature` -- current and target temperature readings
* `humidity` -- current humidity level
* `schedule` -- heating schedule configuration

You access each Feature independently through the API. For example, to read the current temperature:

```bash
curl -u ditto:ditto -X GET \
  'http://localhost:8080/api/2/things/com.example:thermostat-1/features/temperature/properties/value'
```

## Model specification

{% include docson.html schema="jsonschema/feature_v2.json" %}

## Feature ID

Every Feature within a Thing has a unique string identifier. Since Feature IDs often appear in HTTP
request paths, use characters that are safe in
[URIs](https://www.ietf.org/rfc/rfc3986.txt) -- letters, digits, hyphens, and underscores.

The Feature ID must not be the wildcard operator `*`, because it has special meaning in queries
and can produce unexpected results.

## Feature properties

Properties hold the Feature's current state data as a JSON object. Each property can be a scalar
value or a nested object -- any valid JSON value works:

```json
{
  "properties": {
    "value": 23.5,
    "unit": "Celsius",
    "location": {
      "latitude": 48.1351,
      "longitude": 11.5820
    }
  }
}
```

## Feature desired properties

Desired properties represent the **target state** you want the device to reach. They have the same
structure as regular properties but describe what *should be* rather than what *is*.

For example, you might set a desired temperature of 22.0 while the current temperature reads 19.5:

```json
{
  "properties": {
    "value": 19.5
  },
  "desiredProperties": {
    "value": 22.0
  }
}
```

Ditto persists desired properties and indexes them for search queries. The logic for reconciling
desired state with actual state is up to your application or device firmware.

## Feature definition

A definition links the Feature to a type model that documents the Feature's structure and
capabilities. You specify definitions as a list containing:

* **HTTP(s) URLs** -- for example, a [WoT (Web of Things) Thing Model](basic-wot-integration.html#thing-model-describing-a-ditto-feature)
* **Identifier strings** in the format `<namespace>:<name>:<version>` -- for example,
  `org.eclipse.ditto:lamp:1.0.0`

A definition acts as a type annotation. If a Feature has the definition `org.eclipse.ditto:lamp:1.0.0`,
you can expect its properties to follow the structure described by version `1.0.0` of the "lamp" type
in the `org.eclipse.ditto` namespace.

{% include note.html content="Ditto does not contain a built-in type system and does not validate that properties
   match their definition. You can use the [W3C Web of Things (WoT)](#the-link-to-w3c-wot-web-of-things) standard
   to describe data structures and supported messages for Ditto features." %}

### The link to W3C WoT (Web of Things)

When a [feature definition](#feature-definition) is an HTTP(s) URL, that URL can point to a
[W3C WoT Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model) in
[JSON-LD](https://www.w3.org/TR/json-ld11/) format.

For a detailed explanation of how WoT concepts map to Ditto, see the
[WoT integration documentation](basic-wot-integration.html).

## Example

A Thing with two Features -- one for a heating element, one for a switch:

```json
{
  "heating-no1": {
    "properties": {
      "connected": true,
      "temperature": 21.5
    },
    "desiredProperties": {
      "temperature": 23.0
    }
  },
  "switchable": {
    "definition": [ "org.eclipse.ditto:Switcher:1.0.0" ],
    "properties": {
      "on": true,
      "lastToggled": "2017-11-15T18:21Z"
    }
  }
}
```

## Further reading

* [WoT (Web of Things) integration](basic-wot-integration.html)
* [WoT (Web of Things) example](basic-wot-integration-example.html)
* [Things](basic-thing.html) -- the parent entity that contains Features
* [Messages](basic-messages.html) -- send commands to specific Features
