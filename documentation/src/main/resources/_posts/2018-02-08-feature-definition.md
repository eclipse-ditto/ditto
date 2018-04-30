---
title: "Introducing Feature Definition"
published: true
permalink: 2018-02-08-feature-definition.html
layout: post
author: juergen_fickel
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Brace yourselves, [Eclipse Vorto](https://eclipse.org/vorto) is going to be integrated with Ditto.

## Rationale
By now you most probably represented your devices as *things* with *features*. 
This is indeed the proper way to do it.
This approach is flexible and easy so far.

But wouldn't it be nice to have the possibility of providing an explicit schema for the digital twins of your devices?
 
This is where Eclipse Vorto enters the game.
Vorto enables you to define *information models* and *function blocks* which would be mapped to *things* and *features* 
in Ditto.

To make a feature aware of a schema we have extended feature by a *definition*.
A feature definition is a means of describing the type of a feature, thus enabling the validation the integrity of 
a feature's properties.

{% include image.html file="pages/basic/ditto-thing-feature-definition-model.png" alt="Feature Definition Model"
   caption="One Thing can have many features. A feature may conform to a *definition*" max-width=250 %}

Technically, a definition is an array of identifier strings each of which having the form
 `<namespace>:<name>:<version>`.

A fully-fledged JSON representation of a feature with a definition is shown below:

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

For diving deeper into feature definitions, please have a look at [Basic concept - Feature Definition](basic-feature.html#feature-definition).

## Validation
{% include warning.html content="Yet Ditto does not use definition to validate features by itself." %}

However, nothing can stop you from enforcing types by yourself:

  1. Use the [Ditto generator](http://vorto.eclipse.org/#/generators) to generate JSON schema files from your Vorto
     model(s).
  2. Identify the schema files to be used by the Feature definition identifiers.
  3. Validate the JSON representation of your feature using the JSON schema before sending it over the wire.

## Example
Please find more information and an example at [Basic concept - Feature Definition](basic-feature.html#feature-definition).

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
