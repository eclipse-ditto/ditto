---
title: Feature
keywords: definition, properties, entity, feature, functionblock, informationmodel, model, vorto
tags: [model]
permalink: basic-feature.html
---

## Feature

A Feature is used to manage all data and functionality of a Thing that can be clustered in an outlined technical
context.

For different contexts or aspects of a Thing different Features can be used which are all belonging to the same Thing
and do not exist without this Thing.

### Feature ID
Within a Thing each Feature is identified by a unique string - the so called Feature ID.
A Feature ID often needs to be set in the path of a HTTP request. Due to this fact we strongly recommend to use a
restricted set of characters (e.g. those for
[Uniform Resource Identifiers (URI)](https://www.ietf.org/rfc/rfc2396.txt)).

### Feature Properties

The **data** related to Features is managed in form of a **list of properties**. These properties can be categorized,
e.g. to manage the status, the configuration or any fault information.
Feature properties are represented as one JSON object.

Each property itself can be either a simple/scalar value or a complex object; allowed is any JSON value.

### Feature Definition

Ditto supports specifying a Definition for a Feature in order to document how a Feature's state ([Properties](#feature-properties)) 
is structured and which behavior/capabilities ([messages addressed to Features](basic-messages.html)) can be expected from a Feature.<br/>

A Feature Definition is a list of Definition Identifiers containing a *namespace*, *name* and *version* separated 
by colons: `namespace:name:version`.

A Definition can be seen as some kind of type for Features. The [Properties](#feature-properties) of a 
Feature containing a Definition Identifier `"org.eclipse.ditto:lamp:1.0.0"` can be expected to follow
the structure defined in the `lamp` type of namespace `org.eclipse.ditto` semantically versioned with version `1.0.0`.

{% include note.html content="Ditto does not contain a type system on its own and does not specify how to describe types. 
    For that it will make use of [Eclipse Vorto](#the-link-to-eclipse-vorto)." %}

Utilizing Feature Definitions against a type system, Ditto can support contract-based development to ensure validity and 
integrity of **Digital Twins**.

{% include warning.html content="Currently Ditto **does not** ensure or enforce that the Properties of a Feature or its 
    supported messages follow the type defined in the Definition." %}

## Example

A single Feature including its Feature ID:

```json
{
    "arbitrary-feature": {
        "definition": [ "org.eclipse.ditto:complex-type:1.0.0" ],
        "properties": {
            "connected": true,
            "complexProperty": {
                "street": "my street",
                "house no": 42
            }
        }
    }
}
```

## Model specification

The Feature model is the same for API version 1 and 2:

{% include docson.html schema="jsonschema/feature.json" %}


## The link to Eclipse Vorto

> Vorto is an open source tool that allows to create and manage technology agnostic, abstract device descriptions, so called 
information models. Information models describe the attributes and the capabilities of real world devices.<br/>
Source: [http://www.eclipse.org/vorto/](http://www.eclipse.org/vorto/)

Ditto's Feature Definitions may be mapped to the Vorto type system which is defined<br/>
by so called "Information Models":
> Information models represent the capabilities of a particular type of device entirely.
An information model contains one or more function blocks.

and "Function Blocks":
> A function block provides an abstract view on a device to applications that want to employ the devices' functionality.
Thus, it is a consistent, self-contained set of (potentially re-usable) properties and capabilities.

{% include image.html file="pages/basic/ditto-thing-feature-definition-model.png" alt="Feature Definition Model"
    caption="One Thing can have many Features which may conform to a Definition" max-width=250 %}

### Mapping Vorto Function Block elements

A Vorto Function Block consists of 
[different sections defining state and capabilities](https://www.eclipse.org/vorto/documentation/appendix/functionblock-dsl-reference.html#function-block-dsl-semantics) 
of a device (in our case of a Feature):
> * configuration: Contains one or many configuration properties for the function block. 
* status: Contains one or many status properties for the function block. 
* fault: Contains one or many fault properties for the function block. 
* operations: Contains one or many operations for the function block. 
* events: Contains one or many events for the function block. 

#### Function Block state

The `configuration`, `status` and `fault` sections of a Function Block define the state of a Ditto Feature.

They are mapped to a corresponding JSON object inside the Feature [Properties](#feature-properties) 
which gets us following JSON structure of a Ditto Feature:

```json
{
    "feature-id": {
        "definition": [ "namespace:name:version" ],
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

The structure below `configuration, status, fault` is defined by the custom types of the Vorto Function Block. As these 
can be simple types as well as complex types, the JSON structure follows the structure of the types.

#### Function Block capabilities

The `operations` and `events` sections of a Function Block define the capabilities or behavior of a Ditto Feature.

Both are mapped to Feature [messages](basic-messages.html) sent "to" or "from" a Feature.
* a message sent **to** a Feature is mapped to an `operation`
* a messages sent **from** a Feature is mapped to an `event`

### Vorto example

{% include warning.html content="Ditto has not yet included Eclipse Vorto in order to enforce types - the following
    section can be seen as an **outlook** how Ditto would map Vorto concepts to Ditto Features." %}

Here an example for a Vorto Function Block (in Vorto's custom DSL) with the name `Lamp`. This lamp has some additional 
capabilities like detecting movement + smoke for the sake of a good example.

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

The Vorto Function Block contains all information required for a Ditto Feature Definition Identifier:
* namespace: `com.mycompany.fb`
* name: `Lamp`
* version: `1.0.0`

A Feature containing a Definition pointing to a Vorto Function Block could in the above example look like this:

```json
{
    "lamp": {
        "definition": [ "com.mycompany.fb:Lamp:1.0.0" ],
        "properties": {
            "config": {
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

The capabilities or behavior of this Feature would be defined as 5 [messages](basic-messages.html):
* with subject `smokeDetected` which is sent `FROM` a Feature containing a JSON payload with an 
    `intensity` and whether the detected smoke has reached a `critical` mass or not
* with subject `movementAlarm` which is sent `FROM` a Feature with no payload
* with subject `blink` which is sent `TO` a Feature containing a JSON payload of an `interval` 
    (as JSON number) and a duration (also as JSON number) returning a JSON boolean
* with subject `stopBlinking` which is sent `TO` a Feature with no payload returning a JSON boolean
* with subject `changeColor` which is sent `TO` a Feature containing a JSON payload which follows the type
    `Color` defined in another Vorto Function Block
