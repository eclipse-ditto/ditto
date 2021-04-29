---
title: Things - Create-Or-Modify protocol specification
keywords: protocol, specification, create, modify, thing
tags: [protocol]
permalink: protocol-specification-things-create-or-modify.html
---

All `topics` contain the `<channel>` which may be either `twin` or `live`.<br/>
For the meaning of those two channels see [Protocol specification](protocol-specification.html).

## Create a Thing

This command creates the thing specified by the `<namespace>` and `<thingId>` in the topic defined by the JSON in the
value.
The <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.policy}}">Policy</a> of the created 
Thing must include at least one subject authorized to have READ, WRITE permissions.
If no Policy is provided within the command, a default Policy with an entry for the authorized subject with 
all permissions set to true will be created.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/create`     |
| **path**  | `/`     |
| **value** | The complete thing as JSON object, see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/create` |
| **path**   |        | `/`                      |
| **value**  |        | The created Thing as JSON object, see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |
| **status** | *code* |                          | 
|            | `201`  | Success - the thing was created successfully.       |

### Event

The event emitted by Ditto after a thing was created.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/`     |
| **value** | The created thing as JSON object, see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

**Example:** [Create a Thing.](protocol-examples-creatething.html)

## Create or modify a Thing

This command modifies the thing specified by the `<namespace>` and `<thingId>` in the `topic` with the JSON in the 
`value`, if it already exists. Otherwise, the thing is created.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/`     |
| **value** | The complete thing as JSON.<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload) |

For modifying an existing thing, the authorized subject needs WRITE permission.<br/>
If the thing does not yet exist, the same rules apply as described for the [create command](#create-a-thing).

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/`                      |
| **value**  |        | The created Thing as JSON object, see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if the Thing already existed. |
| **status** | _code_ |    
|            | `201`  | Success - the Thing was created successfully.       |
|            | `204`  | Success - the Thing was modified successfully.       |

### Event

The event emitted by Ditto after a thing was modified.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/`     |
| **value** | The modified Thing as JSON<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify a Thing](protocol-examples-modifything.html)

In case a thing was created, the event described for the [create command](#create-a-thing) will be emitted.

## Create or modify all Attributes of a Thing

Create or modify the Attributes of a Thing identified by the `<namespace>` and `<thingId>` in the `topic`.
The Attributes will be replaced by the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/attributes`     |
| **value** | The attributes of the thing as JSON, see property `attributes` of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/attributes`                      |
| **value**  |        | The created attributes as JSON, see property `attributes` of Things JSON schema. See [Ditto protocol payload (JSON).](protocol-specification.html#dittoProtocolPayload). This field is not available, if the thing already contained attributes. |
| **status** | _code_ |                          | 
|            | `201`  | Success - Attributes were created successfully.       |
|            | `204`  | Success - Attributes were modified successfully.       |

### Event

If the thing already contained attributes before the command was applied and they were thus overwritten, a `modified` 
event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/attributes`     |
| **value** | The modified attributes of the thing as JSON, see property `attributes` of the Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify Attributes](protocol-examples-modifyattributes.html)

If the thing did not yet contain attributes before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/attributes`     |
| **value** | The created Attributes of the Thing as JSON, see property `attributes` of the Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create Attributes](protocol-examples-createattributes.html)

## Create or modify a single Attribute of a Thing

Create or modify a specific attribute identified by the `<attributePath>` of the Thing.
The attribute will be created in case it doesn't exist yet, otherwise the thing attribute is updated.
The attribute (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The specific attribute of the Thing as JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/attributes/<attributePath>`                      |
| **value**  |        | The created attribute as JSON. This field is not available, if the attribute already existed. |
| **status** | _code_ |                          | 
|            | `201`  | Success - The Attribute was created successfully.       |
|            | `204`  | Success - The Attribute was modified successfully.       |

### Event

If the attribute already existed before the command was applied and it was thus overwritten by the command, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The modified Attribute of the Thing as JSON value. |

**Example:** [Modify a single Attribute](protocol-examples-modifyattribute.html)

If the attribute did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The created Attribute of the Thing as JSON value. |

**Example:** [Create a single Attribute](protocol-examples-createattribute.html)

## Create or modify a single definition of a Thing

Create or modify a definition of the Thing.
The definition will be created in case it doesn't exist yet, otherwise the thing definition is updated.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/definition`     |
| **value** | The specific definition of the Thing as JSON string value. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/definition`                      |
| **value**  |        | The created definition as JSON string value. This field is not available, if the definition already existed. |
| **status** | _code_ |                          | 
|            | `201`  | Success - The definition was created successfully.       |
|            | `204`  | Success - The definition was modified successfully.       |

### Event

If the definition already existed before the command was applied and it was thus overwritten by the command, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/definition`     |
| **value** | The modified definition of the Thing as JSON string value. |

**Example:** [Modify a definition](protocol-examples-modifythingdefinition.html)

If the definition did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/definition`     |
| **value** | The created definition of the Thing as JSON string value. |

**Example:** [Create a definition](protocol-examples-createthingdefinition.html)

## Create or modify all Features of a Thing

Create or modify the Features of a Thing identified by the `<namespace>` and the `<thingId>` in the topic.<br/>
The list of Features will be replaced by the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features`                      |
| **value**  |        | The created Features as JSON, see property `features` of Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if the thing already contained features. |
| **status** | _code_ |                          | 
|            | `201`  | Success - The Features were created successfully.       |
|            | `204`  | Success - The Features were modified successfully.       |

### Event

If the thing already contained Features before the command was applied and they were thus overwritten, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of the Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify Features](protocol-examples-modifyfeatures.html)

If the thing did not yet contain Features before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features`     |
| **value** | All Features of the Thing as JSON, see property `features` of the Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create Features](protocol-examples-createfeatures.html)

## Create or modify single Feature of a Thing

Create or modify a specific Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the topic).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>`     |
| **value** | The specific Feature of the Thing as JSON. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>`                      |
| **value**  |        | The created Feature as JSON. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if the Feature already existed. |
| **status** | _code_ |                          | 
|            | `201`  | Success - The Feature was created successfully.       |
|            | `204`  | Success - the Feature was modified successfully.       |

### Event

If the Feature already existed before the command was applied and it was thus overwritten by the command, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>`     |
| **value** | The modified Feature of the Thing as JSON. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify a single Feature](protocol-examples-modifyfeature.html)

