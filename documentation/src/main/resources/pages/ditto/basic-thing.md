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

Unique identifier of a Thing. For choosing custom Thing IDs when creating a Thing, following rules apply:

#### Allowed Characters

Due to the fact that a Thing ID often needs to be set in the path of a HTTP request, we have restricted the set of
allowed characters to those for [Uniform Resource Identifiers (URI)](http://www.ietf.org/rfc/rfc2396.txt).

In order to separate Things from different Solution spaces from each other, they are required to be created in a
specific *Namespace*.
This Namespace needs to be provided additionally to every REST request as a **prefix** of the Thing ID:

* The Namespace must conform to Java package naming:
    * must start with a lower- or uppercase character from a-z,
    * can use dots (`.`) to separate characters,
    * a dot (`.`) must be followed by a lower- or uppercase character from a-z,
    * numbers can be used,
    * underscore can be used,
* The Namespace is separated by a mandatory colon (`:`) from the thingId.

#### Examples

Following some examples of valid Thing IDs are given:
* `org.eclipse.ditto:fancycar-1`,
* `foo:fancycar-1`,
* `org.eclipse.ditto_42:fancycar-1`.


### Access control

A Thing in API version 1 contains an inline [ACL](basic-acl.html) defining which authenticated parties may READ, WRITE
and ADMINISTRATE the `Thing`.

A Thing in API version 2 does no longer contain the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a>.
Instead it contains a link to a [Policy](basic-policy.html) in form of a `policyId`. This 
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a> defines which 
authenticated subjects may READ and WRITE the Thing or even parts of it (hierarchically specified).


### Attributes

Attributes describe the Thing in more detail and can be of any type. Attributes can also be used to find Things.
Attributes are typically used to model rather static properties at the Thing level. Static means that the values do not
change as frequently as property values of Features.


### Features

A Thing may contain an arbitrary amount of [Features](basic-feature.html). 

{% include image.html file="pages/basic/ditto-thing-feature.png" alt="Thing and Feature" caption="One Thing can have
many Features" max-width=100 %}


### Model specification

#### API version 1

{% include docson.html schema="jsonschema/thing_v1.json" %}

#### API version 2

{% include docson.html schema="jsonschema/thing_v2.json" %}


### Example (API version 1)

```json
{
  "thingId": "the.namespace:theId",
  "acl": {
    "subject-id": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  },
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
          }
      },
      "switchable": {
          "properties": {
              "on": true,
              "lastToggled": "2017-11-15T18:21Z"
          }
      }
  }
}
```
