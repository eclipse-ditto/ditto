---
title: Protocol specification for Things
keywords: protocol, specification, general
tags: [protocol]
permalink: protocol-specification-things.html
---

The Things specification defines all Ditto Protocol commands, responses, and events for managing Things and their sub-resources (attributes, features, properties, definitions).

{% include callout.html content="**TL;DR**: You interact with Things through four command types -- create/modify, merge, retrieve, and delete -- each targeting the Thing itself or any sub-resource. All commands use the topic pattern `namespace/thingName/things/channel/commands/action`." type="primary" %}

## Topic structure for Things

A valid topic consists of six elements:

```text
<namespace>/<thingName>/things/<channel>/<criterion>/<action>
```

1. `namespace`: the namespace of the Thing.
2. `thingName`: the name of the Thing.
3. `group`: always `things`.
4. `channel`: either `live` or `twin`.
5. `criterion`: `commands`, `events`, [`search`](protocol-specification-things-search.html), or [`messages`](protocol-specification-things-messages.html).
6. `action`: depends on criterion -- see sections below.

All topics contain a `<channel>` which may be either `twin` or `live`.
For the meaning of those two channels see [Twin and live channels](protocol-twinlive.html).

## Thing representation

The JSON representation of a `Thing` in API version 2:

{% include docson.html schema="jsonschema/thing_v2.json" %}

## Common errors

Every Thing command can result in an [error](protocol-specification-errors.html) response. You correlate errors to commands via the `correlation-id` header.

| **status** | Description |
|---|---|
| `400` | Bad Format - malformed request syntax. |
| `401` | Unauthorized - missing authentication. |
| `403` | Forbidden - insufficient permissions (WRITE required). |
| `404` | Not Found - Thing not found for the authenticated user. |
| `412` | Precondition Failed - `If-Match` or `If-None-Match` header check failed. |
| `413` | Request Entity Too Large - entity exceeds the size limit (default: 100 kB). |
| `429` | Too Many Requests - too many outstanding modifying requests to this Thing. |

## Create and modify commands

Create and modify commands use the `modify` or `create` action in the topic path. A `modify` command with path `/` creates the Thing if it does not exist, or replaces it if it does.

### Create a Thing

