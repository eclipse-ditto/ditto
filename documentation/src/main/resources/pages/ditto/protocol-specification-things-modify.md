---
title: Things - Modify protocol specification
keywords: protocol, specification, modify, thing
tags: [protocol]
permalink: protocol-specification-things-modify.html
---

All `topics` contain the `<channel>` which may be either `twin` or `live`.<br/>
For the meaning of those two channels see [Protocol specification](protocol-specification.html).

## Modify a Thing

This command modifies the thing specified by the `<namespace>` and `<thingId>` in the `topic` with the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/`     |
| **value** | The complete thing as JSON.<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

For modifying an existing thing, the authorized subject needs WRITE permission.<br/>
If the update is targeting the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.acl}}">ACL</a>, 
the authorized subject additionally needs ADMINISTRATE permission. 

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Thing was modified successfully.       |
|            | `403`  | Not Modifiable - The Thing could not be modified as the requester had insufficient permissions ('WRITE' is required).  |
|            | `404`  | Not Found - The Thing was not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/`     |
| **value** | The modified Thing as JSON<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify a Thing](protocol-examples-modifything.html)


## Modify all Attributes of a Thing

Create or modify the Attributes of a Thing identified by the `<namespace>` and `<thingId>` in the `topic`.
The Attributes will be replaced by the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/attributes`     |
| **value** | The attributes of the thing as JSON, see property `attributes` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/attributes`                      |
| **value**  |        | The modified attributes as JSON, see property `attributes` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `204`  | Success - Attributes were modified successfully.       |
|            | `403`  | Not Modifiable - The Attributes could not be modified as the requester had insufficient permissions ('WRITE' is required).  |
|            | `404`  | Not Found - The Thing or Attributes were not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the attributes of a thing were modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/attributes`     |
| **value** | The modified attributes of the thing as JSON, see property `attributes` of the Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify Attributes](protocol-examples-modifyattributes.html)


## Modify a single Attribute of a Thing

Create or modify a specific attribute identified by the `<attributePath>` of the Thing.
The attribute will be created in case it doesn't exist yet, otherwise the thing attribute is updated.
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
| **status** | _code_ |                          | 
|            | `204`  | Success - The Attribute was modified successfully.       |
|            | `403`  | Not Modifiable - The Attribute could not be modified as the requester had insufficient permissions ('WRITE' is required).         |
|            | `404`  | Not Found - The Thing or Attribute was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Attribute of a thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The modified Attribute of the Thing as JSON value. |

**Example:** 
[Modify a single Attribute](protocol-examples-modifyattribute.html)


## Modify all Features of a Thing

Create or modify the Features of a Thing identified by identified by the `<namespace>` and the `<thingId>` in the topic.<br/>
The list of Features will be replaced by the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The Features were modified successfully.       |
|            | `403`  | Not Modifiable - The Features could not be modified as the requester had insufficient permissions ('WRITE' is required).          |
|            | `404`  | Not Found - The Thing or Features were not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Features of a Thing were modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of the Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify Features](protocol-examples-modifyfeatures.html)


## Modify single Feature of a Thing

Create or modify a specific Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the topic).

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
| **status** | _code_ |                          | 
|            | `204`  | Success - the Feature was modified successfully.       |
|            | `403`  | Not Modifiable - The Feature could not be modified as the requester had insufficient permissions ('WRITE' is required).  |
|            | `404`  | Not Found - The Thing or Feature was not found or requester had insufficient permissions.   |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a Feature of a Thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>`     |
| **value** | The modified Feature of the Thing as JSON. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify a single Feature](protocol-examples-modifyfeature.html)


## Modify Definition of a Feature

Create or modify the Definition of a Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The Definition of the Feature as JSON array, see property `definition` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Definition was modified successfully.       |
|            | `403`  | Not Modifiable - The Definition could not be modified as the requester had insufficient permissions ('WRITE' is required).  |
|            | `404`  | Not Found - The Thing, Feature or Definition was not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Feature Definition of a Thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The modified Definition of the Feature as JSON array, see property `properties` of the Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify Feature Definition](protocol-examples-modifydefinition.html)


## Modify all Properties of a Feature

Create or modify the Properties of a Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The Properties of the Feature as JSON, see property `properties` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingId>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Properties were modified successfully.       |
|            | `403`  | Not Modifiable - The Properties could not be modified as the requester had insufficient permissions ('WRITE' is required).  |
|            | `404`  | Not Found - The Properties were not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after the Feature Properties of a Thing were modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The modified Properties of the Feature as JSON, see property `properties` of the Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |

**Example:** 
[Modify Feature Properties](protocol-examples-modifyproperties.html)


## Modify a single Property of a Feature

Create or modify a specific Property (identified by `<propertyPath>`) of a Feature (identified by the `<featureId>` in the `path`). 
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
| **status** | _code_ |                          | 
|            | `204`  | Success - the Property was modified successfully.       |
|            | `403`  | Not Modifiable - The Property could not be modified as the requester had insufficient permissions ('WRITE' is required).   |
|            | `404`  | Not Found - The Thing or Property was not found or requester had insufficient permissions.  |
|            |        | See [Thing Error Responses](protocol-examples-errorresponses.html) for examples of other error responses. |

### Event

The event emitted by Ditto after a Feature Property of a Thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingId>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The modified Property of the Thing as JSON. |

**Example:** 
[Modify a single Feature Property](protocol-examples-modifyproperty.html)
