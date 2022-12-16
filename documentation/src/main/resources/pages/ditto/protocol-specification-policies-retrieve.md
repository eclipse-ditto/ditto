---
title: Policies - Retrieve protocol specification
keywords: protocol, specification, retrieve, policy
tags: [protocol]
permalink: protocol-specification-policies-retrieve.html
---

{% include note.html content="The *topic path* of policy commands contains no *channel* element. 
See the [specification](protocol-specification-policies.html#ditto-protocol-topic-structure-for-policies) for details." %}

## Retrieve a Policy

Retrieves a Policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/`     |
| **fields** | Contains a comma separated list of fields to be included in the returned JSON. |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/`                      |
| **value**  |        | The Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success.       |

**Example:** [Retrieve a Policy](protocol-examples-policies-retrievepolicy.html)

## Retrieve Policy entries

Retrieves all entries of the policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries`                      |
| **value**  |        | The Policy entries as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success. |

**Example:** [Retrieve all Policy entries](protocol-examples-policies-retrievepolicyentries.html)

## Retrieve a Policy entry

Retrieve a Policy entry identified by the `<namespace>/<policyName>` pair in the `topic` field 
and the `<label>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries/<label>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries/<label>`                      |
| **value**  |        | The Policy entry as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success.       |

**Example:** [Retrieve a single Policy entry](protocol-examples-policies-retrievepolicyentry.html)

## Retrieve Policy subjects

Retrieve the subjects of the policy identified by the `<namespace>/<policyName>` pair in the `topic` field
and the `<label>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries/<label>/subjects`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries/<label>/subjects`                      |
| **value**  |        | The subjects of the Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success.      |

**Example:** [Retrieve all subjects](protocol-examples-policies-retrievesubjects.html)

## Retrieve a Policy subject

Retrieve specific subject of the policy identified by the `<namespace>/<policyName>` pair in the `topic` field
and the `<label>` and `<subjectId>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries/<label>/subjects/<subjectId>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries/<label>/subjects/<subjectId>`                      |
| **value**  |        | The subject of the policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success.       |

**Example:** [Retrieve a single subject](protocol-examples-policies-retrievesubject.html)

## Retrieve Policy resources

Retrieve all resources of the policy identified by the `<namespace>/<policyName>` pair in the `topic` field 
and the `<label>` in the `path` field. 

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries/<label>/resources`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries/<label>/resources`                      |
| **value**  |        | The resources of the Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success. |

**Example:** [Retrieve all resources](protocol-examples-policies-retrieveresources.html)

## Retrieve a single Policy resources

Retrieve a resource identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<label>` and
 `<resource>` in the `path`field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve`     |
| **path**  | `/entries/<label>/resources/<resource>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**   |        | `/entries/<label>/resources/<resource>`                      |
| **value**  |        | The resource of the policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |    
|            | `200`  | Success. |

**Example:** [Retrieve a single resource](protocol-examples-policies-retrieveresource.html)

## Retrieve Policy imports

Retrieve all policy imports of the policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                                                 |
|-----------|-------------------------------------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**  | `/imports`                                            |

### Response

| Field      |        | Value                                                                                                                                            |
|------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve`                                                                                            |
| **path**   |        | `/imports`                                                                                                                                       |
| **value**  |        | The policy imports of the Policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |
|            | `200`  | Success.                                                                                                                                         |

**Example:** [Retrieve all policy imports](protocol-examples-policies-retrieveimports.html)

## Retrieve a single Policy import

Retrieve a policy import identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<importedPolicyId>` in the `path`field.

### Command

| Field     | Value                                                 |
|-----------|-------------------------------------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path**  | `/imports/<importedPolicyId>`                         |

### Response

| Field      |        | Value                                                                                                                                           |
|------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/retrieve`                                                                                           |
| **path**   |        | `/imports/<importedPolicyId>`                                                                                                                   |
| **value**  |        | The policy import of the policy as JSON object, see [Policy representation (JSON)](protocol-specification-policies.html#policy-representation). |
| **status** | _code_ |
|            | `200`  | Success.                                                                                                                                        |

**Example:** [Retrieve a single policy import](protocol-examples-policies-retrieveimport.html)
