---
title: WoT (Web of Things) integration
keywords: WoT, TD, TM, ThingDescription, ThingModel, W3C, Semantic, Model, definition, ThingDefinition, FeatureDefinition
tags: [wot]
permalink: basic-wot-integration.html
---

Eclipse Ditto added support for **optional** WoT (Web of Things) integration in Ditto version `2.4.0`.  
The integration is based on the
[Web of Things (WoT) Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/).

Using this integration, Ditto managed digital twins can be linked to WoT "Thing Models" from which Ditto can create
WoT "Thing Descriptions" containing the API descriptions of the twins.

The WoT integration is considered stable and therefore active by default starting with Ditto version `3.0.0`.  
If it should be disabled, it can be deactivated via a "feature toggle":  
In order to deactivate the WoT integration, configure the following environment variable for all Ditto services:

```bash
DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=false
```


## Web of Things

> "The Web of Things seeks to counter the fragmentation of the IoT through standard 
> complementing building blocks (e.g., metadata and APIs) that enable easy integration across IoT platforms and 
> application domains."

[Source](https://www.w3.org/groups/wg/wot)

The W3C WoT (Web of Things) working group provides "building blocks" under the roof of the 
"main international standards organization for the World Wide Web" to 
"\[...\] simplify IoT application development by following the well-known and successful Web paradigm.".

Source: [Web of Things in a Nutshell](https://www.w3.org/WoT/documentation/)

At its core, the specification of the so-called "WoT Thing Description" (TD) defines IoT device's metadata and 
interaction capabilities.  
The idea of such a TD (Thing Description) is that a device (or e.g. a digital twin service acting as intermediate) 
describes in a standardized way which capabilities in form of `properties`, `actions` and `events` a Thing provides 
and which input/output can be expected when interacting with the Thing.  

Even more, a TD contains so called `forms` for the mentioned interaction capabilities which map those rather abstract
concepts to actual endpoints, e.g. to HTTP endpoints, HTTP verbs and HTTP headers etc.  
Developed under the roof of the W3C, web APIs are obviously well understood and are incorporated perfectly in the 
"WoT Thing Description" specification.
But also other protocol bindings may be defined in a `form`, e.g. MQTT or CoAP.

The "WoT Thing Description" specification version 1.0 was already published as 
["W3C Recommendation" in April 2020](https://www.w3.org/TR/wot-thing-description/), the next version 1.1 adds the 
concept of "Thing Models" (TM) which can be seen as a template for generating "Thing Descriptions" but without some of
its mandatory fields, e.g. `forms` including the protocol bindings.

With the addition of the "Thing Model" concept, WoT becomes a perfect fit for describing the capabilities of 
[Digital Twins](intro-digitaltwins.html) managed in Ditto.
It is completely optional and even possible as "retrofit" model addition for already connected devices / already existing twins.

The benefits of adding such a "Thing Model" reference to digital twins managed in Ditto are:
* possibility to define model for data (Ditto Thing `attributes` + Ditto Feature `properties`), e.g. containing:
    * data type to expect
    * restrictions which apply (like e.g. possible min/max values)
    * default values to assume if data is not available
    * units of data entries
* possibility to define model for messages
    * same as above for data
    * in addition, describing possible input/output and also possible error situations when invoking a message
* capability to provide semantic context (using JSON-LD), e.g. by referencing to existing ontologies like:
  * saref: [https://ontology.tno.nl/saref/](https://ontology.tno.nl/saref/)
  * OM-2 "Ontology of units of measure": [http://www.ontology-of-units-of-measure.org/page/om-2](http://www.ontology-of-units-of-measure.org/page/om-2)
  * or any other JSON-LD described ontology
* interoperability
    * different IoT systems and devices may engage with each other in a standardized way
    * models and included data formats are not proprietary, but are defined in an open standard
    * avoids vendor lock-in, even a lock-in to Ditto
* introspection of digital twins
    * Ditto managed twins can describe themselves
    * if backed by a WoT Thing Model, the twin can tell you (when asking for `Accept: application/td+json` concent-type) exactly 
        * what it is capable of
        * which HTTP endpoints to invoke to access data / send messages
* advantage of being backed by an open standard
    * W3C are experts on the web, the WoT standard relies on already established other standards like HTTP, JSON, 
      JSON-LD, JsonSchema, JsonPointer, etc. 
    * the standard is in active development
    * many eyes (e.g. well known industry players like Siemens, Intel, Oracle, Fujitsu, ...) review additions, etc.
    * W3C performs a completely open specification process, no decision is made secretly, even the meeting minutes are 
      totally public and the specifications develop over GitHub, making it open to review
* utilization of the tooling landscape / open ecosystem evolving around the WoT standard, e.g.:
    * the [Eclipse edi{TD}or](https://eclipse.github.io/editdor/) online-editor for WoT TDs and TMs
    * the [Eclipse thingweb.node-wot](https://github.com/eclipse/thingweb.node-wot) project providing 
      [node.wot NPM modules](https://www.npmjs.com/org/node-wot), e.g. in order to "consume" Thing Descriptions in 
      Javascript
    * a [node-red library for WoT integration](https://flows.nodered.org/node/node-red-contrib-web-of-things)


### WoT Thing Description

A [Thing Description](https://www.w3.org/TR/wot-thing-description11/#introduction-td) describes exactly one instance of 
a device/Thing.  
This description contains not only the interaction capabilities (`properties` the devices provide, 
`actions` which can be invoked by the devices and `events` the devices emit), but also contain concrete API endpoints 
(via the `forms`) to invoke in order to actually interact with the devices.

### WoT Thing Model

A [Thing Model](https://www.w3.org/TR/wot-thing-description11/#introduction-tm) can be seen as the model 
(or interface in OOP terminology) for a potentially huge population of instances (Thing Descriptions) all "implementing"
this contract.  
It does not need to contain the instance specific parts which a TD must include (e.g. `security` definitions or `forms`).
It focuses on the possible interactions (`properties`, `actions`, `events`) and their data types / semantics in a more 
abstract way.

#### Thing Model modeling tips

Here are some tips you should consider when writing new WoT Thing Models to describe the capabilities of your devices:

* put a version in your Thing Model filename:
    * The WoT specification does not require you to put the version of the Thing Model in the filename, however you should
      really do so.
    * If you don't use a version and provide the file as `lamp.tm.jsonld`, you will probably overwrite the file whenever
      you make a change to the model.
    * Think about an application consuming that model - one day a Thing would be correctly defined by the model,
      but the next day the Thing would no longer follow the model, even if the URL to the model was not changed. 
* apply semantic versioning:
    * if you "fix a bug" in a model, increase the "micro" version: `1.0.X`
    * if you add something new to a model without removing/renaming (breaking) existing definitions, increase the "minor" version: `1.X.0`
    * if you need to break a model (e.g. by removing/renaming something or changing a datatype), increase the "major" version: `X.0.0`
* treat published Thing Models as "immutable":
    * never change a "released" TM once published and accessible via public HTTP endpoint
* provide a `title` and a `description` for your Thing Models
    * you as the model creator can add the most relevant human-readable descriptions
    * also define it for `properties`, `actions`, `events` and all defined data types
    * if you need internationalization, add those to `titles` and `descriptions` 
* provide a semantic context by referencing ontologies in your JSON-LD `@context`
    * at some point, a machine learning or reasoning engine will try to make sense of your Things and their data
    * support the machines to be able to understand your Things semantics
    * find out more about Linked Data and JSON-LD here: [https://json-ld.org](https://json-ld.org)
    * e.g. make use of [public ontologies](#public-available-ontologies-to-reference)
* use the linked ontologies in order to describe your model in a semantic way, e.g. in the `properties` of a 
  "Temperature sensor" model 
  (see also the [example in the TD specification](https://www.w3.org/TR/wot-thing-description11/#semantic-annotations-example-version-units)):
    ```json
    {
      "properties": {
        "currentTemperature": {
          "@type": "om2:CelsiusTemperature",
          "title": "Current temperature",
          "description": "The last or current measured temperature in '°C'.",
          "type": "number",
          "unit": "om2:degreeCelsius",
          "minimum": -273.15
        }
      }
    }
    ```

#### Public available ontologies to reference

Here we listed some ontologies which you can use in order to provide semantic context:

To describe time related data (e.g. durations, timestamps) you can use: [W3C Time Ontology](https://www.w3.org/TR/owl-time/)  
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "time": "http://www.w3.org/2006/time#"
    }
  ]
}
```

To describe geolocations you can use: [W3C Basic Geo (WGS84 lat/long) Vocabulary](https://www.w3.org/2003/01/geo/):  
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "geo": "http://www.w3.org/2003/01/geo/wgs84_pos#"
    }
  ]
}
```

To describe units you can choose between:
* [QUDT.org](https://www.qudt.org)  
  ```json
  {
    "@context": [
      "https://www.w3.org/2022/wot/td/v1.1",
      {
        "qudt": "http://qudt.org/schema/qudt/",
        "unit": "http://qudt.org/vocab/unit/",
        "quantitykind": "http://qudt.org/vocab/quantitykind/"
      }
    ]
  }
  ```
* [OM 2: Units of Measure](http://www.ontology-of-units-of-measure.org/page/om-2):  
  ```json
  {
    "@context": [
      "https://www.w3.org/2022/wot/td/v1.1",
      {
        "om2": "http://www.ontology-of-units-of-measure.org/resource/om-2/"
      }
    ]
  }
  ```

To describe "assets" you can use: [SAREF](https://ontology.tno.nl/saref/)  
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "saref": "https://w3id.org/saref#"
    }
  ]
}
```


## Mapping of WoT concepts to Ditto

Mapping a WoT Thing Description (TD) to a Ditto [Thing](basic-thing.html) can be done in different "complexity levels":
1. the most simple mapping is that a WoT TD describes exactly one Ditto Thing
2. another possible mapping is that a WoT TD describes exactly one Ditto [Feature](basic-feature.html) (being part of a Thing)
3. the most advanced option is that:
    * a WoT TD describes a Ditto Thing
    * and in addition contains "sub Things" as Features of the Thing, all described by their own TD

The third option adds the possibility to provide common aspects which many similar devices support as part of the similar 
devices, all implementing the same "contract" (Thing Model) with the same interaction capabilities 
(`properties`, `actions` and `events`), the same data formats and semantic context.

### Thing Description vs. Thing Model

A WoT TD describes the instance (the Ditto Thing), a WoT TM provides the model of a Thing. The model can be referenced
in the [Thing Definition](basic-thing.html#definition) with its HTTP endpoint.
Ditto supports adding a valid HTTP(s) URL in the `"definition"`.

The same applies for the [Feature Definition](basic-feature.html#feature-definition) which may also contain HTTP
endpoints to a valid WoT Thing Model.

Thing Descriptions are generated by the WoT integration in Ditto, based on the Thing Models referenced in a 
[Thing Definition](basic-thing.html#definition) and in [Feature Definitions](basic-feature.html#feature-definition).

### Thing Model describing a Ditto Thing

This table shows an overview of how those elements map to Ditto concepts for the "Thing" level:

| WoT element                                                                                             | Ditto concept                                                                                                      |
|---------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| [Thing](https://www.w3.org/TR/wot-thing-description11/#thing)                                           | [Ditto Thing](basic-thing.html)                                                                                    |
| [Properties](https://www.w3.org/TR/wot-thing-description11/#propertyaffordance)                         | Thing [attributes](basic-thing.html#attributes)                                                                    |
| [Actions](https://www.w3.org/TR/wot-thing-description11/#actionaffordance)                              | Thing [messages](basic-messages.html#elements) with **Direction** *to* (messages in the "inbox") of a Thing ID.    |
| [Events](https://www.w3.org/TR/wot-thing-description11/#eventaffordance)                                | Thing [messages](basic-messages.html#elements) with **Direction** *from* (messages in the "outbox") of a Thing ID. |
| [Composition via `tm:submodel`](https://www.w3.org/TR/wot-thing-description11/#thing-model-composition) | Thing [features](basic-thing.html#features) representing different aspects of a Ditto Thing.                       |


### Thing Model describing a Ditto Feature

This table shows an overview of how those elements map to Ditto concepts for the "Feature" level:

| WoT element                                                                     | Ditto concept                                                                                                                                                                     |
|---------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Thing](https://www.w3.org/TR/wot-thing-description11/#thing)                   | Feature.<br/>In Ditto, a Feature is an aspect of a [Ditto Thing](basic-thing.html). As the Feature is defined by its properties and messages it supports, it maps to a WoT Thing. |
| [Properties](https://www.w3.org/TR/wot-thing-description11/#propertyaffordance) | Feature [properties](basic-feature.html#feature-properties)                                                                                                                       |
| [Actions](https://www.w3.org/TR/wot-thing-description11/#actionaffordance)      | Feature [messages](basic-messages.html#elements) with **Direction** *to* (messages in the "inbox") of a Thing ID + Feature ID combination.                                        |
| [Events](https://www.w3.org/TR/wot-thing-description11/#eventaffordance)        | Feature [messages](basic-messages.html#elements) with **Direction** *from* (messages in the "outbox") of a Thing ID + Feature ID combination.                                     |


## Integration in Ditto

The WoT integration in Ditto covers several aspects:
* referencing HTTP(s) URLs to WoT Thing Models in [Thing Definitions](basic-thing.html#definition) and in [Feature Definitions](basic-feature.html#feature-definition)
* generation of WoT Thing Descriptions for Thing and Feature instances based on referenced Thing Models
    * resolving potential [extensions via `tm:extends` and imports via `tm:ref`](https://www.w3.org/TR/wot-thing-description11/#thing-model-extension-import)
    * resolving potential Thing level [compositions via `tm:submodel`](https://www.w3.org/TR/wot-thing-description11/#thing-model-composition)
    * resolving potential [TM placeholders](https://www.w3.org/TR/wot-thing-description11/#thing-model-td-placeholder)
* upon creation of new Things, generation of a "JSON skeleton" following the WoT Thing Model, including referenced TM submodels as Features of the Thing 

### Thing Description generation

WoT Thing Models are intended to be used as templates for generating (instance specific) Thing Descriptions, 
the rules for doing that are specified: 
[Derivation of Thing Description Instances](https://www.w3.org/TR/wot-thing-description11/#thing-model-td-generation)

Prerequisites to use the Thing Description generation:
* the feature toggle (environment variable `DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=true`) is activated
* HTTP content negotiation is applied, setting the `Accept` header to the registered WoT TD
  [IANA content type `application/td+json`](https://www.iana.org/assignments/media-types/application/td+json)
  when retrieving the Thing via HTTP `GET /api/2/<namespace>:<thing-name>`

The available configuration of the WoT integration can be found in the 
[things.conf](https://github.com/eclipse-ditto/ditto/blob/master/things/service/src/main/resources/things.conf)
config file of the [things service](architecture-services-things.html) at path `ditto.things.wot`.  
There you can configure which `securityDefinitions` shall be added to the generated TDs and which `base` path 
prefix to create into the TDs, depending on your public Ditto endpoint.

#### Security: TD access / authorization

For accessing Thing Descriptions created for Things and Features no special permission in the Thing's 
[Policy](basic-policy.html) is required.

As the Thing Model must be a publicly available resource and the Thing ID is also known for a user requesting the TD of 
a Thing, there is no additional information to disclose.

Accessing the `properties`, invoking `actions` and subscribing for `events` is of course authorized by the Thing's 
Policy via its [Thing](basic-policy.html#thing), [Feature](basic-policy.html#feature) and 
[Message](basic-policy.html#message) resources.

#### TD generation for Things

Additional prerequisites that a WoT TD is generated for a Ditto Thing:
* the Thing references a valid WoT Thing Model in its [Thing Definition](basic-thing.html#definition) and this TM is
  publicly downloadable via its HTTP(s) URL

Function:
* Ditto checks if the Thing Definition contains a valid HTTP(s) URL
* Ditto downloads the referenced URL and checks if this is a valid WoT Thing Model
* Ditto saves the downloaded TM to a local cache
* Ditto generates a WoT Thing Description and returns it as JSON response
    * defined TM `tm:extends` extensions are resolved by downloading those TMs as well 
    * defined TM `tm:refs` imports are also resolved by downloading those TMs as well 
    * defined TM `tm:submodel`s are added to the `links` of the TD pointing to the TDs of the Features of the Thing
    * metadata available in the Thing or in the Ditto configuration is also included in the generated TD

Using cURL, the Thing Description for a Ditto Thing can be generated and fetched by invoking:

```bash
curl -u ditto:ditto 'http://localhost:8080/api/2/things/io.eclipsepojects.ditto:my-thing' \
  --header 'Accept: application/td+json'
```

#### TD generation for Features

Additional prerequisites that a WoT TD is generated for a Ditto Feature:
* the Feature references at least one valid WoT Thing Model in its
  [Feature Definition](basic-feature.html#feature-definition) and this TM is publicly downloadable via its HTTP(s) URL

Function:
* Ditto checks if the first Feature Definition in the list of definitions contains a valid HTTP(s) URL
    * additional Feature Definitions in the array are interpreted as extended TMs in order to specify the "extension hierarchy"
* Ditto downloads the referenced URL and checks if this is a valid WoT Thing Model
* Ditto saves the downloaded TM to a local cache
* Ditto generates a WoT Thing Description and returns it as JSON response
    * defined TM `tm:extends` extensions are resolved by downloading those TMs as well
    * defined TM `tm:refs` imports are also resolved by downloading those TMs as well
    * metadata available in the Thing or in the Ditto configuration is also included in the generated TD

Using cURL, the Thing Description for a Ditto Feature can be generated and fetched by invoking:

```bash
curl -u ditto:ditto 'http://localhost:8080/api/2/things/io.eclipsepojects.ditto:my-thing/features/my-feature-1' \
  --header 'Accept: application/td+json'
```

#### Resolving Thing Model placeholders

WoT Thing Models may contain [placeholders](https://www.w3.org/TR/wot-thing-description11/#thing-model-td-placeholder)
which **must** be resolved during generation of the TD from a TM.  

In order to resolve TM placeholders, Ditto applies the following strategy:
* when generating a TD for a Thing, it looks in the Things' attribute `"model-placeholders"` (being a JSONObject) in
   order to lookup placeholders
* when generating a TD for a Feature, it looks in the Feature's property `"model-placeholders"` (being a JSON Object) in
   order to lookup placeholders
* when a placeholder was not found in the `"model-placeholders"` of the Thing/Feature, a fallback to the Ditto 
  configuration is done:
    * placeholder fallbacks can be configured in Ditto via the 
      [things.conf](https://github.com/eclipse-ditto/ditto/blob/master/things/service/src/main/resources/things.conf) 
      configuration file of the [things service](architecture-services-things.html) at path 
      `ditto.things.wot.to-thing-description.placeholders`.<br/>  
      This map may contain static values, but use and JSON type as value (e.g. also a JSON Object), e.g.:
      ```
      FOO = "bar"
      TM_OPTIONAL = [
        "/properties/status",
        "/actions/toggle"
      ]
      ```

{% include warning.html content="Please be aware that placeholders put into the `\"model-placeholders\"` attribute/property 
    of a Thing/Feature may be used in TM placeholders and therefore are not 
    protected by any authorization check based on the Thing's [Policy](basic-policy.html) as TDs are available for all
    authenticated users which know the Thing ID."
%}

### Thing skeleton generation upon Thing creation

Prerequisites to use the skeleton generation during Thing creation:
* the feature toggle (environment variable `DITTO_DEVOPS_FEATURE_WOT_INTEGRATION_ENABLED=true`) is activated
* the created Thing references a valid WoT Thing Model in its [Thing Definition](basic-thing.html#definition) and this 
  TM is publicly downloadable via its HTTP(s) URL

Function:
* Ditto checks if the Thing Definition contains a valid HTTP(s) URL
* Ditto downloads the referenced URL and checks if this is a valid WoT Thing Model
* Ditto saves the downloaded TM to a local cache
* Ditto uses the Thing Model as template for generating JSON elements:
    * attributes based on the contained TM `properties`
    * features based on the contained TM `tm:submodel`s using the `instanceName` as Feature ID
        * properties based on the submodel `properties`
* when a `default` is specified for the TM property, this default is used as value, otherwise the "neutral element" of 
  the datatype is used as initial value of the property
* if any error happens during the skeleton creation (e.g. a Thing Model can't be downloaded or is invalid),
  the Thing is created without the skeleton model, just containing the specified `"definition"`

### Thing Model based validation of changes to properties, action and event payloads

Since Ditto `3.6.0` it is possible to configure Ditto in a way so that all modification to the persisted things are validated
according to a linked to WoT Thing Model (TM).  
That way, Ditto can ensure that Ditto managed things **always** comply with a previously defined (WoT Thing) model.

This takes the WoT integration on a new level, as WoT TMs are not only used to 
[create a Thing skeleton when creating a thing](#thing-skeleton-generation-upon-thing-creation), 
ensuring the payload and structure of a thing once on creation.  
But over the lifetime of a digital twin it is always enforced that the payload follows the model 
(e.g. in regard to data types, ranges, patterns, etc.).

The implementation supports validation or enforcing the following Ditto concepts:
* On Thing level:
  * Modification of a Thing's [definition](basic-thing.html#definition) causes validation of the existing thing against the new TM
  * Deletion of a Thing's [definition](basic-thing.html#definition) is forbidden (to not be able to bypass validation)
  * Thing [attributes](basic-thing.html#attributes): 
    * based on the linked TM of a Thing's [definition](basic-thing.html#definition),  the Thing `attributes` have to 
      follow the contract defined by the TM `properties`
    * by default, non-modeled `attributes` are forbidden to be created/updated
    * `attributes` which are not marked as optional (via `"tm:optional"`) are ensured not to be deleted
  * Thing [messages](basic-messages.html#sending-messages): 
    * based on the linked TM of a Thing's [definition](basic-thing.html#definition), the Thing `messages` 
      * sent to its `inbox` have to follow the contract defined by the TM `actions`
        * both, the defined `input` payload and the `output` payload is validated
        * by default, non-modeled `inbox` `messages` are forbidden to be sent
      * sent from its `outbox` have to follow the contract defined by the TM `events`
        * the defined `data` payload is validated
        * by default, non-modeled `outbox` `messages` are forbidden to be sent
  * When modifying (or merging) the complete Thing or all of the features at once:
    * it is ensured that no defined features get effectively "removed"
    * it is ensured that only defined and known features are accepted
* On Feature level:
  * Modification of a Feature's [definition](basic-feature.html#feature-definition) causes validation of the existing feature against the new TM
  * Deletion of a Feature's [definition](basic-feature.html#feature-definition) is forbidden (to not be able to bypass validation)
  * Feature [properties](basic-feature.html#feature-properties): 
    * based on the linked TM of a Feature's [definition](basic-feature.html#feature-definition), the Feature `properties` 
      have to follow the contract defined by the TM `properties`
  * Feature [messages](basic-messages.html#sending-messages):
    * based on the linked TM of a Feature's [definition](basic-feature.html#feature-definition), the Feature `messages`
      * sent to its `inbox` have to follow the contract defined by the TM `actions`
          * both, the defined `input` payload and the `output` payload is validated
          * by default, non-modeled `inbox` `messages` are forbidden to be sent
      * sent from its `outbox` have to follow the contract defined by the TM `events`
          * the defined `data` payload is validated
          * by default, non-modeled `outbox` `messages` are forbidden to be sent

### Thing Model based validation error

When Ditto detects an API call which was not valid according to the model, an HTTP status code `400` (Bad Request) will
be returned to the caller, containing a description of the encountered validation violation.

The basic structure of the WoT validation errors is defined by [the error model](basic-errors.html).  
The `error` field will always be `"wot:payload.validation.error"` - and the `message` will also always be the same.  
The `description` however will contain a specific text and the `"validationDetails"` field contains a map of 
erroneous Json pointer paths. 

The list of collected errors might however not be complete (e.g. for the whole thing), as Ditto will for a 
complete [Thing](basic-thing.html) update first its `attributes` and then its `featuers` (one after another) 
and will fail fast (instead of validating the complete thing) if validation errors are detected.

An example payload when e.g. sending the wrong datatype for a Thing "attribute" could look like:
```json
{
  "status": 400,
  "error": "wot:payload.validation.error",
  "message": "The provided payload did not conform to the specified WoT (Web of Things) model.",
  "description": "The Thing's attribute </serial> contained validation errors, check the validation details.",
  "validationDetails": {
    "/attributes/serial": [
      ": {type=boolean found, string expected}"
    ]
  }
}
```

An example where e.g. a `"required"` field of a `"type": "object"` WoT property was missing would be, for example:
```json
{
  "status": 400,
  "error": "wot:payload.validation.error",
  "message": "The provided payload did not conform to the specified WoT (Web of Things) model.",
  "description": "The Feature <connectivity>'s property </status> contained validation errors, check the validation details.",
  "validationDetails": {
    "/features/connectivity/properties/status": [
      ": {required=[required property 'updatedAt' not found, required property 'message' not found]}"
    ]
  }
}
```

If all feature properties should be updated at once, but no payload was provided, this would list the non-optional 
WoT properties in the error response:
```json
{
  "status": 400,
  "error": "wot:payload.validation.error",
  "message": "The provided payload did not conform to the specified WoT (Web of Things) model.",
  "description": "Required JSON fields were missing from the Feature <sensor>'s properties",
  "validationDetails": {
    "/features/sensor/properties/value": [
      "Feature <sensor>'s property <value> is non optional and must be provided"
    ],
    "/features/sensor/properties/updatedAt": [
      "Feature <sensor>'s property <updatedAt> is non optional and must be provided"
    ]
  }
}
```

#### Model evolution with the help of Thing Model based validation

This new validation will also make it possible to evolute things conforming to a (WoT) model to e.g. a new minor version
with added functionality and also provide means for "migrating" things to breaking (major) model versions.

When updating the Thing's [definition](basic-thing.html#definition) to another version, the Ditto WoT validation will
check if the payload of the Thing is valid according to the new definition.  
If it is not valid (e.g. because a new feature was added to the Thing's model which is not yet in the Thing's payload),
validation will fail with an error:

Example assuming to update a Thing's `definition` from version `1.0.0` to version `1.1.0`
(assuming where a new submodel `coffeeMaker` was added):
```
PUT /api/2/things/org.eclipse.ditto:my-thing-1/definition

payload:
"https://some.domain/some-model-1.1.0.tm.jsonld"
```

The response would e.g. be:
```json
{
  "status": 400,
  "error": "wot:payload.validation.error",
  "message": "The provided payload did not conform to the specified WoT (Web of Things) model.",
  "description": "Attempting to update the Thing with missing in the model defined features: [coffeeMaker]"
}
```

This could be solved by doing a `PATCH` update instead, updating both the Thing's `definition` together with the
missing payload (assuming the `coffee-1.0.0` model contains just a required property `counter`):
```
PATCH /api/2/things/org.eclipse.ditto:my-thing-1

headers:
Content-Type: application/merge-patch+json

payload:
{
  "definition": "https://some.domain/some-model-1.1.0.tm.jsonld",
  "features": {
    "coffeeMaker": {
      "definition": [
        "https://some.domain/submodels/coffee-1.0.0.tm.jsonld"
      ],
      "properties": {
        "counter": 0
      }
    }
  }
}
```

#### Configuration of Thing Model based validation

Starting with Ditto `3.6.0`, the WoT based validation against Thing Models is enabled by default.  
It however can be completely disabled by configuring the environment variable `THINGS_WOT_TM_MODEL_VALIDATION_ENABLED` to `false`.

Every single validation aspect is configurable separately in Ditto (by default all aspects are enabled).  
Please have a look at e.g. the Helm chart configuration, level `things.config.wot.tmValidation`, in order to find all 
available configuration options.  
Or check the [things.conf](https://github.com/eclipse-ditto/ditto/blob/master/things/service/src/main/resources/things.conf)
(key `ditto.things.wot.tm-model-validation`) to also find out the options and with which environment variables they 
can be overridden.

The configuration can also be applied dynamically based on:
* the presence of certain Ditto headers (e.g. a `ditto-originator` - the connection or user which "caused" an API call)
* the name/URL of the Thing's WoT Thing Model (TM)
* the name/URL of the Feature's WoT Thing Model (TM)

And example for the dynamic configuration, which would override the static configuration for 
* a specific user doing the API call
* AND doing the API call to a Thing with a specific model 

would be the following HOCON configuration:
```hocon
things {
  wot {
    tm-model-validation {
      enabled = true

      dynamic-configuration = [
        {
          validation-context {
            // all 3 "patterns" conditions have to match (AND)
            ditto-headers-patterns = [      // if any (OR) of the contained headers block match
              {
                // inside the object, all patterns have to match (AND)
                ditto-originator = "^pre:ditto$"
              }
            ]
            thing-definition-patterns = [   // if any (OR) of the contained patterns match
              "^https://eclipse-ditto.github.io/ditto-examples/wot/models/floor-lamp-1.0.0.tm.jsonld$"
            ]
            feature-definition-patterns = [ // if any (OR) of the contained patterns match
            ]
          }
          // if the validation-context "matches" a processed API call, apply the following overrides:
          config-overrides {
            // enabled = false // we could deactivate the complete WoT Thing Model validation with this config
            thing {
              // disable some aspects of Thing validation
              enforce {
                attributes = false
              }
              forbid {
                thing-description-deletion = false
              }
            }
            feature {
              // disable some aspects of Feature validation
              enforce {
                properties = false
              }
              forbid {
                feature-description-deletion = false
              }
            }
          }
        }
      ]
    }
  }
}
```


## Ditto WoT Extension Ontology

As WoT is built on JSON-LD, extension of Thing Models (TMs) or Thing Descriptions (TDs) via a custom ontology is possible.
Ditto provides such a "WoT extension ontology" with additional terms for:
* **Categorization** of WoT properties (e.g., grouping into "configuration" vs. "status")
* **Deprecation notices** for Things, properties, actions, and events (marking them as deprecated with replacement info and removal timeline)

The Ditto WoT Extension Ontology can be found here:
[https://ditto.eclipseprojects.io/wot/ditto-extension#](https://ditto.eclipseprojects.io/wot/ditto-extension#)

It contains an HTML description of the contained terms of the ontology.

To use the Ditto WoT extension in your Thing Models or Thing Descriptions, add it to your JSON-LD `@context`:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"
    }
  ]
}
```

### Ditto WoT Extension: category

The [category](https://ditto.eclipseprojects.io/wot/ditto-extension#category) is a term which can be added in scope of
WoT TM/TD [Property Affordances](https://www.w3.org/TR/wot-thing-description11/#propertyaffordance).  
It can be used to apply an optional categorization of the property.

Such a category could, for example, be a categorization of configuration vs. status properties.

In order to use that categorization, you need to enhance your JSON-LD context with the Ditto WoT extension. You can then
make use of the `"category"` in properties defined in your TM/TD.

Example:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#",
      "om2": "http://www.ontology-of-units-of-measure.org/resource/om-2/"
    }
  ],
  ...
  "properties": {
    "on": {
      "title": "On",
      "type": "boolean",
      "ditto:category": "configuration"
    },
    "power-consumption": {
      "@type": "om2:Power",
      "title": "Power consumption",
      "type": "number",
      "unit": "om2:kilowatt",
      "ditto:category": "status"
    }
  }
}
```

The effect of this Ditto `"category"` for properties is the following:
* for [Things TD generation](#td-generation-for-things) the category will be used as an additional path element in the
  created `"href"` link of the attribute.
* for [Feature TD generation](#td-generation-for-features) the category will be used as an additional path element in 
  the created `"href"` link of the property.
* for [Thing skeleton generation](#thing-skeleton-generation-upon-thing-creation) the category will be used as a 
  "grouping JSON object".  
  All WoT properties with the same category will be placed in either an attribute or a feature property JSON object 
  having the category name.

Based on the example above, a generated feature JSON would for example look like this:
```json
{
  "definition": [
    "https://some.domain/some.tm.jsonld"
  ],
  "properties": {
    "configuration": {
      "on": false
    },
    "status": {
      "power-consumption": 0.0
    }
  }
}
```

### Ditto WoT Extension: deprecationNotice

The [deprecationNotice](https://ditto.eclipseprojects.io/wot/ditto-extension#deprecationNotice) is a term which can be
added at the WoT TM/TD (Thing) level, or in scope of
[Property Affordances](https://www.w3.org/TR/wot-thing-description11/#propertyaffordance),
[Action Affordances](https://www.w3.org/TR/wot-thing-description11/#actionaffordance), and
[Event Affordances](https://www.w3.org/TR/wot-thing-description11/#eventaffordance).
It can be used to mark an entire Thing Model/Description or individual affordances as deprecated and provide
information about their replacement and removal timeline.

The `deprecationNotice` is a JSON object containing the following properties:

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `deprecated` | boolean | yes | Whether the Thing or affordance is deprecated |
| `supersededBy` | string | no | A JSON Pointer (RFC 6901) referencing the replacement affordance (e.g. `#/properties/newProperty`), or a URL to a replacement Thing Model |
| `removalVersion` | string | yes | The semantic version (SemVer) in which the deprecated Thing or affordance will be removed |

In order to use the deprecation notice, you need to enhance your JSON-LD context with the Ditto WoT extension. You can
then make use of the `"deprecationNotice"` in properties, actions, or events defined in your TM/TD.

Example for a deprecated property with a replacement:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"
    }
  ],
  ...
  "properties": {
    "tempSetpoint": {
      "title": "Temperature Setpoint (DEPRECATED)",
      "description": "Use 'targetTemperature' instead",
      "type": "number",
      "ditto:deprecationNotice": {
        "deprecated": true,
        "supersededBy": "#/properties/targetTemperature",
        "removalVersion": "2.0.0"
      }
    },
    "targetTemperature": {
      "title": "Target Temperature",
      "type": "number",
      "unit": "om2:degreeCelsius"
    }
  }
}
```

Example for a deprecated action without a replacement:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"
    }
  ],
  ...
  "actions": {
    "legacyReset": {
      "title": "Legacy Reset (DEPRECATED)",
      "description": "This action will be removed without replacement",
      "ditto:deprecationNotice": {
        "deprecated": true,
        "removalVersion": "3.0.0"
      }
    }
  }
}
```

Example for a deprecated event with a replacement:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"
    }
  ],
  ...
  "events": {
    "temperatureChanged": {
      "title": "Temperature Changed (DEPRECATED)",
      "description": "Subscribe to 'temperatureUpdate' instead for richer data",
      "data": {
        "type": "number"
      },
      "ditto:deprecationNotice": {
        "deprecated": true,
        "supersededBy": "#/events/temperatureUpdate",
        "removalVersion": "2.0.0"
      }
    },
    "temperatureUpdate": {
      "title": "Temperature Update",
      "description": "Emitted when the measured temperature changes",
      "data": {
        "type": "object",
        "properties": {
          "value": { "type": "number" },
          "timestamp": { "type": "string", "format": "date-time" }
        }
      }
    }
  }
}
```

Example for a deprecated Thing Model with a replacement:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    {
      "ditto": "https://ditto.eclipseprojects.io/wot/ditto-extension#"
    }
  ],
  "title": "Legacy Temperature Sensor (DEPRECATED)",
  "description": "This model is deprecated, use temperature-sensor-2.0.0.tm.jsonld instead",
  "ditto:deprecationNotice": {
    "deprecated": true,
    "supersededBy": "https://example.com/models/temperature-sensor-2.0.0.tm.jsonld",
    "removalVersion": "2.0.0"
  },
  "properties": {
    "temperature": {
      "type": "number"
    }
  }
}
```


## WoT Discovery Thing Directory

Starting with Ditto version `3.9.0`, Ditto provides a WoT Discovery "Thing Directory" endpoint that allows clients to
discover available Thing Descriptions in a standardized way, following the
[WoT Discovery specification](https://www.w3.org/TR/wot-discovery/).

### Endpoint

The Thing Directory is available at:

```
GET /.well-known/wot
```

This endpoint returns a WoT Thing Description that describes the Thing Directory itself, including the available
operations for discovering and retrieving Thing Descriptions.

### Response Format

The response is a WoT Thing Description (content type `application/td+json`) that includes:

* **`things` property**: Describes how to retrieve all Thing Descriptions with optional pagination
* **`retrieveThing` action**: Describes how to retrieve a specific Thing Description by its Thing ID

Example response:
```json
{
  "@context": [
    "https://www.w3.org/2022/wot/td/v1.1",
    "https://www.w3.org/2022/wot/discovery"
  ],
  "@type": "ThingDirectory",
  "id": "urn:ditto:wot:thing-directory",
  "title": "Thing Description Directory (TDD) of Eclipse Ditto",
  "version": {
    "model": "1.0.0",
    "instance": "1.0.0"
  },
  "base": "http://localhost:8080",
  "securityDefinitions": {
    "basic_sc": {
      "scheme": "basic",
      "in": "header"
    }
  },
  "security": "basic_sc",
  "properties": {
    "things": {
      "description": "Retrieve all Thing Descriptions",
      "readOnly": true,
      "uriVariables": {
        "offset": {
          "title": "Offset",
          "description": "Number of Thing Descriptions to skip for pagination",
          "type": "integer",
          "minimum": 0
        },
        "limit": {
          "title": "Limit",
          "description": "Maximum number of Thing Descriptions to return",
          "type": "integer",
          "minimum": 1
        }
      },
      "forms": [{
        "href": "api/2/things{?offset,limit}",
        "op": "readproperty",
        "contentType": "application/json"
      }]
    }
  },
  "actions": {
    "retrieveThing": {
      "description": "Retrieve a Thing Description by its Thing ID.",
      "uriVariables": {
        "thingId": {
          "@type": "ThingID",
          "title": "Thing ID",
          "type": "string",
          "format": "iri-reference"
        }
      },
      "safe": true,
      "idempotent": true,
      "forms": [{
        "href": "api/2/things/{thingId}",
        "htv:methodName": "GET"
      }]
    }
  }
}
```

### Available Operations

The Thing Directory describes two main operations:

1. **List all Thing Descriptions** (`things` property):
   * Endpoint: `GET /api/2/things`
   * Supports pagination via `offset` and `limit` query parameters
   * Returns JSON array of things (use `Accept: application/td+json` header for TD format)

2. **Retrieve a specific Thing Description** (`retrieveThing` action):
   * Endpoint: `GET /api/2/things/{thingId}`
   * Replace `{thingId}` with the actual Thing ID
   * Use `Accept: application/td+json` header to get the Thing Description format

### Configuration

The Thing Directory endpoint can be configured via the following options:

| Configuration | Environment Variable | Default | Description |
|--------------|---------------------|---------|-------------|
| Base prefix | `GATEWAY_WOT_DIRECTORY_BASE_PREFIX` | `http://localhost:8080` | The base URL prefix used in the Thing Directory TD |
| Authentication required | `GATEWAY_WOT_DIRECTORY_AUTHENTICATION_REQUIRED` | `false` | Whether authentication is required to access the endpoint |
| JSON template | - | Basic auth security definitions | Additional JSON to merge into the TD (e.g., custom security definitions) |

#### Public vs. Authenticated Access

By default, the Thing Directory endpoint is publicly accessible without authentication, which aligns with the
WoT Discovery specification expectations for discoverability.

To require authentication, set the environment variable:
```bash
GATEWAY_WOT_DIRECTORY_AUTHENTICATION_REQUIRED=true
```

When authentication is required, the same authentication mechanisms used for the `/api/2/` endpoints apply.

#### Custom Security Definitions

The `json-template` configuration in `gateway.conf` allows customizing the security definitions in the Thing Directory TD.
For example, to use OAuth 2.0 with Google:

```hocon
wot-directory {
  json-template {
    "securityDefinitions": {
      "oauth2_google_sc": {
        "scheme": "oauth2",
        "authorization": "https://accounts.google.com/o/oauth2/v2/auth",
        "token": "https://oauth2.googleapis.com/token",
        "scopes": ["openid"],
        "flow": "code"
      }
    },
    "security": "oauth2_google_sc",
    "support": "https://www.eclipse.dev/ditto/"
  }
}
```


## Example

The example can be found on a [dedicated page](basic-wot-integration-example.html) as the JSONs included in the
example are quite long.
