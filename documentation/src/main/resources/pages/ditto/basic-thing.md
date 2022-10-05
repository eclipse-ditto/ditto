---
title: Thing
keywords: entity, feature, model, namespace, thing
tags: [model]
permalink: basic-thing.html
---

The versatile assets in IoT applications can be managed as Things.


## Thing

Things are very generic entities and are mostly used as a “handle” for multiple features belonging to this Thing.

Examples:

* Physical Device: a lawn mower, a sensor, a vehicle, a lamp.
* Virtual Device: a room in a house, a virtual power plant spanning multiple power plants, the weather information for
  a specific location collected by various sensors.
* Transactional entity: a tour of a vehicle (from start until stop), a series of measurements of a machine.
* Master data entity: a supplier of devices or a service provider for devices, an entity representing a city/region.
* Anything else - if it can be modeled and managed appropriately by the supported concepts/capabilities.


### Thing ID

Unique identifier of a Thing. For choosing custom Thing IDs when creating a Thing, the rules for 
[namespaced IDs](basic-namespaces-and-names.html#namespaced-id) apply.

### Access control

A Thing in API version 2 contains a link to a [Policy](basic-policy.html) in form of a `policyId`. 
This <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a> defines which 
authenticated subjects may READ and WRITE the Thing or even parts of it (hierarchically specified).


### Definition

A Thing may contain a definition. The definition can also be used to find Things. The definition is used to link a thing
to a corresponding model defining the capabilities/features of it.  
The definition can for example point to a:  
* a valid HTTP(s) URL (e.g. in order to define that the Thing is described by a [WoT (Web of Things) Thing Model](basic-wot-integration.html#thing-model-describing-a-ditto-thing))
* or some other model reference using the syntax `<namespace>:<name>:<version>`, e.g. an [Eclipse Vorto](https://www.eclipse.org/vorto/) "information model" 


### Attributes

Attributes describe the Thing in more detail and can be of any type. Attributes can also be used to find Things.
Attributes are typically used to model rather static properties at the Thing level. Static means that the values do not
change as frequently as property values of Features.


### Features

A Thing may contain an arbitrary amount of [Features](basic-feature.html). 

{% include image.html file="pages/basic/ditto-thing-feature.png" alt="Thing and Feature" caption="One Thing can have
many Features" max-width=100 %}


### Metadata

A Thing may contain additional [metadata](basic-metadata.html) for all of its attributes and features describing the
semantics of the data or adding other useful information about the data points of the twin.


### Model specification

#### API version 2

{% include docson.html schema="jsonschema/thing_v2.json" %}


### Example

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
