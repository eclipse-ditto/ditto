---
title: Data Model Overview
keywords: basic concepts, overview, thing, feature, domain model, model
tags: [model]
permalink: basic-overview.html
---

Ditto's data model organizes IoT device data into a hierarchy of Things, Features, and Policies.

{% include callout.html content="**TL;DR**: A Thing has attributes (static metadata) and features (dynamic state). A
Policy controls who can read and write each part. That is the entire data model." type="primary" %}

## How the model works

Ditto does not enforce a specific schema for your
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iot}}">IoT</a> data. Instead, it provides
two structural elements inside each [Thing](basic-thing.html):

* **Attributes** -- static metadata about the `Thing` (location, serial number, manufacturer) stored as
  a JSON object. These values do not change frequently.
* **[Features](basic-feature.html)** -- dynamic state data (sensor readings, configuration,
  operational status). Each Feature groups related properties under a named identifier.

Access control is managed separately through [Policies](basic-policy.html). The `Thing` references its
Policy by `policyId`, and that Policy defines which authenticated subjects may read or write the
`Thing` or parts of it.

{% include image.html file="pages/basic/ditto-class-diagram-v2.png" alt="Ditto Class Diagram"
 caption="Class diagram of Ditto's core entities." max-width=600 %}

## Minimal Thing

The smallest valid Thing contains only an ID and a reference to its Policy:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id"
}
```

Attributes and Features are optional.

## A Thing with data

A more realistic Thing includes a definition, an attribute, and a feature:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
  "definition": "digitaltwin:DigitaltwinExample:1.0.0",
  "attributes": {
    "location": "Kitchen"
  },
  "features": {
    "transmission": {
      "properties": {
        "cur_speed": 90
      }
    }
  }
}
```

## Further reading

* [Things](basic-thing.html) -- full details on Thing structure, IDs, and attributes
* [Features](basic-feature.html) -- properties, desired properties, and definitions
* [Policies](basic-policy.html) -- fine-grained access control for Things and Features
* [Namespaces & Names](basic-namespaces-and-names.html) -- naming conventions for entity IDs
