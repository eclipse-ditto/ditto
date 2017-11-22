---
title: Feature
keywords: definition, entity, feature, functionblock, informationmodel, model, vorto
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

## Example

A single feature including its Feature ID:

```json
{
    "arbitrary-feature": {
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

## Feature Definition

{% include warning.html content="Definitions based on Eclipse Vorto are currently not yet implemented, but the concept
is already clear and documented here." %}

Ditto supports contract-based development by using Definitions to ensure validity and integrity of your managed assets.
This concept is especially useful for large-scale and cross-domain solutions.

These formal Definitions are derived from device abstractions - so called “Information Models” and “Function Blocks” -
which can be modeled based on [Eclipse Vorto](http://www.eclipse.org/vorto/). 

{% include image.html file="pages/basic/ditto-thing-feature-definition-model.png" alt="Feature Definition Model"
caption="One Thing can have many Features which may conform to definitions" max-width=250 %}