Create a new Thing. The Policy must include at least one subject with READ and WRITE permissions. If you do not provide a Policy, Ditto creates a default Policy granting all permissions to the authorized subject.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/create` |
| **path** | `/` |
| **value** | Complete Thing as JSON. See [payload](protocol-specification.html#dittoProtocolPayload). |

**Response**: `201` (created). **Event**: `created` at path `/`. | [Create a Thing](protocol-examples-creatething.html)

### Create or modify a Thing

If the Thing exists, replace it. If not, create it.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/` |
| **value** | Complete Thing as JSON. See [payload](protocol-specification.html#dittoProtocolPayload). |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/`. | [Modify a Thing](protocol-examples-modifything.html)

### Create or modify Attributes

Replace all Attributes of a Thing.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/attributes` |
| **value** | Attributes as JSON object. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/attributes`. | [Modify Attributes](protocol-examples-modifyattributes.html) | [Create Attributes](protocol-examples-createattributes.html)

### Create or modify a single Attribute

Create or update a specific attribute. Use [JSON Pointer (RFC-6901)](https://tools.ietf.org/html/rfc6901) notation for the path.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/attributes/<attributePath>` |
| **value** | The attribute value as JSON. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/attributes/<attributePath>`. | [Modify Attribute](protocol-examples-modifyattribute.html) | [Create Attribute](protocol-examples-createattribute.html)

### Create or modify a Definition

Create or update the Thing's definition.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/definition` |
| **value** | Definition as JSON string. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/definition`. | [Modify Definition](protocol-examples-modifythingdefinition.html) | [Create Definition](protocol-examples-createthingdefinition.html)

### Create or modify all Features

Replace all Features of a Thing.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features` |
| **value** | All Features as JSON. See [payload](protocol-specification.html#dittoProtocolPayload). |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features`. | [Modify Features](protocol-examples-modifyfeatures.html) | [Create Features](protocol-examples-createfeatures.html)

### Create or modify a single Feature

Create or update a specific Feature identified by `<featureId>`.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>` |
| **value** | The Feature as JSON. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>`. | [Modify Feature](protocol-examples-modifyfeature.html) | [Create Feature](protocol-examples-createfeature.html)

### Create or modify a Feature Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>/definition` |
| **value** | Definition as JSON array. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>/definition`. | [Modify Feature Definition](protocol-examples-modifydefinition.html) | [Create Feature Definition](protocol-examples-createdefinition.html)

### Create or modify Feature Properties

Replace all Properties of a Feature.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>/properties` |
| **value** | Properties as JSON object. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>/properties`. | [Modify Properties](protocol-examples-modifyproperties.html) | [Create Properties](protocol-examples-createproperties.html)

### Create or modify a single Feature Property

Use [JSON Pointer (RFC-6901)](https://tools.ietf.org/html/rfc6901) to address nested properties.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>/properties/<propertyPath>` |
| **value** | The property value as JSON. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>/properties/<propertyPath>`. | [Modify Property](protocol-examples-modifyproperty.html) | [Create Property](protocol-examples-createproperty.html)

### Create or modify desired Properties

Replace all desired Properties of a Feature.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>/desiredProperties` |
| **value** | Desired properties as JSON object. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>/desiredProperties`. | [Modify Desired Properties](protocol-examples-modifydesiredproperties.html) | [Create Desired Properties](protocol-examples-createdesiredproperties.html)

### Create or modify a single desired Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/modify` |
| **path** | `/features/<featureId>/desiredProperties/<desiredPropertyPath>` |
| **value** | The desired property value as JSON. |

**Response**: `201` (created) or `204` (modified). **Event**: `created` or `modified` at path `/features/<featureId>/desiredProperties/<desiredPropertyPath>`. | [Modify Desired Property](protocol-examples-modifydesiredproperty.html) | [Create Desired Property](protocol-examples-createdesiredproperty.html)

## Merge commands

Merge commands use [JSON Merge Patch (RFC-7396)](https://tools.ietf.org/html/rfc7396) to partially update a Thing or its sub-resources. In case of conflicts, the patch value overwrites the existing value. Setting a field to `null` removes it.

### Merge a Thing

Apply a JSON Merge Patch to the entire Thing. Creates the Thing if it does not exist.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/` |
| **value** | JSON Merge Patch for the Thing. |

**Response**: `201` (created) or `204` (merged). **Event**: `merged` at path `/`. | [Merge a Thing](protocol-examples-mergething.html)

### Merge Attributes

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/attributes` |
| **value** | JSON Merge Patch for attributes. |

**Response**: `204`. **Event**: `merged` at path `/attributes`. | [Merge Attributes](protocol-examples-mergeattributes.html)

### Merge a single Attribute

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/attributes/<attributePath>` |
| **value** | JSON Merge Patch for the attribute. |

**Response**: `204`. **Event**: `merged` at path `/attributes/<attributePath>`. | [Merge Attribute](protocol-examples-mergeattribute.html)

### Merge the Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/definition` |
| **value** | A valid [Thing definition](basic-thing.html#definition). |

**Response**: `204`. **Event**: `merged` at path `/definition`. | [Merge Definition](protocol-examples-mergethingdefinition.html)

### Merge the Policy ID

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/policyId` |
| **value** | A valid [Policy ID](basic-thing.html#access-control). |

**Response**: `204`.

### Merge all Features

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features` |
| **value** | JSON Merge Patch for features. |

**Response**: `204`. **Event**: `merged` at path `/features`. | [Merge Features](protocol-examples-mergefeatures.html)

### Merge a single Feature

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>` |
| **value** | JSON Merge Patch for the Feature. |

**Response**: `201` (created). **Event**: `merged` at path `/features/<featureId>`. | [Merge Feature](protocol-examples-mergefeature.html)

### Merge a Feature Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>/definition` |
| **value** | JSON Merge Patch for the definition. |

**Response**: `204`. **Event**: `merged` at path `/features/<featureId>/definition`. | [Merge Feature Definition](protocol-examples-mergefeaturedefinition.html)

### Merge Feature Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>/properties` |
| **value** | JSON Merge Patch for properties. |

**Response**: `204`. **Event**: `merged` at path `/features/<featureId>/properties`. | [Merge Properties](protocol-examples-mergeproperties.html)

### Merge a single Feature Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>/properties/<propertyPath>` |
| **value** | JSON Merge Patch for the property. |

**Response**: `204`. **Event**: `merged` at path `/features/<featureId>/properties/<propertyPath>`. | [Merge Property](protocol-examples-mergeproperty.html)

### Merge desired Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>/desiredProperties` |
| **value** | JSON Merge Patch for desired properties. |

**Response**: `204`. **Event**: `merged` at path `/features/<featureId>/desiredProperties`. | [Merge Desired Properties](protocol-examples-mergedesiredproperties.html)

### Merge a single desired Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path** | `/features/<featureId>/desiredProperties/<desiredPropertyPath>` |
| **value** | JSON Merge Patch for the desired property. |

**Response**: `204`. **Event**: `merged` at path `/features/<featureId>/desiredProperties/<desiredPropertyPath>`. | [Merge Desired Property](protocol-examples-mergedesiredproperty.html)

## Retrieve commands

Retrieve commands read the current state of a Thing or its sub-resources. Use the optional `fields` parameter to select specific fields.

### Retrieve a Thing

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/` |
| **fields** | Optional comma-separated list of fields to include. |

**Response**: `200` with the Thing as JSON. | [Retrieve a Thing](protocol-examples-retrievething.html)

### Retrieve multiple Things

Use the placeholder `_` for `<thingName>` in the topic. Specify the Thing IDs in the value.

| Field | Value |
|---|---|
| **topic** | `<namespace>/_/things/<channel>/commands/retrieve` |
| **path** | `/` |
| **value** | JSON object with a `thingIds` array. |
| **fields** | Optional comma-separated list of fields to include. |

**Response**: `200` with a JSON array of Things. | [Retrieve multiple Things](protocol-examples-retrievethings.html)

### Retrieve Attributes

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/attributes` |

**Response**: `200` with the Attributes as JSON. | [Retrieve Attributes](protocol-examples-retrieveattributes.html)

### Retrieve a single Attribute

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/attributes/<attributePath>` |

**Response**: `200` with the Attribute value. | [Retrieve Attribute](protocol-examples-retrieveattribute.html)

### Retrieve the Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/definition` |

**Response**: `200` with the Definition. | [Retrieve Definition](protocol-examples-retrievethingdefinition.html)

### Retrieve all Features

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features` |

**Response**: `200` with all Features as JSON. | [Retrieve Features](protocol-examples-retrievefeatures.html)

### Retrieve a single Feature

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>` |

**Response**: `200` with the Feature as JSON. | [Retrieve Feature](protocol-examples-retrievefeature.html)

### Retrieve a Feature Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>/definition` |

**Response**: `200` with the Feature Definition. | [Retrieve Feature Definition](protocol-examples-retrievedefinition.html)

### Retrieve Feature Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>/properties` |

**Response**: `200` with the Properties as JSON. | [Retrieve Feature Properties](protocol-examples-retrieveproperties.html)

### Retrieve a single Feature Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>/properties/<propertyPath>` |

**Response**: `200` with the Property value. | [Retrieve Feature Property](protocol-examples-retrieveproperty.html)

### Retrieve desired Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>/desiredProperties` |

**Response**: `200` with the desired Properties as JSON. | [Retrieve Desired Properties](protocol-examples-retrieveproperties.html)

### Retrieve a single desired Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/retrieve` |
| **path** | `/features/<featureId>/desiredProperties/<desiredPropertyPath>` |

**Response**: `200` with the desired Property value. | [Retrieve Desired Property](protocol-examples-retrievedesiredproperty.html)

## Delete commands

Delete commands remove a Thing or one of its sub-resources. All delete responses return status `204` on success.

### Delete a Thing

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/` |

**Response**: `204`. **Event**: `deleted` at path `/`. | [Delete a Thing](protocol-examples-deletething.html)

### Delete all Attributes

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/attributes` |

**Response**: `204`. **Event**: `deleted` at path `/attributes`. | [Delete Attributes](protocol-examples-deleteattributes.html)

### Delete a single Attribute

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/attributes/<attributePath>` |

**Response**: `204`. **Event**: `deleted` at path `/attributes/<attributePath>`. | [Delete Attribute](protocol-examples-deleteattribute.html)

### Delete the Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/definition` |

**Response**: `204`. **Event**: `deleted` at path `/definition`. | [Delete Definition](protocol-examples-deletethingdefinition.html)

### Delete all Features

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features` |

**Response**: `204`. **Event**: `deleted` at path `/features`. | [Delete Features](protocol-examples-deletefeatures.html)

### Delete a single Feature

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>`. | [Delete Feature](protocol-examples-deletefeature.html)

### Delete a Feature Definition

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>/definition` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>/definition`. | [Delete Feature Definition](protocol-examples-deletedefinition.html)

### Delete Feature Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>/properties` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>/properties`. | [Delete Feature Properties](protocol-examples-deleteproperties.html)

### Delete a single Feature Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>/properties/<propertyPath>` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>/properties/<propertyPath>`. | [Delete Feature Property](protocol-examples-deleteproperty.html)

### Delete desired Properties

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>/desiredProperties` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>/desiredProperties`. | [Delete Desired Properties](protocol-examples-deletedesiredproperties.html)

### Delete a single desired Property

| Field | Value |
|---|---|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/delete` |
| **path** | `/features/<featureId>/desiredProperties/<desiredPropertyPath>` |

**Response**: `204`. **Event**: `deleted` at path `/features/<featureId>/desiredProperties/<desiredPropertyPath>`. | [Delete Desired Property](protocol-examples-deletedesiredproperty.html)

## Further reading

- [Search protocol](protocol-specification-things-search.html) -- searching across Things
- [Messages protocol](protocol-specification-things-messages.html) -- sending custom messages to/from Things
- [Acknowledgements](protocol-specification-acks.html) -- requesting delivery confirmation
- [Protocol examples](protocol-examples.html) -- complete message examples
