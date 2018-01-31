---
title: "Introducing Feature Definition"
published: true
permalink: 2018-01-30-feature-definition.html
layout: post
author: juergen_fickel
tags: [blog]
hide_sidebar: true
sidebar: false
---

Brace yourselves, [Eclipse Vorto](https://eclipse.org/vorto) is going to be integrated with Ditto.

## Rationale
By now you most probably represented your devices as Things with Features.
And this indeed is the proper way how it is done.
It is flexible and easy.
But wouldn't it be nice to have the possibility of providing an explicit schema for the digital twins of your devices? 
This is where Eclipse Vorto enters the game.
It enables you to define *information models* and *function blocks* which would be mapped to *Things* and *Features* 
in Ditto.

To make a Feature aware of its schema(s) the property *Definition* was introduced.
It is a means of describing the type of a Feature thus enabling validation of the the Feature properties.

{% include image.html file="pages/basic/ditto-thing-feature-definition-model.png" alt="Feature Definition Model"
    caption="One Thing can have many Features which may conform to a Definition" max-width=250 %}

Technically a definition is an array of identifier strings each of which having the form `<namespace>:<name>:<version>`.
A fully-fledged JSON representation of a Feature with a definition is shown below:

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

For diving deeper into Feature definitions please have a look at [Basic concept - Feature Definition](basic-feature.html#feature-definition).

## Validation
{% include warning.html content="Yet Ditto does not use definitions for validating the schemas of Features by itself." %}

However, nothing can stop you from enforcing types by yourself:

  1. Use the *Ditto Generator* of Vorto to generate JSON schema files from your Vorto model(s).
  2. Identify the schema files to be used by the Feature definition identifiers.
  3. Validate the JSON representation of your Feature using the JSON schema before sending it over the wire.

## Example
Please find more information and an example at [Basic concept - Feature Definition](basic-feature.html#feature-definition).

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
