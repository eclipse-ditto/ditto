---
title: Policies - Create-Or-Modify protocol specification
keywords: protocol, specification, create, modify, policy
tags: [protocol]
permalink: protocol-specification-policies-create-or-modify.html
---

## Create a Policy

Create a Policy with the ID specified by the `<namespace>/<policyName>` pair in the topic and the 
JSON representation provided in the `value`.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/create`     |
| **path**  | `/`     |
| **value** | The complete Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/create` |
| **path**   |        | `/`                      |
| **value**  |        | The created Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | *code* |                          | 
|            | `201`  | Success - The Policy was successfully created.       |

**Example:** [Create a Policy](protocol-examples-policies-createpolicy.html).

## Create or modify a Policy

This command modifies the Policy with the ID specified by the `<namespace>/<policyName>` pair in the `topic` and with 
the JSON provided in the `value`, if it already exists. Otherwise, the Policy is created.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/`     |
| **value** | The complete Policy as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

For modifying an existing policy, the authorized subject needs WRITE permission on the `policy:/.` resource.<br/>
If the Policy does not yet exist, the same rules apply as described for the [create command](#create-a-policy).

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/`                      |
| **value**  |        | The created Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). This field is not available, if the Policy entry already existed. |
| **status** | _code_ |    
|            | `201`  | Success - The Policy was successfully created.       |
|            | `204`  | Success - The Policy was successfully updated.       |

**Example:** [Modify a Policy](protocol-examples-policies-modifypolicy.html)

## Modify Policy entries

Modify the Policy entries of the Policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries`     |
| **value** | The Policy entries as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entries were successfully updated.       |

**Example:** [Modify all Policy entries](protocol-examples-policies-modifypolicyentries.html)

## Create or modify a Policy entry

Create or modify the Policy entry identified by the `<namespace>/<policyName>` pair in the `topic` field and the `
<label>` in the `path` field.
<br/>
If you specify a new label, the respective Policy entry will be created. <br/>
If you specify an existing label, the respective Policy entry will be updated.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries/<label>`     |
| **value** | The Policy entry as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries/<label>`                      |
| **value**  |        | The created Policy entry as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). This field is not available, if the Policy entry already existed. |
| **status** | _code_ |    
|            | `201`  | Success - The Policy entry was successfully created.       |
|            | `204`  | Success - The Policy entry was successfully updated.       |

**Example:** [Modify a single Policy entry](protocol-examples-policies-modifypolicyentry.html)

## Modify all subjects

Modify at once all subjects of the Policy entry identified by the `<namespace>/<policyName>` pair in the `topic` 
field and by the `<label>` in the `path` topic.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries/<label>/subjects`     |
| **value** | The subjects of the policy as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries/<label>/subjects`                      |
| **status** | _code_ |    
|            | `204`  | Success - The subjects of the policy were successfully updated.       |

**Example:** [Modify all subjects](protocol-examples-policies-modifysubjects.html)

## Create or modify a single subject

Create or modify the subject with ID `subjectId` of the Policy identified by the `<namespace>/<policyName>` pair in 
the `topic` field and by the `<label>` and the `<subjectId>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries/<label>/subjects/<subjectId>`     |
| **value** | The subject of the policy as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries/<label>/subjects/<subjectId>`                      |
| **value**  |        | The created subject as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). This field is not available, if the subject already existed. |
| **status** | _code_ |    
|            | `201`  | Success - The subject was successfully created.       |
|            | `204`  | Success - The subject was successfully updated.       |

**Example:** [Modify a single subject](protocol-examples-policies-modifysubject.html)

## Modify all resources

Modify all resources of the Policy identified by the `<namespace>/<policyName>` pair in the `topic` field and by the `<label>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries/<label>/resources`     |
| **value** | The resources of the policy as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries/<label>/resources`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy resources were successfully updated.       |

**Example:** [Modify all resources](protocol-examples-policies-modifyresources.html)

## Create or modify a single resource

Create or modify the resource identified by the `path` field of the Policy entry identified the 
`<namespace>/<policyName>` pair in the `topic` field and the `<resource>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**  | `/entries/<label>/resources/<resource>`     |
| **value** | The Policy resource as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify` |
| **path**   |        | `/entries/<label>/resources/<resource>`                      |
| **value**  |        | The created Policy resource as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). This field is not available, if the resource already existed. |
| **status** | _code_ |    
|            | `201`  | Success - The resource was successfully created.       |
|            | `204`  | Success - The resource was successfully updated.       |

**Example:** [Modify a single resource](protocol-examples-policies-modifyresource.html)

## Modify all policy imports

Modify all imports of the Policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                                                                                                                                 |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`                                                                                   |
| **path**  | `/imports`                                                                                                                            |
| **value** | The imports of the policy as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                                                   |
|------------|--------|---------------------------------------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify`     |
| **path**   |        | `/imports`                                              |
| **status** | _code_ |
|            | `204`  | Success - The Policy imports were successfully updated. |

**Example:** [Modify all imports](protocol-examples-policies-modifyimports.html)

## Create or modify a single policy import

Create or modify the import identified by the `path` field of the Policy import identified the
`<namespace>/<policyName>` pair in the `topic` field and the `<importedPolicyId>` in the `path` field.

### Command

| Field     | Value                                                                                                                         |
|-----------|-------------------------------------------------------------------------------------------------------------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/modify`                                                                           |
| **path**  | `/imports/<importedPolicyId>`                                                                                                 |
| **value** | The Policy import as JSON.<br/>see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation) |

### Response

| Field      |        | Value                                                                                                                                                                                                   |
|------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/modify`                                                                                                                                                     |
| **path**   |        | `/imports/<importedPolicyId>`                                                                                                                                                                           |
| **value**  |        | The created Policy import as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). This field is not available, if the resource already existed. |
| **status** | _code_ |
|            | `201`  | Success - The import was successfully created.                                                                                                                                                          |
|            | `204`  | Success - The import was successfully updated.                                                                                                                                                          |

**Example:** [Modify a single import](protocol-examples-policies-modifyimport.html)
