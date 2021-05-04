---
title: Things - Retrieve protocol specification
keywords: protocol, specification, retrieve, thing
tags: [protocol]
permalink: protocol-specification-things-retrieve.html
---

All `topics` contain the `<channel>` which may be either `twin` or `live`.<br/>
For the meaning of those two channels see [Protocol specification](protocol-specification.html).

## Retrieve a Thing

Retrieve the Thing specified by the `<namespace>` and `<thingId>` in the `topic`. 
The response includes all details about the Thing. 
Optionally you can use field selectors (see `fields`) to only get the specified fields.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/`     |
| **fields** | Contains a comma separated list of fields to be included in the returned JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/`                      |
| **value**  |        | The found complete Thing as JSON object. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a Thing](protocol-examples-retrievething.html)

## Retrieve multiple Things

Retrieve the Things specified by the `<namespace>` in the `topic` and the `thingIds` specified
in the value. Note that the `<thingName>` in this case contains the placeholder `_`. You can also omit the
`<namespace>` by using the placeholder `_`.
The response includes all details about the requested Things. 
Optionally you can use field selectors (see `fields`) to only get the specified fields.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/_/things/<channel>/commands/retrieve`     |
| **path**  | `/`     |
| **value** | a field `thingIds` that contains a JSON array of Thing IDs. |
| **fields** | Contains a comma separated list of fields to be included in the returned JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/_/things/<channel>/commands/retrieve` |
| **path**   |        | `/`                      |
| **value**  |        | JSON Array of all found Things (as JSON object). See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve multiple Things](protocol-examples-retrievethings.html)

## Retrieve all Attributes of a Thing

Retrieve the Attributes of a Thing identified by the `<namespace>` and `<thingId>` in the `topic`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/attributes`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/attributes`                      |
| **value**  |        | The Attributes of the Thing as JSON, see property attributes of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve Attributes](protocol-examples-retrieveattributes.html)


## Retrieve a single Attributes of a Thing

Retrieve a specific Attribute identified by the `<attributePath>` of the Thing.
The Attribute (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/attributes/<attributePath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/attributes/<attributePath>`                      |
| **value**  |        | The specific Attribute of the Thing as JSON. |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a single Attribute](protocol-examples-retrieveattribute.html)

## Retrieve the definition of a Thing

Retrieve the Definition of the Thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/definition`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/definition`                      |
| **value**  |        | The specific Definition of the Thing as JSON. |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a single Attribute](protocol-examples-retrieveattribute.html)

## Retrieve all Features of a Thing

Retrieve the Features of a Thing identified by identified by the `<namespace>` and the `<thingId>` in the `topic`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features`                      |
| **value**  |        | All Features of the Thing as JSON, see property features of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve Features](protocol-examples-retrievefeatures.html)


## Retrieve a single Feature of a Thing

Retrieve a specific Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>`                      |
| **value**  |        | The specific Feature of the Thing as JSON. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a single Feature](protocol-examples-retrievefeature.html)


## Retrieve Definition of a Feature

Retrieve the Definition of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>/definition`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **value**  |        | The Definition of the Feature as JSON array, see property properties of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve Feature Definition](protocol-examples-retrievedefinition.html)


## Retrieve all Properties of a Feature

Retrieve all Properties of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>/properties`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **value**  |        | The Properties of the Feature as JSON, see property properties of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve Feature Properties](protocol-examples-retrieveproperties.html)

## Retrieve all desired Properties of a Feature

Retrieve all desired Properties of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>/desiredProperties`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>/desiredProperties`                      |
| **value**  |        | The desired Properties of the Feature as JSON, see property desiredProperties of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload) |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve Feature Properties](protocol-examples-retrieveproperties.html)


## Retrieve a single Property of a Feature

Retrieve the Property of the Feature identified by the `<featureId>` in the path.
The Property (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>/properties/<propertyPath>`                      |
| **value**  |        | The specific Property of the Feature as JSON. |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a single Feature Property](protocol-examples-retrieveproperty.html)

## Retrieve a single desired Property of a Feature

Retrieve the desired Property of the Feature identified by the `<featureId>` in the path.
The Property (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path**   |        | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`                      |
| **value**  |        | The specific desired Property of the Feature as JSON. |
| **status** | _code_ |                          | 
|            | `200`  | Success.       |

**Example:** 
[Retrieve a single Feature Property](protocol-examples-retrievedesiredproperty.html)

