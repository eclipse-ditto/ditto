---
title: Policies - Delete protocol specification
keywords: protocol, specification, delete, policy
tags: [protocol]
permalink: protocol-specification-policies-delete.html
---

## Delete a Policy

Delete the policy identified by the `<namespace>/<policyName>` pair in the `topic` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/delete`     |
| **path**  | `/`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/delete` |
| **path**   |        | `/`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy was successfully deleted.       |

**Example:** [Delete a Policy](protocol-examples-policies-deletepolicy.html)


## Delete a Policy entry

Deletes a Policy entry identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<label>` in the
 `path` field. 

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/delete`     |
| **path**  | `/entries/<label>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/delete` |
| **path**   |        | `/entries/<label>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entry was successfully deleted.       |

**Example:** [Delete a Policy entry](protocol-examples-policies-deletepolicyentry.html)

## Delete a single resource

Deletes the resource identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<label>` and
 `<resource>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/delete`     |
| **path**  | `/entries/<label>/resources/<resource>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/delete` |
| **path**   |        | `/entries/<label>/resources/<resource>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The resource was successfully deleted.       |

**Example:** [Delete a resource](protocol-examples-policies-deleteresource.html)

## Delete a single subject

Delete the subject identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<label>` and
 `<subjectId>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/delete`     |
| **path**  | `/entries/<label>/subjects/<subjectId>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/delete` |
| **path**   |        | `/entries/<label>/subjects/<subjectId>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The subject was successfully deleted.       |

**Example:** [Delete a subject](protocol-examples-policies-deletesubject.html)

## Delete a single policy import

Deletes the policy import identified by the `<namespace>/<policyName>` pair in the `topic` field and the `<importedPolicyId>` in the `path` field.

### Command

| Field     | Value                                               |
|-----------|-----------------------------------------------------|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path**  | `/imports/<importedPolicyId>`                       |

### Response

| Field      |        | Value                                                 |
|------------|--------|-------------------------------------------------------|
| **topic**  |        | `<namespace>/<policyName>/policies/commands/delete`   |
| **path**   |        | `/imports/<importedPolicyId>`                         |
| **status** | _code_ |
|            | `204`  | Success - The policy import was successfully deleted. |

**Example:** [Delete a policy import](protocol-examples-policies-deleteimport.html)