---
title: Feature
keywords: definition, properties, desiredProperties, entity, feature, functionblock, informationmodel, model, vorto
tags: [model]
permalink: basic-feature.html
---

A Feature is used to manage all data and functionality of a Thing that can be clustered in an outlined technical
context.

For different contexts or aspects of a Thing different Features can be used which are all belonging to the same Thing
and do not exist without this Thing.

## Feature ID
Within a Thing each Feature is identified by a unique string - the so-called Feature ID.
A Feature ID often needs to be set in the path of a HTTP request. Due to this fact we strongly recommend using a
restricted set of characters (e.g. those for [Uniform Resource Identifiers (URI)](https://www.ietf.org/rfc/rfc3986.txt)).

The Feature ID may not be the wildcard operator '*' because it has a special meaning and can lead to unexpected
results when retrieving or searching for things/features.

## Feature properties

The **data** related to Features is managed in form of a **list of properties**. These properties can be categorized,
e.g. to manage the status, the configuration or any fault information.
Feature properties are represented as one JSON object.

Each property itself can be either a simple/scalar value or a complex object; allowed is any JSON value.

## Feature desired properties

Desired properties represent the desired state of the properties. They are a tool to represent the desired target state 
of the properties. 
The **desiredProperties** related to Features are managed in form of a **list of properties**. These desired properties 
can be categorized, e.g. to manage the status, the configuration or any fault information.
Feature desired properties are represented as one JSON object.

Each desired property itself can be either a simple/scalar value or a complex object; allowed is any JSON value.

Please note however, that besides persisting the desired properties, and indexing the fields for search requests, filtering 
etc. for the time being, Ditto does not implement their further processing. Such functionality will come with future releases.

## Feature definition

Ditto supports specifying a definition for a feature in order to document how a feature's state is structured
(in [properties](#feature-properties)), and which behavior/capabilities
([messages related to features](basic-messages.html)) can be expected from such a feature.

A feature's definition is a list of definition identifiers containing 
* either a *namespace*, *name* and *version* separated by colons: `<namespace>:<name>:<version>`
* or valid HTTP(s) URLs

A Definition can be seen as some kind of type for features. The [properties](#feature-properties) of a 
feature containing a definition identifier `"org.eclipse.ditto:lamp:1.0.0"` can be expected to follow the structure
described in the `lamp` type of namespace `org.eclipse.ditto` semantically versioned with version `1.0.0`.

{% include note.html content="Ditto does not contain a type system on its own and does not specify how to describe types. 
   You may either use [Eclipse Vorto](#the-link-to-eclipse-vorto) or [W3C Web of Things](#the-link-to-w3c-web-of-things)
   to describe data structures and supported messages of Ditto features." %}

{% include warning.html content="Currently Ditto **does not** ensure that the `properties` or 
 `desiredProperties` of a feature or its supported messages follow the type defined in the definition." %}

### The link to Eclipse Vorto

If a [feature definition](#feature-definition) has the form `<namespace>:<name>:<version>`, those 3 values may be
interpreted as the link to an Eclipse Vorto "function block" model.

{% include warning.html content="Ditto does not enforce structures and data types based on Eclipse Vorto models -
a referenced Vorto model may be used in order to find out which data structures and messages a Ditto feature
supports. Validation - if needed - must be done in another place." %}

> Vorto is an open source tool that allows to create and manage technology agnostic, abstract device descriptions,
so called information models. Information models describe the attributes and the capabilities of real world devices.
Source: [http://www.eclipse.org/vorto/](http://www.eclipse.org/vorto/)

Ditto's feature definition may be mapped to the Vorto type system which is defined by so called "information models"
and "function blocks":
> Information models represent the capabilities of a particular type of device entirely.
An information model contains one or more function blocks.

> A function block provides an abstract view on a device to applications that want to employ the devices' functionality.
Thus, it is a consistent, self-contained set of (potentially re-usable) properties and capabilities.

{% include image.html file="pages/basic/ditto-thing-feature-definition-model.png" alt="Feature Definition Model"
caption="One Thing can have many features. A feature may conform to a definition" max-width=250 %}

#### Mapping Vorto function block elements

A Vorto function block consists of different sections defining state and capabilities
(see also [Eclipse Vorto's documentation](https://www.eclipse.org/vorto/)) of a device
(in our case of a feature):
* `configuration`: Contains one or many configuration properties for the function block.
* `status`: Contains one or many status properties for the function block.
* `fault`: Contains one or many fault properties for the function block.
* `operations`: Contains one or many operations for the function block.
* `events`: Contains one or many events for the function block.

##### Function block state

The `configuration`, `status` and `fault` sections of a function block define the state of a feature in Ditto.

They are mapped to a corresponding JSON object inside the feature [properties](#feature-properties) which gets us
following JSON structure of a Ditto feature:

```json
{
    "feature-id": {
        "definition": [ "<namespace>:<name>:<version>" ],
        "properties": {
            "configuration": {
            },
            "status": {
            },
            "fault": {
            }
        }
    }
}
```

The structure below `configuration, status, fault` is defined by the custom types of the Vorto function block. As these
can be simple types as well as complex types, the JSON structure follows the structure of the types.

##### Function block capabilities

The `operations` and `events` sections of a function block define the capabilities or behavior of a Ditto feature.

Both are mapped to feature [messages](basic-messages.html) sent "to" or "from" a feature.
* A message sent **to** a feature is mapped to an `operation`.
* A messages sent **from** a feature is mapped to an `event`.

#### Vorto example

Here an example for a Vorto Function Block (in Vorto's custom DSL) with the name `Lamp`.
For the sake of giving an example for events this lamp has some additional capabilities like detecting movement and
smoke .

```
 namespace com.mycompany.fb
 version 1.0.0
 displayname "Lamp"
 category demo	
 using com.mycompany.fb.Lamp ; 1.0.0
 using com.mycompany.fb.Color ; 1.2.0
 functionblock Lamp {
     configuration {
         mandatory on as boolean "this defines whether the lamp should be on or off"
         location as Location "the location of the lamp"
     }
     	
     status { 
         mandatory on as boolean "the reported on/off state"
         color as Color "the reported color of the lamp"
     }
     
     events {
        smokeDetected {
            intensity as float <MIN 0.0, MAX 10.0> "the intensity of the detected smoke"
            mandatory critical as boolean "whether it is critical or not"
        }
        
        movementAlarm {}
    }
	
     operations{
         blink(interval as int, duration as int) returns boolean "lets the Lamp blink in the interval for duration"
         stopBlinking() returns boolean "stops blinking lets the Lamp"
         changeColor(newColor as Color) "changes the color of the Lamp"
     }
 }
```

The Vorto function block contains all information required for a feature's definition identifier in Ditto:
* namespace: `com.mycompany.fb`
* name: `Lamp`
* version: `1.0.0`

A feature containing a definition pointing to such a Vorto function block would look like this:

```json
{
    "lamp": {
        "definition": [ "com.mycompany.fb:Lamp:1.0.0" ],
        "properties": {
            "configuration": {
                "on": true,
                "location": {
                    "longitude": 34.052235,
                    "latitude": -118.243683
                }
            },
            "status": {
                "on": false,
                "color": {
                    "red": 128,
                    "green": 255,
                    "blue": 0
                }
            }
        }
    }
}
```

The capabilities or behavior of this "lamp" feature would be defined as [messages](basic-messages.html):
* Message with subject `smokeDetected` which is sent `FROM` a feature containing a JSON payload with an
  `intensity` and whether the detected smoke has reached a `critical` mass or not.
* Message with subject `movementAlarm` which is sent `FROM` a feature with no payload.
* Message with subject `blink` which is sent `TO` a feature containing a JSON payload of an `interval`
  (as JSON number) and a duration (also as JSON number) returning a JSON boolean.
* Message with subject `stopBlinking` which is sent `TO` a feature with no payload returning a JSON boolean.
* Message with subject `changeColor` which is sent `TO` a feature containing a JSON payload which follows the type
  `Color` defined in another Vorto function block.

### The link to W3C Web of Things

If a [feature definition](#feature-definition) has the form of an HTTP(s) URL, this URL pointing to a resource may be
interpreted as the link to a [W3C WoT (Web of Things)](https://www.w3.org/TR/wot-thing-description11/) 
[Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model) in [JSON-LD](https://www.w3.org/TR/json-ld11/) 
format.

#### Mapping WoT Thing Model elements

A WoT Thing Model describes the following elements a "thing" supports:
* properties
* actions
* events

The following table shows an overview of how those elements map to Ditto concepts:

| WoT element           | Ditto concept  |
| [Thing](https://www.w3.org/TR/wot-thing-description11/#thing) | Feature.<br/>In Ditto, a Feature is an aspect of a [Ditto Thing](basic-thing.html). As the feature is defined by its properties and messages it supports, it maps to a WoT Thing. |
| [Properties](https://www.w3.org/TR/wot-thing-description11/#propertyaffordance) | Feature [properties](#feature-properties) and [desired properties](#feature-desired-properties) |
| [Actions](https://www.w3.org/TR/wot-thing-description11/#actionaffordance) | [Messages](basic-messages.html#elements) with **Direction** *to* ("inbox") of a Thing ID + Feature ID combination |
| [Events](https://www.w3.org/TR/wot-thing-description11/#eventaffordance) | [Messages](basic-messages.html#elements) with **Direction** *from* ("outbox") of a Thing ID + Feature ID combination  |

# Example

The following snippet shows a Feature with the ID "arbitrary-feature" and a definition with the sole identifier
"org.eclipse.ditto:complex-type:1.0.0":

```json
{
  "arbitrary-feature": {
    "definition": [ "org.eclipse.ditto:complex-type:1.0.0" ],
    "properties": {
      "status": {
        "connected": true,
        "complexProperty": {
          "street": "my street",
          "house no": 42
        }
      }
    },
    "desiredProperties": {
      "status": {
        "connected": false
      }
    }
  }
}
```

## Model specification

The feature model API version 2:

### V2
{% include docson.html schema="jsonschema/feature_v2.json" %}
