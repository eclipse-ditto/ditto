---
title: "W3C WoT (Web of Things) integration"
published: true
permalink: 2022-03-03-wot-integration.html
layout: post
author: thomas_jaeckle
tags: [blog, wot, http]
hide_sidebar: true
sidebar: false
toc: true
---

The upcoming Eclipse Ditto **version 2.4.0** will add support for [W3C WoT (Web of Things)](https://www.w3.org/WoT/) 
integration by referencing WoT Thing Model in Ditto managed twins describing the Things' capabilities.

Using this integration, Ditto managed digital twins can be linked to WoT "Thing Models" from which Ditto can create 
WoT "Thing Descriptions" containing the API descriptions of the twins.

By integrating WoT, Ditto takes a big step forward towards:
* increased interoperability
* introspection of twins to find out their capabilities 
    * which `properties` are provided and their data type and format
    * which `actions` can be invoked on the devices, including their expected input/output data type and format 
    * which `events` the devices are able to emit, including their data type and format
* addition of semantic context to Ditto managed digital twins and their capabilities
* description of Ditto twin HTTP APIs in an open, established, well specified, "web optimized", active IoT standard
* backing Ditto managed twins with WoT models, also supporting "brownfield" setups, without the need for actual devices to be aware of WoT
* opening the door to a new ecosystem of tools

To learn more about WoT (Web of Things), please visit: [Web of Things in a Nutshell](https://www.w3.org/WoT/documentation/)


## WoT integration in Ditto

The WoT integration in Ditto covers several aspects:
* referencing HTTP(s) URLs to WoT Thing Models in [Thing Definitions](basic-thing.html#definition) and in [Feature Definitions](basic-feature.html#feature-definition)
* generation of WoT Thing Descriptions for Thing and Feature instances based on referenced Thing Models
    * resolving potential [extensions via `tm:extends` and imports via `tm:ref`](https://www.w3.org/TR/2022/WD-wot-thing-description11-20220311/#thing-model-extension-import)
    * resolving potential Thing level [compositions via `tm:submodel`](https://www.w3.org/TR/2022/WD-wot-thing-description11-20220311/#thing-model-composition)
    * resolving potential [TM placeholders](https://www.w3.org/TR/2022/WD-wot-thing-description11-20220311/#thing-model-td-placeholder)
* upon creation of new Things, generation of a "JSON skeleton" following the WoT Thing Model, including referenced 
  TM submodels as Features of the Thing

For additional details about the WoT integration, please check the full 
[WoT integration documentation](basic-wot-integration.html).

The WoT integration is still marked as "experimental" as the WoT Thing Description version 1.1 is not yet published as
"W3C Recommendation" and may still change - as well as the implementation of the standard in Ditto.

{% include note.html content="In order to enable the **experimental** support for WoT in Ditto `2.4.0`, please
      configure the environment variable `DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED` to `true`." %}

## Example 

For a full example of the different aspects of the WoT integration, please check the full 
[WoT integration example](basic-wot-integration-example.html).

To summarize:
* "link" a Thing with a publicly available WoT Thing Model by specifying the URL in its [Thing Definition](basic-thing.html#definition).
* creation of a new Thing can use a Thing Model (e.g. the example model [https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld](https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld)) in order to generate a JSON skeleton:
    ```bash
    curl --location --request PUT -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
    --header 'Content-Type: application/json' \
    --data-raw '{
        "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld"
    }'
    ```
* which results in a Thing like this:
  ```json
  {
    "thingId": "io.eclipseprojects.ditto:floor-lamp-0815",
    "policyId": "io.eclipseprojects.ditto:floor-lamp-0815",
    "definition": "https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld",
    "attributes": {
      "manufacturer": "",
      "serialNo": ""
    },
    "features": {
      "Spot1": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
        ],
        "properties": {
          "dimmer-level": 0.0,
          "color": {
            "r": 0,
            "g": 0,
            "b": 0
          },
          "on": false
        }
      },
      "Spot2": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
        ],
        "properties": {
          "dimmer-level": 0.0,
          "color": {
            "r": 0,
            "g": 0,
            "b": 0
          },
          "on": false
        }
      },
      "Spot3": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/dimmable-colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
        ],
        "properties": {
          "dimmer-level": 0.0,
          "color": {
            "r": 0,
            "g": 0,
            "b": 0
          },
          "on": false
        }
      },
      "ConnectionStatus": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/connection-status-1.0.0.tm.jsonld"
        ],
        "properties": {
          "readySince": "",
          "readyUntil": ""
        }
      },
      "PowerConsumptionAwareness": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/power-consumption-aware-1.0.0.tm.jsonld"
        ],
        "properties": {
          "reportPowerConsumption": {}
        }
      },
      "SmokeDetection": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/smoke-detector-1.0.0.tm.jsonld"
        ]
      },
      "Status-LED": {
        "definition": [
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/colored-lamp-1.0.0.tm.jsonld",
          "https://eclipse-ditto.github.io/ditto-examples/wot/models/switchable-1.0.0.tm.jsonld"
        ],
        "properties": {
          "color": {
            "r": 0,
            "g": 0,
            "b": 0
          },
          "on": false
        }
      }
    }
  }
  ```
* the WoT Thing Description of Things containing a Thing Model in their `"definition"` may then be retrieved by invoking 
  the existing endpoint<br/>
  `GET /api/2/things/<thingId>` with the `Accept` header `application/td+json`:
  ```bash
  curl --location --request GET -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815' \
  --header 'Accept: application/td+json'
  ```
* Features of Things are handled as WoT "submodels" and also can describe themselves with the same approach, e.g.: 
  ```bash
  curl --location --request GET -u ditto:ditto 'https://ditto.eclipseprojects.io/api/2/things/io.eclipseprojects.ditto:floor-lamp-0815/features/Spot1' \
  --header 'Accept: application/td+json'
  ```


## Ditto WoT Java model

As part of the integration of WoT in Ditto, a Java module has been added to provide a Java API for the WoT 
"Thing Description"/"Thing Model" and their parts - this module can also be used separately from Ditto in order to
e.g. have a builder based API for building new objects or to read a TD/TM from a string:

```xml
<dependency>
    <groupId>org.eclipse.ditto</groupId>
    <artifactId>ditto-wot-model</artifactId>
    <version>${ditto.version}</version> <!-- the ditto-wot-model is available since "ditto.version" 2.4.0 -->
</dependency>
```

Please have a look at the added [ditto-wot-model module](https://github.com/eclipse-ditto/ditto/tree/master/wot/model) to find
out more about example usage.


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
