---
title: Things - Merge protocol specification 
keywords: protocol, specification, merge, thing 
tags: [protocol]
permalink: protocol-specification-things-merge.html
---

All `topics` contain the `<channel>` which may be either `twin` or `live`.<br/>
For the meaning of those two channels see [Protocol specification](protocol-specification.html).

For all merge commands the `value` field is provided in [JSON merge patch](https://tools.ietf.org/html/rfc7396)
format. In case of conflicts with the existing thing, the value provided in the patch overwrites the existing value.

## Merge a thing

This command merges the thing specified by the `<namespace>` and `<thingName>` in the topic with
the [JSON merge patch](https://tools.ietf.org/html/rfc7396) defined by the JSON in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [thing](basic-thing.html#thing) referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/`                      |
| **status** | *code* |                          | 
|            | `204`  | Success - the thing was merged successfully.       |

### Event

The event emitted by Ditto after a thing was merged.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/`     |
| **value** |  The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the thing referenced in the `topic`. |

**Example:** [Merge a thing.](protocol-examples-mergething.html)

## Merge all attributes of a thing

Merge the attributes of a thing identified by the `<namespace>` and `<thingName>` in the `topic`. The attributes will be
merged with the JSON merge patch provided in the `value` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/attributes`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [attributes](basic-thing.html#attributes) of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/attributes`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - attributes were merged successfully.       |

### Event

The event emitted by Ditto after the attributes of a thing were merged.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/attributes`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the attributes of the thing referenced in the `topic`. |

**Example:** [Merge attributes](protocol-examples-mergeattributes.html)

## Merge a single attribute of a thing

Merge a specific attribute identified by the `<attributePath>` of the thing. The attribute (JSON) can be referenced
hierarchically by applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [attribute](basic-thing.html#attributes) identified by `path`  of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/attributes/<attributePath>`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The attribute was merged successfully.       |

### Event

The event emitted by Ditto after a single attribute was merged.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/attributes/<attributePath>`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the attribute identified by `path` of the thing referenced in the `topic`. |

**Example:** [Merge a single attribute](protocol-examples-mergeattribute.html)

## Merge the definition of a thing

Merge the definition of a thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/definition`     |
| **value** | A valid [thing definition](basic-thing.html#definition) that replaces the definition of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The definition was merged successfully.       |

## Merge the policy ID of a thing

Merge the policy ID of a thing.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/policyId`     |
| **value** | A valid [policy ID](basic-thing.html#access-control) that replaces the policyId of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The definition was merged successfully.       |

### Event

The event emitted by Ditto after the definition was merged.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/definition`     |
| **value** |  The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the definition of the thing referenced in the `topic`. |

**Example:** [Merge a definition](protocol-examples-mergethingdefinition.html)

## Merge all features of a thing

Merge the features of a thing identified by the `<namespace>` and the `<thingName>` in the topic.<br/>
The list of features will be merged with the JSON merge patch provided in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features`     |
| **value**| The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [features](basic-thing.html#features) of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - The features were modified successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the features of the thing referenced in the `topic`. |

**Example:** [Merge features](protocol-examples-mergefeatures.html)

## Merge a single feature of a thing

Merge a specific feature (identified by the feature ID in the `path`) of the thing (identified by the `<namespace>` and
the `<thingName>` in the topic).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the specific [feature](basic-thing.html#features) identified by the feature ID in the `path` of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>`                      |
| **status** | _code_ |                          | 
|            | `201`  | Success - The feature was created successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the specific feature identified by the feature ID in the `path` of the thing referenced in the `topic`. |

**Example:** [Merge a single feature](protocol-examples-mergefeature.html)

## Merge the definition of a feature

Merge the definition of a feature (identified by the feature ID in the `path`) of the thing (identified by the
`<namespace>` and the `<thingName>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [definition of the feature](basic-feature.html#feature-definition) identified by the feature ID in the `path` of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>/definition`                      |
| **status** | _code_ |                          | 
|            | `204`  | Success - the definition was merged successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>/definition`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the definition of the feature identified by the feature ID in the `path` of the thing referenced in the `topic`. |

**Example:** [Merge feature definition](protocol-examples-mergefeaturedefinition.html)

## Merge all properties of a feature

Merge the properties of a feature (identified by the feature ID in the `path`) of the thing (identified by the
`<namespace>` and the `<thingName>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [properties of the feature](basic-feature.html#feature-properties) identified by the feature ID in the `path` of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>/properties`                      |
| **status** | *code* |                          | 
|            | `204`  | Success - the properties were modified successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>/properties`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the properties of the feature identified by the feature ID in the `path` of the thing referenced in the `topic`. |

**Example:** [Merge feature properties](protocol-examples-mergeproperties.html)

## Merge all desired properties of a feature

Merge the desired properties of a feature (identified by the feature ID in the `path`) of the thing (identified by the
`<namespace>` and the `<thingName>` in the `topic`).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>/desiredProperties`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [desired properties of the feature](basic-feature.html#feature-desired-properties) identified by the feature ID in the `path` of the thing referenced in the `topic`. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>/desiredProperties`                      |
| **status** | *code* |                          | 
|            | `204`  | Success - the desired properties were modified successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>/desiredProperties`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the desired properties of the feature identified by the feature ID in the `path` of the thing referenced in the `topic`. |

**Example:** [Merge feature desired properties](protocol-examples-mergedesiredproperties.html)

## Merge a single property of a feature

Merge a specific property (identified by `<propertyPath>`) of a feature (identified by the `<featureId>` in the `path`).
The property (JSON) can be referenced hierarchically by
applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [property](basic-feature.html#feature-properties) identified by the property path and the feature ID in `path` of the thing referenced in the `topic`.|

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>/properties/<propertyPath>`                      |
| **status** | *code* |                          | 
|            | `204`  | Success - the property was merged successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>/properties/<propertyPath>`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the property identified by the property path and the feature ID in `path` of the thing referenced in the `topic`.|

**Example:** [Merge a single feature property](protocol-examples-mergeproperty.html)

## Merge a single desired property of a feature

Merge a specific desired property (identified by `<desiredPropertyPath>`) of a feature (identified by the `<featureId>`
in the `path`). The property (JSON) can be referenced hierarchically by
applying [JSON Pointer notation (RFC-6901)](https://tools.ietf.org/html/rfc6901).

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/commands/merge`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |
| **value** | The JSON value in [JSON merge patch](https://tools.ietf.org/html/rfc7396) format that is applied to the [desired property](basic-feature.html#feature-desired-properties) identified by the desired property path and the feature ID in `path` of the thing referenced in the `topic`.|

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<thingName>/things/<channel>/commands/merge` |
| **path**   |        | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`                      |
| **status** | *code* |                          | 
|            | `204`  | Success - the desired property was merged successfully.       |

### Event

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<thingName>/things/<channel>/events/merged`     |
| **path**  | `/features/<featureId>/desiredProperties/<desiredPropertyPath>`     |
| **value** | The [JSON merge patch](https://tools.ietf.org/html/rfc7396) that was applied to the property identified by the desired property path and the feature ID in `path` of the thing referenced in the `topic`.|

**Example:** [Merge a single feature desired property](protocol-examples-mergedesiredproperty.html)