If the Feature did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>`     |
| **value** | The created Feature of the Thing as JSON.<br/>see [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create a single Feature](protocol-examples-createfeature.html)

## Create or modify Definition of a Feature

Create or modify the Definition of a Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The Definition of the Feature as JSON array, see property `definition` of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **value**  |        | The created Definition of the Feature as JSON array, see property `definition` of Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if the Definition already existed. |
| **status** | _code_ |                          | 
|            | `201`  | Success - the Definition was created successfully.       |
|            | `204`  | Success - the Definition was modified successfully.       |

### Event

If the Feature Definition already existed before the command was applied and it was thus overwritten by the command, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The modified Definition of the Feature as JSON array, see property `properties` of the Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify Feature Definition](protocol-examples-modifydefinition.html)

If the Feature Definition did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The created Definition of the Feature as JSON array, see property `definition` of the Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create Feature Definition](protocol-examples-createdefinition.html)


## Modify all Properties of a Feature

Create or modify the Properties of a Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The Properties of the Feature as JSON, see property `properties` of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **value**  |        | The created Properties of the Feature as JSON object, see property `properties` of Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if Feature already contained Properties. |
| **status** | *code* |                          | 
|            | `201`  | Success - the Properties were created successfully.       |
|            | `204`  | Success - the Properties were modified successfully.       |

### Event

If the Feature already contained Properties before the command was applied and they were thus overwritten by the 
command, a `modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The modified Properties of the Feature as JSON, see property `properties` of the Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify Feature Properties](protocol-examples-modifyproperties.html)

If the Feature did not yet contain Properties before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The created Properties of the Feature as JSON object, see property `properties` of the Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create Feature Properties](protocol-examples-createproperties.html)

## Modify all desired Properties of a Feature

Create or modify the desired Properties of a Feature (identified by the Feature ID in the `path`) of the Thing (identified by the `<namespace>` and the `<thingId>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/desiredProperties`     |
| **value** | The desired Properties of the Feature as JSON, see property `desiredProperties` of Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/desiredProperties`                      |
| **value**  |        | The created desired Properties of the Feature as JSON object, see property `desiredProperties` of Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). This field is not available, if Feature already contained desired Properties. |
| **status** | *code* |                          | 
|            | `201`  | Success - the desired Properties were created successfully.       |
|            | `204`  | Success - the desired Properties were modified successfully.       |

### Event

If the Feature already contained desired Properties before the command was applied and they were thus overwritten by the command, a `modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/desiredProperties`     |
| **value** | The modified desired Properties of the Feature as JSON, see property `desiredProperties` of the Things JSON schema. See [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Modify Feature Desired Properties](protocol-examples-modifydesiredproperties.html)

If the Feature did not yet contain desired Properties before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/desiredProperties`     |
| **value** | The created desired Properties of the Feature as JSON object, see property `desiredProperties` of the Things JSON schema at [Ditto protocol payload (JSON)](protocol-specification.html#dittoProtocolPayload). |

**Example:** [Create Feature Desired Properties](protocol-examples-createdesiredproperties.html)

## Create or modify a single Property of a Feature

Create or modify a specific Property (identified by `<propertyPath>`) of a Feature (identified by the `<featureId>` in the `path`). 
The Property will be created if it doesn't exist or else updated.
The Property (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The specific Property of the Feature as JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/properties/<propertyPath>`                      |
| **value**  |        | The created Property of the Feature as JSON. This field is not available, if the Property already existed. |
| **status** | *code* |                          | 
|            | `201`  | Success - the Property was created successfully.       |               | 
|            | `204`  | Success - the Property was modified successfully.       |

### Event

If the Feature Property already existed before the command was applied and it was thus overwritten by the command, a 
`modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The modified Property of the Thing as JSON. |

**Example:** [Modify a single Feature Property](protocol-examples-modifyproperty.html)

If the Feature Property did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The created Property of the Thing as JSON. |

**Example:** [Create a single Feature Property](protocol-examples-createproperty.html)

## Create or modify a single desired Property of a Feature

Create or modify a specific desired Property (identified by `<desiredPropertyPath>`) of a Feature (identified by the `<featureId>` in the `path`). 
The desired Property will be created if it doesn't exist or else updated.
The Property (JSON) can be referenced hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |
| **value** | The specific desired Property of the Feature as JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path**   |        | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`                      |
| **value**  |        | The created desired Property of the Feature as JSON. This field is not available, if the Property already existed. |
| **status** | *code* |                          | 
|            | `201`  | Success - the desired Property was created successfully.       |               | 
|            | `204`  | Success - the desired Property was modified successfully.       |

### Event

If the Feature desired Property already existed before the command was applied and it was thus overwritten by the command, a `modified` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/modified`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |
| **value** | The modified desired Property of the Thing as JSON. |

**Example:** [Modify a single Feature desired Property](protocol-examples-modifydesiredproperty.html)

If the Feature desired Property did not yet exist before the command was applied, a `created` event will be emitted.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/created`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |
| **value** | The created Property of the Thing as JSON. |

**Example:** [Create a single Feature desired Property](protocol-examples-createdesiredproperty.html)
