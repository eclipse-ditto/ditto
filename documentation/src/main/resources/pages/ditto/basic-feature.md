---
title: Feature
keywords: definition, properties, desiredProperties, entity, feature, functionblock, informationmodel, model
tags: [model]
permalink: basic-feature.html
---

A Feature is used to manage all data and functionality of a Thing that can be clustered in an outlined technical
context.

For different contexts or aspects of a Thing different Features can be used which are all belonging to the same Thing
and do not exist without this Thing.

## Model specification

The feature model in API version 2:

{% include docson.html schema="jsonschema/feature_v2.json" %}


## Feature ID

Within a Thing each Feature is identified by a unique string - the so-called Feature ID.  
A Feature ID often needs to be set in the path of an HTTP request. Due to this fact we strongly recommend using a
restricted set of characters (e.g. those for [Uniform Resource Identifiers (URI)](https://www.ietf.org/rfc/rfc3986.txt)).

The Feature ID may not be the wildcard operator `*` because it has a special meaning and can lead to unexpected
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
* either valid HTTP(s) URLs (e.g. in order to define that the Feature is described by [WoT (Web of Things) Thing Models](basic-wot-integration.html#thing-model-describing-a-ditto-feature))
* or a *namespace*, *name* and *version* separated by colons: `<namespace>:<name>:<version>`
   * in that case the "knowledge" where to look up the definition must be managed somewhere else

A definition can be seen as a type for features. The [properties](#feature-properties) of a 
feature containing a definition identifier `"org.eclipse.ditto:lamp:1.0.0"` can be expected to follow the structure
described in the `lamp` type of namespace `org.eclipse.ditto` semantically versioned with version `1.0.0`.

{% include note.html content="Ditto does not contain a type system on its own and does not specify how to describe types. 
   You may e.g. use the official [W3C Web of Things (WoT)](#the-link-to-w3c-wot-web-of-things) standard 
   to describe data structures and supported messages of Ditto features." %}

Currently, Ditto **does not** ensure that the `properties` or `desiredProperties` of a feature or its supported messages 
must follow the type defined in the definition.  
This can be ensured e.g. before sending a property to Ditto or before sending a message.

### The link to W3C WoT (Web of Things)

If a [feature definition](#feature-definition) has the form of an HTTP(s) URL, this URL pointing to a resource may be
interpreted as the link to a [W3C WoT (Web of Things)](https://www.w3.org/TR/wot-thing-description11/) 
[Thing Model](https://www.w3.org/TR/wot-thing-description11/#thing-model) in [JSON-LD](https://www.w3.org/TR/json-ld11/) 
format.

For a detailed explanation how WoT and its concepts link to Ditto, please consult the 
[dedicated WoT integration documentation](basic-wot-integration.html).


# Example

Please inspect the following separate pages for the WoT integration and an example:
* [WoT (Web of Things) integration](basic-wot-integration.html)
* [WoT (Web of Things) example](basic-wot-integration-example.html)
