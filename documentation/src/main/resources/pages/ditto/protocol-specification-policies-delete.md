---
title: Policies - Delete protocol specification
keywords: protocol, specification, delete, policy
tags: [protocol]
permalink: protocol-specification-policies-delete.html
---

## Delete a Policy

Delete the policy identified by the `<namespace>/<policyId>` pair in the `topic` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyId>/policies/commands/delete`     |
| **path**  | `/`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyId>/policies/commands/delete` |
| **path**   |        | `/`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entries were successfully updated.       |

**Example:** [Delete a Policy](protocol-examples-policies-deletepolicy.html)


## Delete a Policy entry

Deletes a Policy entry identified by the `<namespace>/<policyId>` pair in the `topic` field and the `<label>` in the
 `path` field. 

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyId>/policies/commands/delete`     |
| **path**  | `/entries/<label>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyId>/policies/commands/delete` |
| **path**   |        | `/entries/<label>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entries were successfully updated.       |

**Example:** [Delete a Policy entry](protocol-examples-policies-deletepolicyentry.html)

## Delete a single resource

Deletes the resource identified by the `<namespace>/<policyId>` pair in the `topic` field and the `<label>` and
 `<resource>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyId>/policies/commands/delete`     |
| **path**  | `/entries/<label>/resources/<resource>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyId>/policies/commands/delete` |
| **path**   |        | `/entries/<label>/resources/<resource>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entries were successfully updated.       |

**Example:** [Delete a resource](protocol-examples-policies-deleteresource.html)

## Delete a single subject

Delete the subject identified by the `<namespace>/<policyId>` pair in the `topic` field and the `<label>` and
 `<subjectId>` in the `path` field.

### Command

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `<namespace>/<policyId>/policies/commands/delete`     |
| **path**  | `/entries/<label>/subjects/<subjectId>`     |

### Response

| Field      |        | Value                    |
|------------|--------|--------------------------|
| **topic**  |        | `<namespace>/<policyId>/policies/commands/delete` |
| **path**   |        | `/entries/<label>/subjects/<subjectId>`                      |
| **status** | _code_ |    
|            | `204`  | Success - The Policy entries were successfully updated.       |

**Example:** [Delete a subject](protocol-examples-policies-deletesubject.html)

## Common errors

| **status** | Value                    |
|------------|--------------------------|
|    `400`   | Bad Format - The request could not be completed due to malformed request syntax. |
|    `401`   | Unauthorized - The request could not be completed due to missing authentication.       |
|    `403`   | Forbidden - The Policy could not be modified as the requester had insufficient permissions ('WRITE' is required).          |
|    `404`   | Not Found - The request could not be completed. The Policy with the given ID was not found in the context of the authenticated user.  |
|    `412`   | Precondition Failed - A precondition for reading or writing the (sub-)resource failed. This will happen for write requests, if you specified an If-Match or If-None-Match header, which fails the precondition check against the current ETag of the (sub-)resource.  |
|    `413`   | Request Entity Too Large - The created or modified entity is larger than the accepted limit of 100 kB.  |
|            | See [Policy Error Responses](protocol-examples-policies-errorresponses.html) for examples of other error responses. |