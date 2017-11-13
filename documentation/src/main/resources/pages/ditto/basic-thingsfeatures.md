---
title: Things and Features
keywords: thing, feature
tags: [model]
permalink: basic-thingsfeatures.html
---

The versatile assets in IoT applications can be managed as Things.

## Thing

Things are very generic entities and are mostly used as a “handle” for multiple features belonging to this Thing.

Examples:

* Physical Device: a lawn mower, a sensor, a vehicle, a lamp
* Virtual Device: a room in a house, a virtual power plant spanning multiple power plants, the weather information for a specific location collected by various sensors
* Transactional entity: a tour of a vehicle (from start until stop), a series of measurements of a machine
* Master data entity: a supplier of devices or a service provider for devices, an entity representing a city/region
* Anything else - if it can be modeled and managed appropriately by the supported concepts/capabilities

### Attributes

Attributes describe the Thing in more detail and can be of any type. Attributes can also be used to find Things. Attributes are typically used to model rather static properties at the Thing level. Static means that the values do not change as frequently as property values of Features.

### Thing ID

For choosing custom Thing IDs when creating a Thing, following rules apply:

#### Allowed Characters

Due to the fact that a Thing ID often needs to be set in the path of a HTTP request, we have restricted the set of allowed characters to those for Uniform Resource Identifiers (URI) see http://www.ietf.org/rfc/rfc2396.txt.
Namespace

In order to separate Things from different Solution spaces from each other, they are required to be created in a specific Namespace.
This Namespace needs to be provided additionally to every REST request as a **prefix** of the thingId:

* The Namespace must conform to Java package naming:
    * must start with a lower- or uppercase character from a-z
    * can use dots (`.`) to separate characters
    * a dot (`.`) must be followed by a lower- or uppercase character from a-z
    * numbers can be used
    * underscore can be used
* The Namespace is separated by a mandatory colon (`:`) from the thingId

Examples for a valid Thing ID:
* `org.eclipse.ditto:fancycar-1`
* `foo:fancycar-1`
* `org.eclipse.ditto_42:fancycar-1`

## Feature

A Feature is used to manage all data and functionality of a Thing that can be clustered in an outlined technical context.

For different contexts or aspects of a Thing different Features can be used which are all belonging to the same Thing and do not exist without this Thing.

The **data** related to Features is managed in form of a **list of properties**. These properties can be categorized, e.g. to manage the status, the configuration or any fault information.

Each property itself can be either a simple/scalar value or a complex object. Allowed is any JSON object.

### Feature ID
Due to the fact that a Feature ID often needs to be set in the path of a HTTP request, we strongly recommend to use a restricted the set of characters (e.g. those for Uniform Resource Identifiers (URI) see [https://www.ietf.org/rfc/rfc2396.txt](https://www.ietf.org/rfc/rfc2396.txt)).

## Schematic view

{% include image.html file="pages/basic/ditto-thing-feature.png" alt="Thing and Feature" caption="One Thing can have many Features" max-width=100 %}

## Example

```json
{
  "thingId": "the.namespace:theId",
  "attributes": {
      "someAttr": 32,
      "manufacturer": "ACME corp"
  },
  "features": {
      "featureId-0-to-n": {
          "properties": {
              "connected": true,
              "complexProperty": {
                  "street": "my street",
                  "house no": 42
              }
          }
      }
  }
}
```
