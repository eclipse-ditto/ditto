---
title: Things - Create protocol specification
keywords: command, create, event, protocol, response, specification, thing
tags: [protocol]
permalink: protocol-specification-things-create.html
---

All topics contain the `<channel>` which may be either `twin` or `live`.
For the meaning of those two channels please see [Protocol specification](protocol-specification.html).

## Create a Thing

This command creates the thing specified by the `<namespace>` and `<thingId>` in the topic defined by the JSON in the
value.
The <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a> of the created Thing
must include at least one subject authorized to READ, WRITE and ADMINISTRATE permissions.
If no ACL is provided within the command, a default ACL with an entry for the authorized subject with all permissions
set to true will be created.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/create`     |
| **path**  | `/`     |
| **value** | The complete thing as JSON object, see [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/create` |
| **path**   |        | `/`                      |
| **value**  |        | The created Thing as JSON object, see [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - the thing was created successfully.       |
|            | `409`  | Conflict - a thing with the given ID already exists. |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/`     |
| **value** | The created thing as JSON object, see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create a Thing.](protocol-examples-creatething.html)


## Create all Attributes of a Thing

This command creates the attributes of the thing identified by the `<namespace>` and `<thingId>` in the topic.
The attributes will be set to the JSON in the value.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/attributes`     |
| **value** | The attributes of the thing as JSON object, see property `attributes` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/attributes`                      |
| **value**  |        | The created attributes as JSON, see property `attributes` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - Attributes were created successfully.       |
|            | `403`  | Not Modifiable - Attributes could not be modified.        |
|            | `404`  | Not Found - Thing not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Attributes of a Thing were created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/attributes`     |
| **value** | The created Attributes of the Thing as JSON, see property `attributes` of the Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create Attributes.](protocol-examples-createattributes.html)


## Create a single Attribute of a Thing

Create a specific attribute identified by the `<attributePath>` of the Thing.
The attribute will be created in case it doesn't exist yet, otherwise the Thing attribute is updated.
The attribute (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The specific attribute of the Thing as JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/attributes/<attributePath>`                      |
| **value**  |        | The created attribute as JSON. |
| **status** | _code_ |                          | 
|            | `201`  | Success - Attribute was created successfully.       |
|            | `403`  | Not Modifiable - Attribute could not be modified.        |
|            | `404`  | Not Found - Thing not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Attribute of a thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The created Attribute of the Thing as JSON value. |

**Example:** [Create a single Attribute.](protocol-examples-createattribute.html)


## Create all Features of a Thing

Create the Features of a Thing identified by identified by the `<namespace>` and the `<thingId>` in the topic.
The list of Features will be replaced by the JSON in the value.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON object, see property `features` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features`                      |
| **value**  |        | The created Features as JSON, see property `features` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - the Features were created successfully.       |
|            | `403`  | Not Modifiable - the Features could not be modified.         |
|            | `404`  | Not Found - the Thing was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Features of a Thing were created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of the Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create Features.](protocol-examples-createfeatures.html)


## Create single Feature of a Thing

Create a specific Feature (identified by the Feature ID in the path) of the Thing (identified by the `<namespace>` and
the `<thingId>` in the topic).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>`     |
| **value** | The specific Feature of the Thing as JSON. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>`                      |
| **value**  |        | The created Feature as JSON. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - the Feature was created successfully.       |
|            | `403`  | Not Modifiable - the Feature could not be modified.         |
|            | `404`  | Not Found - the Thing or Feature was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a Feature of a Thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>`     |
| **value** | The created Feature of the Thing as JSON.<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Create a single Feature](protocol-examples-createfeature.html)


## Create Definition of a Feature

Create the Definition of a Feature (identified by the Feature ID in the path) of the Thing (identified by the
`<namespace>` and the `<thingId>` in the topic).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The Definition of the Feature as JSON array, see property `definition` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **value**  |        | The created Definition of the Feature as JSON array, see property `definition` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - the Definition was created successfully.       |
|            | `403`  | Not Modifiable - the Definition could not be modified.         |
|            | `404`  | Not Found - the Thing or Feature was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Feature Definition of a Thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The created Definition of the Feature as JSON array, see property `definition` of the Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create Feature Definition.](protocol-examples-createdefinition.html)


## Create all Properties of a Feature

Create the Properties of a Feature (identified by the Feature ID in the path) of the Thing (identified by the
`<namespace>` and the `<thingId>` in the topic).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The Properties of the Feature as JSON, see property `properties` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **value**  |        | The created Properties of the Feature as JSON object, see property `properties` of Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | *code* |                          | 
|            | `201`  | Success - the Properties were created successfully.       |
|            | `403`  | Not Modifiable - the Properties could not be modified.         |
|            | `404`  | Not Found - the Thing or Feature was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Feature Properties of a Thing were created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The created Properties of the Feature as JSON object, see property `properties` of the Things JSON schema at [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create Feature Properties.](protocol-examples-createproperties.html)


## Create a single Property of a Feature

Create a specific Property (identified by `<propertyPath>`) of a Feature (identified by the `<featureId>` in the path). 
The Property will be created if it doesn't exist or else updated.
The Property (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The specific Property of the Feature as JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/properties/<propertyPath>`                      |
| **value**  |        | The created Property of the Feature as JSON. |
| **status** | *code* |                          | 
|            | `201`  | Success - the Property was created successfully.       |
|            | `403`  | Not Modifiable - the Property could not be modified.         |
|            | `404`  | Not Found - the Property was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a Feature Property of a Thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The created Property of the Thing as JSON. |

**Example:** [Create a single Feature Property.](protocol-examples-createproperty.html)
