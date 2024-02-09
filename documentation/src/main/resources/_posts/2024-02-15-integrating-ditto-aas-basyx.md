---
title: "Access Ditto Things from an Asset Administration Shell"
published: true
permalink: 2024-02-15-integrating-ditto-aas-basyx.html
layout: post
author: johannes_kristan
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Integrating digital representations of devices into an IT infrastructure is a recurring task in different domains and application areas.
To address this challenge in Industry 4.0 scenarios and data along the supply chain, the community specified the [Asset Administration Shell](https://industrialdigitaltwin.org/) within the Industrial Digital Twin Association (IDTA) to handle all kinds of information of a physical asset over its lifecycle.

Eclipse Ditto provides a backend for handling such device data as Things and takes care of a number of general tasks that are otherwise easy to be done wrong, like handling device connectivity over different protocols or state management. Therefore, it is promising to use the benefits of Eclipse Ditto for populating an AAS infrastructure when the devices already communicate with an existing instance of Eclipse Ditto.

We therefore want to share our solution and learnings from creating a joint deployment of [Eclipse Basyx](https://eclipse.dev/basyx/) as AAS infrastructure and Eclipse Ditto.

{% include image.html file="blog/2024-02-15-integrating-ditto-ass-basyx/basic-interaction.svg" alt="User-device interaction via AAS and IoT backend" max-width=1000 %}
*Figure 1:  User-device interaction via BaSyx and Ditto*

## Background

We start with some background on the AAS and Eclipse Basyx. If you are allready familiar with both, it is safe to skip this section.

### Asset Administration Shell

The Asset Administration Shell (AAS) is a standardization effort of
the International Digital Twin Association (IDTA) that originated from the
Platform Industry 4.0 (I4.0) ([AAS Spec Part I](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01001-3-0_SpecificationAssetAdministrationShell_Part1_Metamodel.pdf); [AAS Spec Part II](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01002-3-0_SpecificationAssetAdministrationShell_Part2_API.pdf)).

An AAS is a digital representation of a physical asset and consists of one or more submodels. Each submodel contains a structured set of submodel elements.
Submodels, as well as their submodel elements, can either be a type or an instance.
The AAS metamodel defines the possible elements for modeling an AAS like Asset, AssetAdminstrationShell (AAS), Submodel (SM), SubmodelElementCollection (SMEC),
Property, and SubmodelElement (SME). You can find further details [here](https://www.plattform-i40.de/IP/Redaktion/EN/Downloads/Publikation/2021_What-is-the-AAS.html) and [here](https://industrialdigitaltwin.org/en/wp-content/uploads/sites/2/2023/04/IDTA-01001-3-0_SpecificationAssetAdministrationShell_Part1_Metamodel.pdf).

A user who wants to interact with an AAS over HTTP follows the sequence of service calls depicted in Figure 2.
The flow starts by requesting an AAS ID from the AAS discovery interface based on a (local) specific asset ID or a global asset ID. An example of such an asset ID is a serial number written on the device. With the AAS ID, the user retrieves the endpoint for the AAS through the AAS registry interface.
The user then requests the SM ID from that AAS endpoint and uses this SM ID to get the SM endpoint from the SM Registry.
From that SM endpoint, the user can request the SME, which contains the required value.

{% include image.html file="blog/2024-02-15-integrating-ditto-ass-basyx/aas-sequenz.svg" alt="Sequence of data flow through AAS infrastructure" max-width=1000 %}
*Figure 2: Sequence of data flow through AAS infrastructure*

If you want to dig deeper into the specifics of the AAS, consult the [AAS Reading Guide](https://www.plattform-i40.de/IP/Redaktion/DE/Downloads/Publikation/AAS-ReadingGuide_202201.html), which helps the interested reader to navigate through the available material.

### Eclipse BaSyx

[Eclipse BaSyx](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components) is an open-source project hosted by the Eclipse Foundation providing components to deploy an Industry 4.0 middleware.
Apart from other features, Eclipse BaSyx provides several easy-to-use off-the-shelf components to realize an AAS infrastructure:

* [AAS Server Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Server)
* [Registry Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_Registry)
* [DataBridge Component](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_DataBridge)
* [AAS Web UI](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Web_UI)

You can pull them from Docker Hub or [follow the instructions](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_Docker) to build them yourself.

In this post, we mainly work with the AAS Server Component and the Registry Component.

## Architectural Considerations

Making Eclipse Ditto Things available in an AAS infrastructure, in our case from the Eclipse Basyx project, boils down to making Thing data available as Submodels of an AAS accessible via the AAS Interface.

We see three approaches to achieve this:

* BaSyx AAS SM server *pulls* the current state from Eclipse Ditto via a *wrapper* around Eclipse Ditto
  This approach requires the creation of a custom AAS infrastructure around Eclipse Ditto without the chance of reusing existing components of the Eclipse Basyx project.
  The Eclipse Ditto project followed a comparable approach to implement [Web of Things](https://eclipse.dev/ditto/2022-03-03-wot-integration.html) (WoT) APIs, which is another specification to integrate IoT devices from different contexts and align their utilized data model.
  Ditto now allows the generation of new Things based on a WoT Thing Description.
* BaSyx AAS SM server *pulls* the current state from Eclipse Ditto via a *bridge* component, which Eclipse Basyx already provides.
  To integrate the bridge, the BaSyx SM-server component has a delegation feature, where the user can configure an SME with an endpoint to which the server delegates incoming requests.
  The configured endpoint can reference the bridge that then retrieves the actual data from Ditto and applies transformation logic.
* Eclipse Ditto *pushes* the latest updates to a BaSyx SM server
  For this approach, we configure Eclipse Ditto to notify the BaSyx SM server about any change to the relevant Things. During the creation of the notification message, Ditto applies a payload mapping to transform the data into the AAS format. The BaSyx SM server then caches the received submodel element and responds directly to the requests by the users.

{% include image.html file="blog/2024-02-15-integrating-ditto-ass-basyx/push.svg" alt="Push approach sequence" max-width=1000 %}
*Figure 3: Push approach sequence*

As the push approach treats the AAS infrastructure as a blackbox and almost all configuration happens within Eclipse Ditto we will follow this approach here.

## Mapping of Data Models

Eclipse Ditto and Eclipse Basyx work with different data structures and conceptual elements to represent device and asset data. Since we want to convert between these data models, we need to come up with a mapping between them:

| Eclipse Ditto | Asset Administration Shell |
| ------------- | -------------------------- |
| Namespace     | Asset Administration Shell |
| Thing         |  ---                       |
| Features      | Submodel                   |
| Property      | Submodel Element           |
| Attribute     | Submodel Element           |

*Table 1: Concept mapping from Eclipse Ditto to the AAS*

We map a Ditto [`Namespace`](https://eclipse.dev/ditto/basic-namespaces-and-names.html#namespace) to a single AAS. An AAS holds multiple SMs, and not all of these SMs necessarily have counterparts in Ditto. We thus treat a `Thing` as an opaque concept and do not define an explicit mapping for a `Thing` but map each [`Feature`](https://eclipse.dev/ditto/basic-feature.html) to one SM.
[`Property`](https://eclipse.dev/ditto/basic-feature.html#feature-properties) and [`Attribute`](https://eclipse.dev/ditto/basic-thing.html#attributes) are mapped to SMEs.

By that, it is possible to have more than one Thing organized in one AAS. This can especially be useful if an AAS organizes complex equipment with different sensors and actuators, which belong together but are in multiple Things.

## Integration steps

With the more theoretical details completed, we can now turn to the actual implementation and describe what is required to integrate Eclipse Ditto into an AAS infrastructure of Eclipse BaSyx.

### Prerequisites

1. Running instance of [Eclipse Ditto](https://eclipse.dev/ditto/)
2. Running instance of [Eclipse BaSyx](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components)

Those two instances must be available, and a network connection must exist between them. In this tutorial, we use the demo environment of Eclipse Ditto available at `ditto.eclipseprojects.io`.

For our setup, we used version 3.0.1 for Eclipse Ditto and version 1.4.0 for Eclipse BaSyx.

### Payload Mappers from Ditto to BaSyx

Let us assume a device with a sensor named `machine:sensor` that is capable of measuring temperature values. This device may send sensor data to an Eclipse Ditto instance as a Ditto Protocol message [Ditto Protocol message](https://eclipse.dev/ditto/1.3/protocol-overview.html):

```json
{
  "topic": "machine/sensor/things/twin/commands/modify",
  "headers": {},
  "path": "/features/temperature/properties/value",
  "value": 46
}
```

If the device uses another message format, you can find more details on [how to map it](https://eclipse.dev/ditto/connectivity-mapping.html) to a Ditto Protocol message.

After such an update to a Thing, we want Ditto to map this information to an AAS-conforming representation and forward this via an outbound connection to an AAS server.
So the task in Eclipse Ditto is to define [payload mappers](https://eclipse.dev/ditto/connectivity-mapping.html) for these tasks in accordance with the mapping in [Mapping of Data Models](#mapping-of-data-models). Ditto allows the usage of JavaScript for creating the mappers. We will then configure connections in Ditto to the BaSyx components, where we filter for the relevant changes to a Thing and then trigger the respective mapper.

We need to implement the following mappers:

* Creation of AAS triggered by creation of new `namespaces`
* Creation of submodel triggered by creation of `feature`
* Creation and update of submodel element triggered by creation and modification of a `property` or `feature`

#### Map from Thing Creation to AAS Creation

The following snippet performs a mapping to an AAS, and we will run it every time a Thing is created.

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

In this mapping, we only use the `namespace`, which is the first part of the ID of a Thing, e.g., `machine` in our `machine:sensor` example Thing.
More precisely, the mapping creates a representation of an AAS with the ID `namespace` and returns a new message with this text payload. The Ditto connectivity service then runs the mapping and pushes the new message to the BaSyx AAS server to create the described AAS.
For example, whenever a Ditto Thing with the ID `machine:sensor` is created, an AAS with the ID `machine` will be created.

### Map from Feature creation to Submodel creation

The next mapper creates an AAS submodel, and we configure it in the connection to run for a newly created feature in a Ditto Thing.

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

Besides `namespace`, this mapper uses the parameters `name` and `path` from the Ditto Protocol message. The `name`
represents the second part of the Thing-ID, e.g., `sensor` from our `machine:sensor` example Thing. The `path` describes the part of the Thing whose change triggered the processed Ditto Protocol message. It may include the feature ID of the Ditto Thing or the whole path of the affected property of the Ditto Thing, but it can also be only `/` after the creation of a Ditto Thing. In our example message [above](#payload-mappers-from-ditto-to-basyx), the `path` is `/features/temperature/properties/value`.

The mapping function extracts the ID of the feature from the parameter `path` and uses this together with the `name` of the Ditto Thing to build the ID of the corresponding AAS submodel. For example, whenever the feature `temperature` of a Thing called `machine:sensor` is created, an AAS submodel with the ID `sensor_temperature` in the AAS `machine` will be created.

Similarly to the [AAS creation mapping](#map-from-thing-creation-to-aas-creation), the listed function returns a new message with a custom text payload. Below, we will create a connection so that this payload gets pushed to the BaSyx AAS server to trigger the creation of an AAS submodel there.

#### Map from Property Update to Submodel Update

The next mapper creates a submodel element representation, and we use it in the connection for every modification of a property in a Thing.

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

The mapper extracts the `feature_id` and the `property_id` from the `path`, which is only possible if the parameter `path` includes the `property_id`. So, in the configuration of the connection, we have to ensure that this mapper only runs for the right messages.
Moreover, we can access the `value` of the modified `property`, which will be set as `value` in the submodel element from the `textPayload` output.

For example if a message updates the `path`: `/features/temperature/properties/value` in the Thing `machine:sensor`, the submodel element with the ID `properties_value` in the submodel `sensor_temperature` will be updated with the new temperature as `value`.

We update a submodel element instead of the whole submodel when an existing Thing changes because the mapper only has access to the changed property of the Ditto Thing and no information about the other properties.
Therefore, submodel elements, which may already be part of the submodel due to previous updates, would implicitly be dropped.
With our approach, we preserve the existing properties and only modify the updated properties.

#### Create a connection to the BaSyx AAS server

To apply the introduced mappers, we configure a new [Ditto connection](https://eclipse.dev/ditto/basic-connections.html) to a BaSyx AAS server.
The listings below show the respective HTTP call using curl. We encode the payload by escaping certain characters and removing the line breaks. So we replaced newlines with `\n` and `'` with `'"'`.

The Javascript mappers from above are part of `piggybackCommand.connection.mappingDefinitions` in `mappingforShell`, `mappingforSubmodel` and `mappingforSubmodelElement`.

In the example, we post the connection configuration to the Ditto demo instance at `ditto.eclipseprojects.io`. When you use another Ditto instance, you need to adapt the call accordingly.
We assume you have access rights to the Ditto [Devops Commands](https://eclipse.dev/ditto/installation-operating.html#devops-commands) credentials in the used instance. The default devops credentials are:

* username: devops
* password: foobar

You can change the password by setting the environment variable *DEVOPS_PASSWORD* in the [gateway service](https://eclipse.dev/ditto/architecture-services-gateway.html).

Alternatively, an already existing password can be obtained and stored as an environment variable using the following command:
```bash
export DEVOPS_PWD=$(kubectl --namespace ditto get secret my-ditto-gateway-secret -o jsonpath="{.data.devops-password}" | base64 --decode)
```
Please be aware that this command assumes Ditto has been deployed within a namespace named "ditto".

Finally, you adjust the parameter `piggybackCommand.connection.uri` with the URL of the running BaSyx server to which Ditto should have network connectivity.

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
              "outgoingScript": "function mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra) {\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify({\n    conceptDictionary: [],\n    identification: {\n      idType: '"'Custom'"',\n      id: namespace\n    },\n    idShort: namespace,\n    dataSpecification: [],\n    modelType: {\n      name: '"'AssetAdministrationShell'"'\n    },\n    asset: {\n      identification: {\n        idType: '"'Custom'"',\n        id: namespace + '"'-asset'"'\n      },\n      idShort: namespace + '"'-asset'"',\n      kind: '"'Instance'"',\n      dataSpecification: [],\n      modelType: {\n        name: '"'Asset'"'\n      },\n      embeddedDataSpecifications: []\n    },\n    embeddedDataSpecifications: [],\n    views: [],\n    submodels: []\n  });\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(headers, textPayload, bytePayload, contentType);}"            
            }
          },
          "mappingforSubmodel": {
            "mappingEngine": "JavaScript",
            "options": {
                "outgoingScript": "function mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra) {\n  \n  let feature_id = path.split('"'/'"').slice(2);\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify(\n    {\n      parent: {\n        keys: [\n          {\n            idType: '"'Custom'"',\n            type: '"'AssetAdministrationShell'"',\n            value: namespace,\n            local: true\n          }\n        ]\n      },\n      identification: {\n        idType: '"'Custom'"',\n        id: name+'"'_'"'+feature_id\n      },\n      idShort: name+'"'_'"'+feature_id,\n      kind: '"'Instance'"',\n      dataSpecification: [],\n      modelType: {\n        name: '"'Submodel'"'\n      },\n      embeddedDataSpecifications: [],\n      submodelElements: []\n    }\n\n  );\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(headers, textPayload, bytePayload, contentType);}"
            }
          },
          "mappingforSubmodelElement": {
            "mappingEngine": "JavaScript",
            "options": {
              "outgoingScript": "function mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra) {\n  let property_id = path.split('"'/'"').slice(3).join('"'_'"');\n  let feature_id = path.split('"'/'"').slice(2,3);\n  let headers = dittoHeaders;\n  let dataType = typeof value;\n  dataType = mapDataType(dataType)\n\n  function mapDataType(dataType) {\n    switch (dataType) {\n        case '"'undefined'"':\n        return '"'Undefined'"';\n        case '"'boolean'"':\n        return '"'boolean'"';\n        case '"'number'"':\n        return '"'int'"';\n        case '"'string'"':\n        return '"'string'"';\n        case '"'symbol'"':\n        return '"'Symbol'"';\n        case '"'bigint'"':\n        return '"'BigInt'"';\n        case '"'object'"':\n        return '"'string'"';\n        case '"'function'"':\n        return '"'Function'"';\n        default:\n        return '"'Unknown'"';\n    }\n  }\n  let textPayload = JSON.stringify(\n  {\n    parent: {\n      keys: [\n        {\n          idType: '"'Custom'"',\n          type: '"'Submodel'"',\n          value: name+'"'_'"'+feature_id,\n          local: true\n        }\n      ]\n    },\n    idShort: property_id,\n    kind: '"'Instance'"',\n    valueType: dataType,\n    modelType: {\n      name: '"'Property'"'\n    },\n    value: value\n  }\n  );\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(headers, textPayload, bytePayload, contentType);}"
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
          }
        ]
      }
    }
  }' http://ditto.eclipseprojects.io/devops/piggyback/connectivity
```

When Ditto established the connection and our payload mappings work, it returns a successful HTTP response and otherwise an error message.

Without any further means, the payload mappings defined in `piggybackCommand.mappingDefinition` and set in `piggybackCommand.targets` get executed for all changes to a Thing.
Thus, we use [filtering](https://eclipse.dev/ditto/basic-changenotifications.html#filtering) with [RQL expressions](https://eclipse.dev/ditto/basic-rql.html) to make sure that our payload mappings are executed for the correct messages. For example, the filter

```json
_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),eq(resource:path,'"'/'"'))
```

for `mappingforShell` in `piggybackCommands.targets[0].topics[0]` makes sure that it only triggers for messages, which create a Ditto Thing.

Another example for `mappingForSubmodel` in `pigybackCommands.targets[1].topics[0]` makes sure, that the parameter `path` contains a Ditto `feature` and not a `property`:

```json
"_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),not(eq(resource:path,'"'/features'"')),like(resource:path,'"'/features*'"'),not(like(resource:path,'"'*properties*'"')))"
```

#### Setup Connection to an BaSyx AAS Registry

The newly created AAS should also be discoverable from the AAS registry. Because of that, we have to create a connection in Eclipse Ditto to the BaSyx AAS Registry.

We therefore, define another payload mapping to add a registry entry for the new AAS:

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
            address: 'http://basyx-aas-server:4001/aasServer/shells/' + namespace + '/aas',
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

As introduced in [Mapping of Data Models](#mapping-of-data-models), we map a `namespace` in Ditto to an AAS.
The new entry in the BaSyx Registry has to contain the endpoint of the BaSyx AAS server, which hosts the new AAS in the variable `endpoints.address`. So you need to adapt this value here and in the following HTTP request to the address of the BaSyx ASS server that you are using and configured in the [connection between Ditto and the BaSyx AAS Server](#create-a-connection-to-the-basyx-aas-server).

With this mapping, it is now possible to configure a new connection from Ditto to a BaSyx AAS registry through the following HTTP request:

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
              "outgoingScript": "function mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra) {\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify({\n    endpoints: [\n        {\n            address: '"'http://basyx-aas-server:4001/aasServer/shells/'"' + namespace + '"'/aas'"',\n            type: '"'http'"'\n        }\n    ],\n    modelType: {\n        name: '"'AssetAdministrationShellDescriptor'"'\n    },\n    identification: {\n        idType: '"'Custom'"',\n        id: namespace\n},\n    idShort: namespace,\n      asset: {\n          identification: {\n              idType: '"'Custom'"',\n              id: namespace + '"'-asset'"'\n          },\n          idShort: namespace + '"'-asset'"',\n          kind: '"'Instance'"',\n          dataSpecification: [],\n          modelType: {\n              name: '"'Asset'"'\n          },\n          embeddedDataSpecifications: []\n      },\n      submodels: []\n  });\n  let bytePayload = null;\n  let contentType = '"'application/json'"';\n  return Ditto.buildExternalMsg(headers, textPayload, bytePayload, contentType);}"
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

We list the JavaScript mapper in `piggybackCommand.connection.mappingDefinitions.mappingForShell.options.outgoingScript` and reference it as `mappingForShell` in `piggybackCommand.connection.targets[0].payloadMapping`.
The address of the BaSyx AAS registry is configured in the parameter `piggybackCommand.connection.uri`.

As filter, to make sure that our function in the Javascript only triggers after the creation of new Thing, we use:

```json
"_/_/things/twin/events?filter=and(in(topic:action,'"'created'"'),eq(resource:path,'"'/'"'))"
```

Since the registry uses the AAS server endpoint as a base to also get access to all submodels and submodel elements from the same AAS, it is enough to register the AAS endpoint.

### Test the connection

We now configured all required connections in Ditto and can test our setup.
All configured mappers trigger through changes to a Thing, so we begin by creating a Thing.

As the testing example, we again use the Ditto demo instance  `ditto.eclipseprojects.io`, which you need to adapt when using a custom Ditto instance.

#### Creating a Thing in Eclipse Ditto

##### Setup a common policy

To define authorization information to be used by the Things, we first create a [policy](https://eclipse.dev/ditto/basic-policy.html) with the policy-id `machine:my-policy`.

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

 You will get a response `201 Created` response, if the policy creation concluded successfuly. In the subsequent steps, we use the policy-id `machine:my-policy` to refer to the created policy.

#### Create the Thing

The next step is to create the actual Thing. We use the namespace and name `machine:my-policy` and policy-id `machine:my-policy` here:

```bash
NAMESPACE=machine
NAME=sensor
DEVICE_ID=$NAMESPACE:$NAME

curl -i -X PUT -u ditto:ditto -H 'Content-Type: application/json' --data '{
  "policyId": "'$POLICY_ID'"
}' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID
```

Again, a successful creation returns a `201 Created` response.

We configured two connections to trigger a mapper on the create event of a Thing to push a new AAS to the AAS server and a reference to that AAS in the AAS registry.

You can check whether the execution of the scripts was successful by running:

```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells
```

which should return the following result

```json
[{"modelType":{"name":"AssetAdministrationShell"},"idShort":"machine","identification":{"idType":"Custom","id":"machine"},"dataSpecification":[],"embeddedDataSpecifications":[],"submodels":[{"keys":[{"type":"AssetAdministrationShell","local":true,"value":"machine","idType":"Custom"},{"type":"Submodel","local":true,"value":"sensor_temperature","idType":"Custom"}]}],"asset":{"keys":[{"type":"Asset","local":true,"value":"machine-asset","idType":"Custom"}],"identification":{"idType":"Custom","id":"machine-asset"},"idShort":"machine-asset","kind":"Instance","dataSpecification":[],"modelType":{"name":"Asset"},"embeddedDataSpecifications":[]},"views":[],"conceptDictionary":[]}]
```

In addition, the request:

```bash
curl -X GET http://basyx-aas-registry:4000/registry/api/v1/registry
```

should return:

```json
[{"modelType":{"name":"AssetAdministrationShellDescriptor"},"endpoints":[{"address":"http://basyx-aas-server:4001/aasServer/shells/machine/aas","type":"http"}],"identification":{"idType":"Custom","id":"machine"},"idShort":"machine","asset":{"identification":{"idType":"Custom","id":"machine-asset"},"idShort":"machine-asset","kind":"Instance","dataSpecification":[],"modelType":{"name":"Asset"},"embeddedDataSpecifications":[]},"submodels":[]}]
```

At this point, the newly created Thing has no features, properties, or attributes yet.
So let us populate that Thing.

#### Create a feature for the twin

Next we create a feature for the Thing to contain a property with the data of a temperature sensor.

```bash
FEATURE_ID=temperature

curl -X PUT -u ditto:ditto -H 'Content-Type: application/json' --data-binary '{
  "properties": {
    "value": null
  }
}' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID/features/$FEATURE_ID
```

The feature creation triggers the mapper (`mappingforSubmodel`) to create a corresponding Submodel in the previously created AAS.

To check if this was successful, we use curl:

```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells/$NAMESPACE/aas/submodels/${NAME}_${FEATURE_ID}/submodel
```

which should result in the following response:

```json
{"parent":{"keys":[{"idType":"Custom","type":"AssetAdministrationShell","value":"machine","local":true}]},"identification":{"idType":"Custom","id":"sensor_temperature"},"idShort":"sensor_temperature","kind":"Instance","dataSpecification":[],"modelType":{"name":"Submodel"},"embeddedDataSpecifications":[],"submodelElements":[]}
```

### Updating the Twin

After we have successfully created a device, we can check if the update of a property works as well by executing:

```bash
curl -i -X PUT -u ditto:ditto -H "content-type: application/json" --data-binary '46' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID/features/$FEATURE_ID/properties/value
```

Again, we check if our change was successful:

```bash
curl -u ditto:ditto -w '\n' http://ditto.eclipseprojects.io/api/2/things/$DEVICE_ID
```

and expect:

```json
{"thingId":"machine:sensor","policyId":"machine:my-policy","features":{"temperature":{"properties":{"value":46}}}}
```

If this was successful, then the mapping `mappingforSubmodelElement` should trigger.
To verify that the Submodel was updated, call:

```bash
curl -X GET http://basyx-aas-server:4001/aasServer/shells/$NAMESPACE/aas/submodels/${NAME}_${FEATURE_ID}/submodel/submodelElements/properties_value
```

This should lead to the response:

```json
{"parent":{"keys":[{"idType":"Custom","type":"Submodel","value":"sensor_temperature","local":true}]},"idShort":"properties_value","kind":"Instance","valueType":"int","modelType":{"name":"Property"},"value":46}
```

Here, we see that we are abel to access the sensor data of the device through the AAS Submodel API via Eclipse BaSyx.

As an alternative to plain Json responses, you can use one of the UI-tools provided by the AAS community, like the [AAS Web UI](https://wiki.eclipse.org/BaSyx_/_Documentation_/_Components_/_AAS_Web_UI).

{% include image.html file="blog/2024-02-15-integrating-ditto-ass-basyx/AASDashboard.png" alt="AAS Dashboard" max-width=1000 %}
*Figure 4: BaSyx AAS Web UI*

## Summary

During this post, we present our approach for making Ditto Things available as AAS-Submodels.
We defined a mapping concept between Ditto Things and AAS. To apply the mapping concept, we created connections with mappers from Ditto to a BaSyx AAS server and a BaSyx AAS registry. Afterward, we tested the connections with an example Ditto Thing and data from a sensor.

Here, the capabilities of Ditto allowed us to integrate devices connected to Ditto into AAS. We, therefore, assume that our presented approach can be comparably applied to other technologies.

<br/>
<br/>
Milena Jäntgen, [Sven Erik Jeroschewski](https://github.com/eriksven) and [Max Grzanna](https://github.com/max-grzanna) contributed to this post.
