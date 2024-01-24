---
title: "Making Eclipse Ditto Things Accessible in the Asset Administration Shell"
published: true
permalink: 2024-01-15-integrating-ditto-aas-basyx.html
layout: post
author: bs-jokri
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Integrating digital represenations of devices into an application landscape is a recurring task in different domains and application areas.
Several initiatives, projects and standards exist, which try to support in here.
With the Industry 4.0 effort the [Asset Administration Shell](https://industrialdigitaltwin.org/) got specified by the Industrial Digital Twin Association (IDTA), providing a set of specifications to handle all kinds of information of a physical asset over its lifecycle.

One type is runtime information, which needs to be retrieved from a device at runtime.
However, handling network connectivity, state management and API harmonization is a tedious task, easy to be done wrong.

Luckily, [Eclipse Ditto](https://eclipse.dev/ditto/) relieves us from that task but one question still remains: How to integrate Eclipse Ditto with and Asset Administration Shell infrastructure to make information of Ditto [Things](https://eclipse.dev/ditto/basic-thing.html) accessible from within an AAS?

We came up with a solution to use Eclipse Ditto Things from an AAS infrastructure, based on the [Eclipse Basyx](https://eclipse.dev/basyx/) project.

![User-device interaction via AAS and IoT
backend](fig/basic-interaction.svg)

*Figure 1:  User-device interaction via BaSyx and Ditto*

In this blog post we want to share our solution and learnings.


# Some Background
First, we would like to share some background to the Asset Administration Shell and Eclipse Basyx for those of you not yet familiar with it. In case you know the AAS spec as well as the Eclipse Basyx project allready, it is safe to skip this section.

## Asset Administration Shell
The Asset Administration Shell (AAS) is a standardization effort of
the International Digital Twin Association (IDTA) that originated from the
Platform Industry 4.0 (I4.0) ([AAS Spec Part I](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01001-3-0_SpecificationAssetAdministrationShell_Part1_Metamodel.pdf); [AAS Spec Part II](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01002-3-0_SpecificationAssetAdministrationShell_Part2_API.pdf)).

The AAS is a digital representation of a physical asset, and the
combination of both form an I4.0 component where the AAS provides the
interface for I4.0 communication.
An AAS consists of one or more submodels.
Each submodel contains a structured set of elements.
Submodels, as well as their elements, can either be a type or an instance.
The AAS metamodel defines the possible elements for modeling the AAS instances, e.g., Asset, AssetAdminstrationShell (AAS), Submodel (SM), SubmodelElementCollection (SMEC),
Property and further SubmodelElement(s) (SME). You can find further details [here](https://www.plattform-i40.de/IP/Redaktion/EN/Downloads/Publikation/2021_What-is-the-AAS.html) and [here](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01001-3-0_SpecificationAssetAdministrationShell_Part1_Metamodel.pdf).

A user who wants to interact with an AAS follows the sequence of service calls depicted in Figure 2.
The flow starts by requesting an AAS ID from the AAS discovery interface based on a (local) specific asset ID or a global asset ID. With the AAS ID, the application retrieves the endpoint for the AAS through the AAS Registry interface.
The application then requests the SM ID from that AAS endpoint and uses this SM ID to get the SM endpoint from the SM Registry.
From that SM endpoint, the user can request the SME, which contains the required value.

![Sequence of data flow through AAS
infrastructure](fig/aas-sequenz.svg)

*Figure 2: Sequence of data flow through AAS infrastructure*

If you want to dig deeper into the specifics of the AAS, you might want to consult  the [AAS Reading Guide](https://www.plattform-i40.de/IP/Redaktion/DE/Downloads/Publikation/AAS-ReadingGuide_202201.html), a document helping the interested reader to navigate through the available material.

## Eclipse BaSyx
[Eclipse BaSyx](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components) is an open-source project hosted by the Eclipse Foundation providing components to deploy an Industry 4.0 middleware.
Appart from other features it implements the AAS specification allowing to realize an AAS infrastructure on the basis of Eclipse Basyx.
BaSyx provides several easy to use off-the-shelf components. They can be used as a library, as an executable jar or as a docker container.

* [AAS Server Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Server)
* [Registry Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_Registry)
* [DataBridge Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_DataBridge)
* [AAS Web UI](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Web_UI)

You can either pull them from Docker Hub or [follow the instructions](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_Docker) to build them by yourself.

In the following we mainly work with the AAS Server Component and the Registry Component. The AAS Web UI Component can be helpful for visualization purposes. Later we will show how you can integrate the UI into a Basyx-based AAS setup.


# Making Eclipse Ditto Things available in an AAS
The AAS specification defines APIs of several services required within an AAS infrastructure.
For now we only need to focus on the *AAS Interface* and the *AAS Registry Interface*.
The AAS Interface is implemented by an AAS server, in our case this API is provided by the Eclipse Basyx AAS server.
The AAS Registry, in a similar way, is implemented by an AAS Registry component, in our case by the Eclipse Basyx AAS Registry component.

The AAS server provides access to information on assets.
This information is organized in Submodels and there Submodel Elements.

Making Eclipse Ditto Things availabe in an AAS infrastructure boils down to make them available as Submodels of an AAS accessible from an AAS server.
The Submodel representing a Ditto Thing can either be attached to an already existing AAS or a new AAS needs to be created.
In case we use an existing AAS it is already registered at an AAS registry.
In case a Ditto Thing is to be added to a new AAS , the new AAS needs to be created as well, which in turn needs to be registered in the AAS registry.

The next question is, how to make the Thing data available as a Submodel accessible from an AAS server.
We see three approaches:
* Eclipse Ditto *pushes* latest updates to an BaSyx AAS SM server
* BaSyx AAS SM server *pulls* the current state from Eclipse Ditto via a *wrapper* arround Eclipse Ditto
* BaSyx AAS SM server *pulls* the current state from Eclipse Ditto via a *bridge* component Eclipse Basyx already provides.

## Wrapper around Eclipse Ditto exposing AAS APIs
This approach requires to implement a custom AAS infrastructure arround Eclipse Ditto, without the chance of reusing existing components of the Eclipse Basyx project.
A similar approach was taken by the Eclipse Ditto project to implement the [Web of Things](https://eclipse.dev/ditto/2022-03-03-wot-integration.html) (WoT), which is another specification to integrate IoT devices from different contexts and align their utilized data model.
Ditto now allows the generation of new Things based on a WoT Thing Description.


## Pulling Device Data upon Request
Eclipse Basyx provides a bridge component that can be registered with the BaSyx AAS server,
The AAS server delegates requests for an AAS to the bridge that retrieves the actual data from Ditto and applies transformation logic.
For that the BaSyx SM-server component has a delegation feature, where the user can configure an SME with an endpoint to which the server delegates requests.


## Push Device State into AAS Infrastructure
For this approach Eclipse Ditto is configured so that it pushes state changes of devices every time a device changes.
The data is transformed into the AAS format and pushed to the BaSyx SM server, which directly responds to the requests by the users.

![Push approach sequence](fig/push.svg)
*Figure 3: Push approach sequence*

As the push approach treats the AAS infrastructure as a blackbox and almost all configuration happens within Eclipe Ditto we will follow this approach.


## Concept Mapping
Eclipse Ditto and Eclipse Basyx work with different data structures.
Eclipse Ditto is using [Things](https://eclipse.dev/ditto/basic-thing.html) Eclipse Basyx [Submodels](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01001-3-0_SpecificationAssetAdministrationShell_Part1_Metamodel.pdf) from the AAS specification to represent Devices or Assets in general.

Thus, the Things data structure needs to be mapped to Submodels and their respective Submodel Elements.
The following Table 1 shows the mapping of Eclipse Ditto to AAS.

*Table 1: Concept mapping from Eclipse Ditto to the AAS*

| Eclipse Ditto | Asset Administration Shell |
| ------------- | -------------------------- |
| Namespace     | Asset Administration Shell |
| Thing         |  ---                       |
| Features      | Submodel                   |
| Property      | Submodel Element           |
| Attribute     | Submodel Element           | 


A Ditto [`Namespace`](https://eclipse.dev/ditto/basic-namespaces-and-names.html#namespace) is mapped to a single AAS. An AAS holds multiple
SMs. Since a `Thing` comprises one or more [`Features`](https://eclipse.dev/ditto/basic-feature.html), we treat a
`Thing` as an opaque concept and do not define an explicit mapping for a
`Thing` but map one `Feature` to one SM. [`Property`](https://eclipse.dev/ditto/basic-feature.html#feature-properties) and [`Attribute`](https://eclipse.dev/ditto/basic-thing.html#attributes) are
mapped to SMEs.

By that it is possible to have more then one Thing organized in an AAS. This can especially be useful if an AAS organizes some more complex equipment with different sensors and actuators, which belong together.

# Configuration steps
With the more theoretical details completed we can now turn to the actual implementation and describe step by step what is required to integrate Eclipes Ditto into an AAS infrastructure of Eclipse BaSyx.

## Prerequisites
1. Running instance of [Eclipse Ditto](https://eclipse.dev/ditto/)
2. Running instance of [Eclipse BaSyx](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components)

Those two instances must be available and a network connection must exist between both. In this tutorial we use the demo environment of Eclipse Ditto called `ditto.eclipseprojects.io`.

For our tests we used version 3.0.1 for Eclipse Ditto and version for Eclipse BaSyx. However we assume other versions might work as well.

## Scenario
Let's assume for this tutorial that we have a device with a sensor named `machine:sensor` which is capable of measuring temperature. This device sends sensor data to an Eclipse Ditto instance in the following format.

```json
{
  "topic": "machine/sensor/things/twin/commands/modify",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 46
}
```

Surprisingly, the very same format as Ditto expects in a [Ditto Protocol message](https://eclipse.dev/ditto/1.3/protocol-overview.html) for updating the internal representation of a Thing.
As this message is already in the format of a Ditto Protocol message, this saves us one mapping step from a custom message to a Ditto Protocol message. So if you have a custom message, you have to map it first to a Ditto Protocol message. You can find more details on that [here](https://eclipse.dev/ditto/connectivity-mapping.html).

## Create a connection to BaSyx

We further proceed with the example Thing given above.
This message will update the temperature value of a Thing ``machine:sensor`` in an Eclipse Ditto instance.
So we assume that we already could access the device's sensor data as API via Eclipse Ditto.

Now, if such a message arrives at Eclipse Ditto, we want to achieve that it is mapped to an AAS-conforming representation and forwarded to some outbound connection to an AAS infrastructure.
Eclipse Ditto provides the feature of [payload mapping](https://eclipse.dev/ditto/connectivity-mapping.html) in its connectivity service.
So the task is to define payload mappings according to the mapping provided in "Concept Mapping".
We need to define one mapping to create an AAS, a second mapping to create a Submodel and a third one to update the Submodel according to changed Thing property values.


Before we can update an AAS submodel, an AAS must be created, which contains the submodel. For that, we need a payload mapping, which creates an AAS every time a Ditto Thing was created. Here we map the `namespace` of a Ditto Thing to the AAS.

Secondly, an AAS submodel must be created. As we want to map a Ditto `feature` to a submodel, we further need a payload mapping, which creates a submdel every time a feature of a Thing was created.

The third payload mapping will be triggered every time a message updates the (temperature) value of a Ditto Thing. With that mapping we could create and modify an AAS submodel element with the latest (temperature) value.

As one may already have observed, the first two payload mappings only react on actions, where something was created. So these mappings perform a configuratio. In the third payload mapping the actual device data is processed. The reason why we update a submodel element instead of the whole submodel is that when a Ditto Protocol message arrives at Eclipse Ditto, we only have access to one changed property of a Ditto Thing and no information about the other Ditto Thing properties and therefore submodel elements, which may already be contained in the submodel.

In the next sections, we discuss the different payload mappings in more detail and look at how the BaSyx-Ditto connection can be registered with the Ditto connectivity service.

### AAS mapping

Ditto's Payload mappings are defined in Javascript. The following JavaScript-based mapping performs a mapping to an AAS every time a Ditto Thing is created.

```javascript
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {
  let headers = dittoHeaders;
  let textPayload = JSON.stringify({
    conceptDictionary: [],
    identification: {
      idType: 'Custom',
      id: namespace
    },
    idShort: namespace,
    dataSpecification: [],
    modelType: {
      name: 'AssetAdministrationShell'
    },
    asset: {
      identification: {
        idType: 'Custom',
        id: namespace + '-asset'
      },
      idShort: namespace + '-asset',
      kind: 'Instance',
      dataSpecification: [],
      modelType: {
        name: 'Asset'
      },
      embeddedDataSpecifications: []
    },
    embeddedDataSpecifications: [],
    views: [],
    submodels: []
  });
  let bytePayload = null;
  let contentType = 'application/json';
  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
    textPayload, // The external mapped String
    bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
  );
}
```

The function `mapFromDittoProtocolMsg` maps the passed parameters which originated from a Ditto Protocol message to an external message. In this mapping, only `namespace` is used. That is the first part of the name of a Ditto Thing, e.g. `machine` in our example.
More precisely, a text payload is created with the configuration of an AAS with the id `namespace` and the function returns a new message with this text payload. After the Ditto connectivity services runs the Javascript with success, the new message will be pushed to the BaSyx AAS server and an AAS will be created. For example, whenever a Ditto Thing with id `machine:sensor` is created, an AAS will be created with the id `machine`.


### AAS Submodel mapping

The second JavaScript-based mapping performs a mapping to an AAS submodel every time a feature of a Ditto Thing was created.

```javascript
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {
  
  let feature_id = path.split('/').slice(2);
  let headers = dittoHeaders;
  let textPayload = JSON.stringify(
    {
      parent: {
        keys: [
          {
            idType: 'Custom',
            type: 'AssetAdministrationShell',
            value: namespace,
            local: true
          }
        ]
      },
      identification: {
        idType: 'Custom',
        id: name+'_'+feature_id
      },
      idShort: name+'_'+feature_id,
      kind: 'Instance',
      dataSpecification: [],
      modelType: {
        name: 'Submodel'
      },
      embeddedDataSpecifications: [],
      submodelElements: []
    }

  );
  let bytePayload = null;
  let contentType = 'application/json';
  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
    textPayload, // The external mapped String
    bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
  );
}
```

In contrast to the previous mapping additionally to `namespace`, in this mapping also the parameters `name` and `path` from the Ditto Protocol message are used. The parameter `name` represents the second part of the name of a Ditto Thing, e.g. `sensor` from our example. The `path` is the path which is affected by the Ditto Protocol message. It can include the feature id of the Ditto Thing as well as the whole path of the property of a Ditto Thing, but it can also be only `/`, when a Ditto Thing is created. You can find an example for this parameter in the message from the section "Scenario".

In this mapping, the function extracts the id of the feature from parameter `path` and uses this together with the `name` of the Ditto Thing to build the id of an AAS submodel; in our example this would be `sensor_temperature`. Therefore it is important that the `path` contains the id of the feature. We come to that back again in section "Create the connection".

Similarly to the first mapping, the function returns a new message with a custom text payload. After the Ditto connectivity services runs the Javascript with success, the new message will be pushed to the BaSyx AAS server and an AAS submodel will be created. For example, whenever a feature `temperature` of a Ditto Thing called `machine:sensor` is created, an AAS submodel will be created with the id `sensor_temperature` in the AAS `machine`.

### AAS Submodel element mapping

The third JavaScript-based mapping performs a mapping to an AAS submodel element every time a property of a Ditto Thing is modified.

```javascript
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {
  let property_id = path.split('/').slice(3).join('_');
  let feature_id = path.split('/').slice(2,3);
  let headers = dittoHeaders;
  let dataType = typeof value;
  dataType = mapDataType(dataType)

  function mapDataType(dataType) {
    switch (dataType) {
        case 'undefined':
        return 'Undefined';
        case 'boolean':
        return 'boolean';
        case 'number':
        return 'int';
        case 'string':
        return 'string';
        case 'symbol':
        return 'Symbol';
        case 'bigint':
        return 'BigInt';
        case 'object':
        return 'string';
        case 'function':
        return 'Function';
        default:
        return 'Unknown';
    }
  }
  let textPayload = JSON.stringify(
  {
    parent: {
      keys: [
        {
          idType: 'Custom',
          type: 'Submodel',
          value: name+'_'+feature_id,
          local: true
        }
      ]
    },
    idShort: property_id,
    kind: 'Instance',
    valueType: dataType,
    modelType: {
      name: 'Property'
    },
    value: value
  }
  );
  let bytePayload = null;
  let contentType = 'application/json';
  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
    textPayload, // The external mapped String
    bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
  );
}
```

Additionally to the `feature` id, we extract the id of the `property ` of the Ditto Thing from the parameter `path` in this mapping. Again, this is only possible, if the parameter `path` includes the `property` id of the Ditto Thing.
Moreover here we have access to the `value` of the corresponding `property`. That is the `value` which was applied in the modify action.

So if a message updates a corresponding `property` of a Ditto Thing, the function in the mapping extracts the Ditto `feature` and `property` id's of the updated `value` from the Ditto parameter `path` and creates a new text payload for updating the submodel element of the corresponding submodel with the new `value`. For example if a message would update the Ditto `property` called `value` with feature `temperature` from our example, the submodel element with id `properties_value` in the submodel `sensor_temperature` from the AAS `machine` will be updated with the new temperature value.

### Create a connection to the BaSyx AAS server

With the mappings defined above it is now possible to configure a new [Ditto connection](https://eclipse.dev/ditto/basic-connections.html) to a BaSyx AAS server. Be aware, that the scripts above must be provided to Ditto's Connectivity service via http. This also requires to escape certain characters and remove the line breaks. So replace newlines with `\n` and `'` with `'"'`.

In the following script we have added the Ditto demo instance called `ditto.eclipseprojects.io` for testing purposes, which needs to be exchanged, if someone needs to use a custom Ditto instance. Also you must have access to the Ditto [Devops](https://eclipse.dev/ditto/installation-operating.html#devops-commands) credentials. The default devops credentials are username: devops, password: foobar, but the password can be changed by setting the environment variable DEVOPS_PASSWORD in the [gateway service](https://eclipse.dev/ditto/architecture-services-gateway.html).

Finally adjust the parameter `uri` with the ip of your running BaSyx server.
```bash
curl -X POST -u devops:foobar -H 'Content-Type: application/json' --data-binary '{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
      "aggregate": false
    },
    "piggybackCommand": {
      "type": "connectivity.commands:createConnection",
      "connection": {
        "id": "basyxserver-http-connection",
        "connectionType": "http-push",
        "connectionStatus": "open",
        "uri": "http://basyx-aas-server:4001",
        "failoverEnabled": true,
        "mappingDefinitions": {
          "mappingforShell": {
            "mappingEngine": "JavaScript",
            "options": {
              "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify({\n    conceptDictionary: [],\n    identification: {\n      idType: '"'Custom'"',\n      id: namespace\n    },\n    idShort: namespace,\n    dataSpecification: [],\n    modelType: {\n      name: '"'AssetAdministrationShell'"'\n    },\n    asset: {\n      identification: {\n        idType: '"'Custom'"',\n        id: namespace + '"'-asset'"'\n      },\n      idShort: namespace + '"'-asset'"',\n      kind: '"'Instance'"',\n      dataSpecification: [],\n      modelType: {\n        name: '"'Asset'"'\n      },\n      embeddedDataSpecifications: []\n    },\n    embeddedDataSpecifications: [],\n    views: [],\n    submodels: []\n  });\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}"            
            }
          },
          "mappingforSubmodel": {
            "mappingEngine": "JavaScript",
            "options": {
                "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n  \n  let feature_id = path.split('"'/'"').slice(2);\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify(\n    {\n      parent: {\n        keys: [\n          {\n            idType: '"'Custom'"',\n            type: '"'AssetAdministrationShell'"',\n            value: namespace,\n            local: true\n          }\n        ]\n      },\n      identification: {\n        idType: '"'Custom'"',\n        id: name+'"'_'"'+feature_id\n      },\n      idShort: name+'"'_'"'+feature_id,\n      kind: '"'Instance'"',\n      dataSpecification: [],\n      modelType: {\n        name: '"'Submodel'"'\n      },\n      embeddedDataSpecifications: [],\n      submodelElements: []\n    }\n\n  );\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}"
            }
          },
          "mappingforSubmodelElement": {
            "mappingEngine": "JavaScript",
            "options": {
              "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n  let property_id = path.split('"'/'"').slice(3).join('"'_'"');\n  let feature_id = path.split('"'/'"').slice(2,3);\n  let headers = dittoHeaders;\n  let dataType = typeof value;\n  dataType = mapDataType(dataType)\n\n  function mapDataType(dataType) {\n    switch (dataType) {\n        case '"'undefined'"':\n        return '"'Undefined'"';\n        case '"'boolean'"':\n        return '"'boolean'"';\n        case '"'number'"':\n        return '"'int'"';\n        case '"'string'"':\n        return '"'string'"';\n        case '"'symbol'"':\n        return '"'Symbol'"';\n        case '"'bigint'"':\n        return '"'BigInt'"';\n        case '"'object'"':\n        return '"'string'"';\n        case '"'function'"':\n        return '"'Function'"';\n        default:\n        return '"'Unknown'"';\n    }\n  }\n  let textPayload = JSON.stringify(\n  {\n    parent: {\n      keys: [\n        {\n          idType: '"'Custom'"',\n          type: '"'Submodel'"',\n          value: name+'"'_'"'+feature_id,\n          local: true\n        }\n      ]\n    },\n    idShort: property_id,\n    kind: '"'Instance'"',\n    valueType: dataType,\n    modelType: {\n      name: '"'Property'"'\n    },\n    value: value\n  }\n  );\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}"
            }
          },
          "mappingforAttributesSubmodel": {
            "mappingEngine": "JavaScript",
            "options": {
                "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify(value);\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}"
            }
          }
        },
        "sources": [],
        "targets": [
          {
            "address": "PUT:/aasServer/shells/{{ thing:namespace }}",
            "headerMapping": {
              "content-type": "{{ header:content-type }}"
            },
            "authorizationContext": ["nginx:ditto"],
            "topics": [
              "_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),eq(resource:path,'"'/'"'))"
            ],
            "payloadMapping": [
              "mappingforShell"
            ]
          },
          {
            "address": "PUT:/aasServer/shells/{{ thing:namespace }}/aas/submodels/{{ thing:name }}_{{ resource:path | fn:substring-after('"'/features/'"') }}",
            "headerMapping": {
              "content-type": "{{ header:content-type }}"
            },
            "authorizationContext": ["nginx:ditto"],
            "topics": [
              "_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),not(eq(resource:path,'"'/features'"')),like(resource:path,'"'/features*'"'),not(like(resource:path,'"'*properties*'"')))"
            ],
            "payloadMapping": [
              "mappingforSubmodel"
            ]
          },
          {
            "address": "PUT:/aasServer/shells/{{ thing:namespace }}/aas/submodels/{{ thing:name }}_{{ resource:path | fn:substring-after('"'/features/'"') | fn:substring-before('"'/properties'"') }}/submodel/submodelElements/properties_{{ resource:path | fn:substring-after('"'/properties/'"') | fn:replace('"'/'"','"'_'"') }}",
            "headerMapping": {
              "content-type": "{{ header:content-type }}"
            },
            "authorizationContext": ["nginx:ditto"],
            "topics": [
              "_/_/things/twin/events?filter=and(in(topic:action,'"'modified'"'),not(eq(resource:path,'"'/features'"')),like(resource:path,'"'/features*'"'),like(resource:path,'"'*properties*'"'),not(like(resource:path,'"'*properties'"')))"
            ],
            "payloadMapping": [
              "mappingforSubmodelElement"
            ]
          },
          {
            "address": "PUT:/aasServer/shells/{{ thing:namespace }}/aas/submodels/{{ resource:path | fn:substring-after('"'/attributes/submodels/'"') }}",
            "headerMapping": {
              "content-type": "{{ header:content-type }}"
            },
            "authorizationContext": ["nginx:ditto"],
            "topics": [
              "_/_/things/twin/events?filter=and(in(topic:action,'"'modified'"'),like(resource:path,'"'/attributes/submodels*'"'))"
            ],
            "payloadMapping": [
              "mappingforAttributesSubmodel"
            ]
          }
        ]
      }
    }
  }' http://ditto.eclipseprojects.io/devops/piggyback/connectivity
```

The Javascripts from above you can find in `mappingforShell`, `mappingforSubmodel` and `mappingforSubmodelElement`. The address of the BaSyx AAS server is configured in the parameter `uri`. As the Ditto connectivity services creates the connection to the AAS server, the connectivity service must be allowed to reach the address from `uri`. So you must have a running Ditto instance and a running BaSyx AAS server, where the Ditto connectivity service and the AAS server are in the same network.

When the connection is established and our payload mapping works we receive a successful HTTP response otherwise an error message is returned.

With that connection configured changes to the state of a Thing are now propagated to the configured BaSyx AAS server according to the mapping defined in "Concept mapping". However, without any further means the payload mapping is executed for all changes.
Thus, we use [filtering](https://eclipse.dev/ditto/basic-changenotifications.html#filtering) with [RQL expressions](https://eclipse.dev/ditto/basic-rql.html) to make sure that our payload mappings are executed for the right messages, e.g. the filter for `mappingforShell` makes sure that it is only  triggered for messages, which creates a Ditto Thing. Moreover, we can make sure, that in the `mappingforSubmodel` the parameter `path` contains the id of a Ditto `feature`.


### Setup Connection to BaSyx AAS Registry

To make the AAS available from the Eclipse Basyx AAS Registry, we additionally have to create a connection from Eclipse Ditto to the BaSyx AAS Registry. Whenever a Thing is created, an AAS entry should be registered in the Eclipse Basyx AAS Registry. For that we definde the following payload mapping:

```javascript
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {
  let headers = dittoHeaders;
  let textPayload = JSON.stringify({
    endpoints: [
        {
            address: 'http:/' + '/{{ .Release.Name }}-basyx-aas-server:4001/aasServer/shells/' + namespace + '/aas',
            type: 'http'
        }
    ],
    modelType: {
        name: 'AssetAdministrationShellDescriptor'
    },
    identification: {
        idType: 'Custom',
        id: namespace
},
    idShort: namespace,
      asset: {
          identification: {
              idType: 'Custom',
              id: namespace + '-asset'
          },
          idShort: namespace + '-asset',
          kind: 'Instance',
          dataSpecification: [],
          modelType: {
              name: 'Asset'
          },
          embeddedDataSpecifications: []
      },
      submodels: []
  });
  let bytePayload = null;
  let contentType = 'application/json';
  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
   textPayload, // The external mapped String
   bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
);
}
```

As in Section "AAS mapping" the `namespace` of the Ditto Thing is mapped to an `AAS`. In contrast to the mapping in Section "AAS mapping" here it is important, that the new BaSyx Registry entry contains the BaSyx AAS server endpoint for the new AAS in the variable `endpoints`. After we defined this mapping, it is now possible to configure a new connection to a BaSyx AAS registry. Be aware, that the script above must be provided to Dittos Connectivity service via http. This also requires to escape certain characters and remove the line breaks. So replace newlines with `\n` and `'` with `'"'`.

In the following script we have added the Ditto demo instance called `ditto.eclipseprojects.io` for testing porpuses, which needs to be adjusted if someone needs to use a custom Ditto instance.

```bash
curl -X POST -u devops:foobar -H 'Content-Type: application/json' --data-binary '{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
      "aggregate": false
    },
    "piggybackCommand": {
      "type": "connectivity.commands:createConnection",
      "connection": {
        "id": "basyxregistry-http-connection",
        "connectionType": "http-push",
        "connectionStatus": "open",
        "uri": "http://basyx-aas-registry:4000",
        "failoverEnabled": true,
        "mappingDefinitions": {
          "mappingforShell": {
            "mappingEngine": "JavaScript",
            "options": {
              "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify({\n    endpoints: [\n        {\n            address: '"'http:/'"' + '"'/basyx-aas-server:4001/aasServer/shells/'"' + namespace + '"'/aas'"',\n            type: '"'http'"'\n        }\n    ],\n    modelType: {\n        name: '"'AssetAdministrationShellDescriptor'"'\n    },\n    identification: {\n        idType: '"'Custom'"',\n        id: namespace\n},\n    idShort: namespace,\n      asset: {\n          identification: {\n              idType: '"'Custom'"',\n              id: namespace + '"'-asset'"'\n          },\n          idShort: namespace + '"'-asset'"',\n          kind: '"'Instance'"',\n          dataSpecification: [],\n          modelType: {\n              name: '"'Asset'"'\n          },\n          embeddedDataSpecifications: []\n      },\n      submodels: []\n  });\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n   textPayload, // The external mapped String\n   bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n);\n}"
            }
          }
        },
        "sources": [],
        "targets": [
          {
            "address": "PUT:/registry/api/v1/registry/{{ thing:namespace }}",
            "headerMapping": {
              "content-type": "{{ header:content-type }}"
            },
            "authorizationContext": ["nginx:ditto"],
            "topics": [
              "_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),eq(resource:path,'"'/'"'))"
            ],
            "payloadMapping": [
              "mappingforShell"
            ]
          }
        ]
      }
    }
  }' http://ditto.eclipseprojects.io/devops/piggyback/connectivity
```

The same Javascript function `mapFromDittoProtocolMsg` presented above is also contained in the json document assigned to parameter `payloadMapping` referenced as `mappingforShell`. The address of the BaSyx AAS registry is configured in the parameter `uri`. As the Ditto connectivity services creates the connection to the AAS registry, the connectivity service must be allowed to reach the address from `uri`. So you must have a running Ditto instance and a running BaSyx AAS registry, where the Ditto connectivity service and the AAS registry are in the same network.

Here again, we used the option of [filtering](https://eclipse.dev/ditto/basic-changenotifications.html#filtering) with [RQL expressions](https://eclipse.dev/ditto/basic-rql.html) to make sure that our function in the Javascript will only be triggered when a Thing was created.

So with the configuration given above, everytime a Ditto Thing is created an entry for an AAS with the name of the namespace of the Thing is created in the AAS Registry pointing to the configured AAS Server.

Note that it is enough to only register the AAS with its BaSyx AAS server endpoint in the BaSyx AAS registry. The registry only needs the AAS server endpoint to also get access to all submodels and submodel elements from the AAS.

## Test the connection
Now we have all required Connections properly configured and we are able to test our setup.
As all our configured connections are triggered by changes to a Thing the natural way to test is to manipulate a Thing in Ditto.
We begin by creating one.

In the following scripts we have added the Ditto demo instance  `ditto.eclipseprojects.io` for testing purposes, which needs to be changed for a custom Ditto instance.

### Creating a Thing in Eclipse Ditto

#### Setup a common policy

In order to define common authorization information for all Things about to be created in Ditto, we first create a [policy](https://eclipse.dev/ditto/basic-policy.html) with the policy-id machine:my-policy.

```bash 
POLICY_ID=machine:my-policy

curl -i -X PUT -u ditto:ditto -H 'Content-Type: application/json' --data '{
  "entries": {
    "DEFAULT": {
      "subjects": {
        "{{ request:subjectId }}": {
           "type": "Ditto user authenticated via nginx"
        }
      },
      "resources": {
        "thing:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        },
        "policy:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        },
        "message:/": {
          "grant": ["READ", "WRITE"],
          "revoke": []
        }
      }
    }
  }
}' http://ditto.eclipseprojects.io/api/2/policies/$POLICY_ID
```
This creates a policy we can use in the next steps to configure our Things. You will get a response 201 Created response, if the policy creation concluded successfuly. In the next steps we will use the policy-id `machine:my-policy` to refer to the created policy.


#### Create the Thing
The next step is to create the actual Thing you need to provide a name for the thing, its namespace and the policy-id for the policy to be applied to this thing. In our case this is  `machine:sensor` and policy-id `machine:my-policy`.

With curl the call looks as follows:
```bash
NAMESPACE=machine
NAME=sensor
DEVICE_ID=$NAMESPACE:$NAME


curl -i -X PUT -u ditto:ditto -H 'Content-Type: application/json' --data '{
  "policyId": "'$POLICY_ID'"
}' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID
```

Again a successful creation returns a 201 Created response.

As we configured two connections, triggered on the create event of a Thing, the corresponding mapping scripts are executed and an entry for a new AAS in the AAS server (``mappingforShell`` from the BaSyx server connection) and an entry in the AAS registry (``mappingforShell`` from the BaSyx registry connection) are created.


If the execution of the scripts was successful can be checked by
```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells
```
which should return the following result

```json
[{"modelType":{"name":"AssetAdministrationShell"},"idShort":"machine","identification":{"idType":"Custom","id":"machine"},"dataSpecification":[],"embeddedDataSpecifications":[],"submodels":[{"keys":[{"type":"AssetAdministrationShell","local":true,"value":"machine","idType":"Custom"},{"type":"Submodel","local":true,"value":"sensor_temperature","idType":"Custom"}]}],"asset":{"keys":[{"type":"Asset","local":true,"value":"machine-asset","idType":"Custom"}],"identification":{"idType":"Custom","id":"machine-asset"},"idShort":"machine-asset","kind":"Instance","dataSpecification":[],"modelType":{"name":"Asset"},"embeddedDataSpecifications":[]},"views":[],"conceptDictionary":[]}]
```
and

```bash
curl -X GET http://basyx-aas-registry:4000/registry/api/v1/registry
```
which should return the following result

```json
[{"modelType":{"name":"AssetAdministrationShellDescriptor"},"endpoints":[{"address":"http://basyx-aas-server:4001/aasServer/shells/machine/aas","type":"http"}],"identification":{"idType":"Custom","id":"machine"},"idShort":"machine","asset":{"identification":{"idType":"Custom","id":"machine-asset"},"idShort":"machine-asset","kind":"Instance","dataSpecification":[],"modelType":{"name":"Asset"},"embeddedDataSpecifications":[]},"submodels":[]}]
```

Now we have a thing created, which is not much more then a hull without any features, properties, or attributes.
So lets populate our Thing.

### Create a feature for the twin
Next we create a Feature for the Thing.
This Feature will contain a Property containing data of a virtual sensor.

```bash
FEATURE_ID=temperature

curl -X PUT -u ditto:ditto -H 'Content-Type: application/json' --data-binary '{
  "properties": {
    "value": null
  }
}' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID/features/$FEATURE_ID
```
This triggers the next payload mapping (mappingforSubmodel), which creates a Submodel for the Feature entry in the previously created AAS in the connected AAS server.

To check if this was successful, use curl, e.g. as follows:
```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells/$NAMESPACE/aas/submodels/${NAME}_${FEATURE_ID}/submodel
```
Which should result in the following response

```json
{"parent":{"keys":[{"idType":"Custom","type":"AssetAdministrationShell","value":"machine","local":true}]},"identification":{"idType":"Custom","id":"sensor_temperature"},"idShort":"sensor_temperature","kind":"Instance","dataSpecification":[],"modelType":{"name":"Submodel"},"embeddedDataSpecifications":[],"submodelElements":[]}
```

### Updating the twin

After we have successfully created a device we can now check if the updating of a property works as well.
This can for example be done by directly accessing the HTTP API of Eclipse Ditto for sending the data:
```bash 
curl -i -X PUT -u ditto:ditto -H "content-type: application/json" --data-binary '46' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID/features/$FEATURE_ID/properties/value
```
Again, we check if our change was successful.
```bash
curl -u ditto:ditto -w '\n' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID
```
which results in

```json
{"thingId":"machine:sensor","policyId":"machine:my-policy","features":{"temperature":{"properties":{"value":46}}}}
```
If this was successful, then the mapping `mappingforSubmodelElement` should be triggered and also the Submodel should have been updated.

Verify this by calling

```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells/$NAMESPACE/aas/submodels/${NAME}_${FEATURE_ID}/submodel/submodelElements/properties_value
```
This should lead to the response

```json
{"parent":{"keys":[{"idType":"Custom","type":"Submodel","value":"sensor_temperature","local":true}]},"idShort":"properties_value","kind":"Instance","valueType":"int","modelType":{"name":"Property"},"value":46}
```

Here we finally see, that we could access the device's sensor data as AAS Submodel API via Eclipse BaSyx.

If plain json responses are not fancy enough to test this, you can use one of the UI tools provided by the AAS community, to do further tests. You coud use [AAS Web UI](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Web_UI), which workes quite well.
In case you deployed it and want to try, then you can access your web UI in a web browser e.g. on `http://basyx-aas-web-ui:4006`.
As ,,Registry Server URL" paste the URL from your AAS Registry in the following form `http://basyx-aas-registry:4000/registry`. After reloading the page you can see the created Shell und Submodel in die AAS Web UI.

{% include image.html file="blog/2024-01-15-integrating-ditto-ass-basyx/AASDashboard.png" alt="AAS Dashboard" max-width=1000 %}
*Figure 4: BaSyx AAS Web UI*

# Summary
In this post we presented our approach on how to make Ditto Things available as Submodels of an AAS within an AAS environment.
We defined a mapping concept for mapping the Ditto Things datastructure to Submodels and their respective Submodel Elements. To fulfill the mapping concept we showed how to create connections from Ditto to the BaSyx AAS server component and the BaSyx AAS registry component and finally tested the connection with an example Ditto Thing and data from a virtual sensor.

We also shortly discussed alternative approaches on how to make Ditto Things available in an AAS. One idea is to enable the *pull with wrapper* approach to implement a custom AAS infrastructure around Eclipse Ditto without reusing existing components of the Eclipse Basyx project, resulting in a custom AAS specification implementation arround the Eclipse Ditto project. A lot of work without much reuse of existing code bases.

The *pull with bridge* approach could be realized by using the bridge component Eclipse BaSyx provides and which can be registered with the BaSyx AAS server. However, at the time of writing this blog post, the bridge component was not able to properly handle Ditto's provided authentication mechanisms.

Ditto's features allowed us to easily integrate devices connected to Ditto into AAS. We assume our presented approach can be applied to other technologies in a similar way.






<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
