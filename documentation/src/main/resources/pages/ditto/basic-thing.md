---
title: Things
keywords: entity, feature, model, namespace, thing
tags: [model]
permalink: basic-thing.html
---

A Thing is Ditto's core entity. It represents any asset you want to manage as a digital twin -- a
physical device, a virtual grouping, or any concept you can model as structured data.

{% include callout.html content="**TL;DR**: A Thing is a JSON object with an ID, a Policy reference, optional
attributes (static metadata), and optional features (dynamic state data)." type="primary" %}

## What is a Thing?

A Thing is a generic container that acts as a handle for all the data related to one asset. You
decide what a Thing represents:

* **Physical device** -- a sensor, a lamp, a vehicle, a lawn mower
* **Virtual device** -- a room in a building, a virtual power plant, weather data for a location
* **Transactional entity** -- a vehicle trip from start to stop, a series of machine measurements
* **Master data entity** -- a device supplier, a service provider, a geographic region
* **Anything else** that fits the [data model](basic-overview.html)

## Thing structure

### Thing ID

Every Thing has a unique identifier that follows the [namespaced ID](basic-namespaces-and-names.html#namespaced-id)
format: `<namespace>:<name>`. For example: `com.example:temperature-sensor-42`.

### Policy reference

Each Thing links to a [Policy](basic-policy.html) via its `policyId`. The
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a> defines which
authenticated subjects may read and write the Thing -- down to individual attributes and feature
properties.

### Definition

A Thing can include a `definition` that links it to a model describing its capabilities. The
definition can be:

* A valid HTTP(s) URL -- for example pointing to a [WoT (Web of Things) Thing Model](basic-wot-integration.html#thing-model-describing-a-ditto-thing)
* A reference using the syntax `<namespace>:<name>:<version>` -- for example `org.eclipse.ditto:HeatingDevice:2.1.0`

### Attributes

Attributes store static metadata about the Thing as a JSON object. Use them for data that does
not change frequently -- manufacturer, serial number, installation location. You can nest values
to any depth:

```json
{
  "manufacturer": "ACME corp",
  "location": {
    "building": "HQ",
    "floor": 3,
    "room": "Lab-A"
  }
}
```

### Features

A Thing can contain any number of [Features](basic-feature.html). Each Feature groups related
dynamic state data (sensor readings, configuration, operational status) under a named identifier.

{% include image.html file="pages/basic/ditto-thing-feature.png" alt="Thing and Feature" caption="One Thing can have
many Features" max-width=100 %}

### Metadata

A Thing can carry additional [metadata](basic-metadata.html) attached to any of its attributes or
feature properties -- for example, timestamps recording when values last changed or units of
measurement.

## Model specification

{% include docson.html schema="jsonschema/thing_v2.json" %}

## Example

```json
{
  "thingId": "the.namespace:theName",
  "policyId": "the.namespace:thePolicyName",
  "definition": "org.eclipse.ditto:HeatingDevice:2.1.0",
  "attributes": {
    "someAttr": 32,
    "manufacturer": "ACME corp"
  },
  "features": {
    "heating-no1": {
      "properties": {
        "connected": true,
        "complexProperty": {
          "street": "my street",
          "house no": 42
        }
      },
      "desiredProperties": {
        "connected": false
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
}
```

## Further reading

* [Features](basic-feature.html) -- properties, desired properties, and definitions
* [Policies](basic-policy.html) -- access control for Things
* [Namespaces & Names](basic-namespaces-and-names.html) -- ID format and naming rules
* [Metadata](basic-metadata.html) -- attach contextual information to Thing data
