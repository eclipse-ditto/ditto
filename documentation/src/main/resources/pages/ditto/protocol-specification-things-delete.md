---
title: Things - Delete protocol specification
keywords: protocol, specification, delete, thing
tags: [protocol]
permalink: protocol-specification-things-delete.html
---

All `topics` contain the `<channel>` which may be either `twin` or `live`.<br/>
For the meaning of those two channels see [Protocol specification](protocol-specification.html).

## Delete a Thing

Deletes the Thing identified by the `<namespace>` and `<thingId>` in the `topic`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Thing was deleted successfully.       |

### Event

The event emitted by Ditto after a thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/`     |

**Example:** 
[Delete a Thing](protocol-examples-deletething.html)

## Delete all Attributes of a Thing

Deletes all Attributes of a Thing identified by the `<namespace>` and `<thingId>` in the `topic`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/attributes`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/attributes`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - Attributes were deleted successfully.       |

### Event

The event emitted by Ditto after the attributes of a thing were deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/attributes`     |

**Example:** 
[Delete Attributes](protocol-examples-deleteattributes.html)


## Delete a single Attribute of a Thing

Delete a specific Attribute identified by the `<attributePath>` of the Thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/attributes/<attributePath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/attributes/<attributePath>`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The Attribute was deleted successfully.       |

### Event

The event emitted by Ditto after the Attribute of a thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/attributes/<attributePath>`     |

**Example:** 
[Delete a single Attribute](protocol-examples-deleteattribute.html)

## Delete the definition of a Thing

Delete the definition of the Thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/definition`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The definition was deleted successfully.       |

### Event

The event emitted by Ditto after the definition of a thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/definition`     |

**Example:** 
[Delete a single definition](protocol-examples-deletethingdefinition.html)



## Delete all Features of a Thing

Delete all Features of a Thing identified by identified by the `<namespace>` and the `<thingId>` in the `topic`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The Features were deleted successfully.       |

### Event

The event emitted by Ditto after the Features of a Thing were deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features`     |

**Example:** 
[Delete Features](protocol-examples-deletefeatures.html)


## Delete single Feature of a Thing

Delete a specific Feature (identified by the `<featureId>` in the `path`) of a Thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Feature was deleted successfully.       |

### Event

The event emitted by Ditto after a Feature of a Thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>`     |

**Example:** 
[Delete a single Feature](protocol-examples-deletefeature.html)


## Delete Definition of a Feature

Delete the Definition of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).


### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>/definition`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Definition was deleted successfully.       |

### Event

The event emitted by Ditto after the Feature Definition of a Thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>/definition`     |

**Example:** 
[Delete Feature Definition](protocol-examples-deletedefinition.html)

## Delete all Properties of a Feature

Delete all Properties of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).


### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>/properties`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Properties were deleted successfully.       |

### Event

The event emitted by Ditto after the Feature Properties of a Thing were deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>/properties`     |

**Example:** 
[Delete Feature Properties](protocol-examples-deleteproperties.html)

## Delete all desired Properties of a Feature

Delete all desired Properties of a Feature (identified by the `<featureId>` in the `path`) of the Thing 
(identified by the `<namespace>` and the `<thingId>` in the `topic`).


### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>/desiredProperties`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>/desiredProperties`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the desired Properties were deleted successfully.       |

### Event

The event emitted by Ditto after the Feature desired Properties of a Thing were deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>/desiredProperties`     |

**Example:** 
[Delete Feature Desired Properties](protocol-examples-deletedesiredproperties.html)

## Delete a single Property of a Feature

Delete a specific Property (identified by `<propertyPath>`) of a Feature (identified by the `<featureId>` in the `path`). 

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>/properties/<propertyPath>`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the Property was deleted successfully.       |

### Event

The event emitted by Ditto after a Feature Property of a Thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |

**Example:** 
[Delete a single Feature Property](protocol-examples-deleteproperty.html)

## Delete a single desired Property of a Feature

Delete a specific Property (identified by `<desiredPropertyPath>`) of a Feature (identified by the `<featureId>` in the `path`). 

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path**   |        | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the desired Property was deleted successfully.       |

### Event

The event emitted by Ditto after a Feature desired Property of a Thing was deleted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/deleted`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |

**Example:** 
[Delete a single Feature Desired Property](protocol-examples-deletedesiredproperty.html)
