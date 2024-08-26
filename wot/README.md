## Eclipse Ditto :: WoT

This module contains models and implementation of the W3C "Web of Things" (WoT) integration of Eclipse Ditto.

As of version `2.4.0` of Ditto, this implementation follows the 
[Web of Things (WoT) Thing Description 1.1](https://www.w3.org/TR/wot-thing-description11/).

As of Ditto version `3.6.0`, the WoT integration was structured better to foster reusability of e.g. **model** and **api** 
from codebases, reducing the dependencies on the WoT integration a lot. 

This WoT module is separated in the following submodules:
* **model** (`ditto-wot-model`): contains a Java model of WoT (Web of Things) entities based on **ditto-json**.<br/>
  May be used in order to:
   * read a WoT "Thing Model" or "Thing Description" JSON from a String and convert it to Java objects
   * use the builder based Java API in order to create a WoT "Thing Model" or "Thing Description" from Java and e.g.
     output the JSON representation
* **api** (`ditto-wot-api`): contains API and implementation for the main functionality Ditto performs with regard to WoT:
  * fetching a WoT TM (Thing Model) from an HTTP endpoint: `WotThingModelFetcher`
    * requiring an implementation of the interface `JsonDownloader`
  * resolving WoT TM (Thing Model) extensions and references: `WotThingModelExtensionResolver`
  * generating Ditto `Thing` JSON skeletons based on a WoT TM: `WotThingSkeletonGenerator`
  * generating Ditto specific WoT TDs (Thing Descriptions), including generated HTTP `forms` based on WoT TMs: `WotThingDescriptionGenerator`
  * validating Ditto `Thing` instances against a WoT TM: `WotThingModelValidator`
* **validation** (`ditto-wot-validation`): contains configuration and logic how Ditto `Thing`s and `Feature`s should
  be validated against WoT TMs (Thing Models), using a JsonSchema validation library, producing a `WotThingModelPayloadValidationException`
  if validation against a given model was not successful
* **integration** (`ditto-wot-integration`): contains Ditto (and Apache Pekko) specific integration of the `ditto-wot-api`
