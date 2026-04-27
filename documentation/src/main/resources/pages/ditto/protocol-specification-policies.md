---
title: Protocol specification for Policies
keywords: protocol, specification, general, policy, policies
tags: [protocol]
permalink: protocol-specification-policies.html
---

The Policies specification defines all Ditto Protocol commands, responses, and announcements for managing Policies and their sub-resources (entries, subjects, resources, imports).

{% include callout.html content="**TL;DR**: Policy commands use the topic pattern `namespace/policyName/policies/commands/action` with no channel segment. You can create, modify, retrieve, and delete Policies, entries, subjects, resources, and imports." type="primary" %}

## Topic structure for Policies

A valid topic consists of five elements:

```
<namespace>/<policyName>/policies/commands/<action>
```

1. `namespace`: the namespace of the Policy.
2. `policyName`: the name of the Policy.
3. `group`: always `policies`.
4. `criterion`: `commands` for CRUD operations, or [`announcements`](#announcements) for policy announcements.
5. `action`: `create`, `modify`, `retrieve`, or `delete` for commands; the announcement name for announcements.

{% include note.html content="The topic path of the *policies* group does not contain a channel unlike the *things* group." %}

## Policy representation

The JSON representation of a `Policy`:

{% include docson.html schema="jsonschema/policy.json" %}

## Common errors

Every Policy command can result in an [error](protocol-specification-errors.html) response. You correlate errors to commands via the `correlation-id` header.

| **status** | Description |
|---|---|
| `400` | Bad Format - malformed request syntax. |
| `401` | Unauthorized - missing authentication. |
| `403` | Forbidden - insufficient permissions for the operation. |
| `404` | Not Found - Policy not found for the authenticated user. |
| `412` | Precondition Failed - `If-Match` or `If-None-Match` header check failed. |
| `413` | Request Entity Too Large - Policy exceeds the size limit (default: 100 kB). |
| `429` | Too Many Requests - too many outstanding modifying requests to this Policy. |

## Response and Event conventions

Policy commands follow the same response and event conventions as Thing commands. See
[Things - Response and Event conventions](protocol-specification-things.html#response-and-event-conventions)
for the full reference.

Key differences:
* Policy topics have no `channel` segment.
* Create/modify: `201` (created, `value` contains the resource) or `204` (modified, no `value`).
* Retrieve: `200`, `value` contains the requested resource.
* Delete: `204`, no `value`.
* All modify and delete commands require WRITE permission on `policy:/` or the targeted sub-resource.

## Create and modify commands

### Create a Policy

Create a Policy with the specified ID and JSON representation.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/create` |
| **path** | `/` |
| **value** | Complete Policy as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the Policy).

**Example:** [Create a Policy](protocol-examples-policies-createpolicy.html)

### Create or modify a Policy

If the Policy exists, replace it. If not, create it. You need WRITE permission on `policy:/.` to modify an existing Policy.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/` |
| **value** | Complete Policy as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the Policy) or `204` (modified, no `value`).

**Example:** [Modify a Policy](protocol-examples-policies-modifypolicy.html)

### Modify Policy entries

Replace all entries of the Policy.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries` |
| **value** | Policy entries as JSON. See [Policy representation](#policy-representation). |

**Response**: `204`.

**Example:** [Modify Policy entries](protocol-examples-policies-modifypolicyentries.html)

### Create or modify a Policy entry

Create or update a single Policy entry identified by `<label>`.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>` |
| **value** | Policy entry as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the resource) or `204` (modified, no `value`).

**Example:** [Modify a Policy entry](protocol-examples-policies-modifypolicyentry.html)

### Modify all subjects

Replace all subjects of a Policy entry.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>/subjects` |
| **value** | Subjects as JSON. See [Policy representation](#policy-representation). |

**Response**: `204`.

**Example:** [Modify subjects](protocol-examples-policies-modifysubjects.html)

### Create or modify a single subject

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>/subjects/<subjectId>` |
| **value** | Subject as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the resource) or `204` (modified, no `value`).

**Example:** [Modify a subject](protocol-examples-policies-modifysubject.html)

### Modify all resources

Replace all resources of a Policy entry.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>/resources` |
| **value** | Resources as JSON. See [Policy representation](#policy-representation). |

**Response**: `204`.

**Example:** [Modify resources](protocol-examples-policies-modifyresources.html)

### Create or modify a single resource

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>/resources/<resource>` |
| **value** | Resource as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the resource) or `204` (modified, no `value`).

**Example:** [Modify a resource](protocol-examples-policies-modifyresource.html)

### Modify Policy entry namespaces

Modify the namespace patterns of a Policy entry.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/entries/<label>/namespaces` |
| **value** | Namespace patterns as JSON array, e.g. `["com.acme", "com.acme.*"]`. An empty array means the entry applies to all namespaces. |

**Response**: `204`.

### Modify all Policy imports

Replace all imports of the Policy.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/imports` |
| **value** | Imports as JSON. See [Policy representation](#policy-representation). |

**Response**: `204`.

**Example:** [Modify imports](protocol-examples-policies-modifyimports.html)

### Create or modify a single Policy import

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/modify` |
| **path** | `/imports/<importedPolicyId>` |
| **value** | Import as JSON. See [Policy representation](#policy-representation). |

**Response**: `201` (created, `value` contains the resource) or `204` (modified, no `value`).

**Example:** [Modify an import](protocol-examples-policies-modifyimport.html)

## Retrieve commands

### Retrieve a Policy

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/` |
| **fields** | Optional comma-separated list of fields to include. |

**Response**: `200`, `value` contains the Policy as JSON.

**Example:** [Retrieve a Policy](protocol-examples-policies-retrievepolicy.html)

### Retrieve Policy entries

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries` |

**Response**: `200`, `value` contains all entries as JSON.

**Example:** [Retrieve Policy entries](protocol-examples-policies-retrievepolicyentries.html)

### Retrieve a Policy entry

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>` |

**Response**: `200`, `value` contains the entry as JSON.

**Example:** [Retrieve a Policy entry](protocol-examples-policies-retrievepolicyentry.html)

### Retrieve Policy subjects

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>/subjects` |

**Response**: `200` with subjects as JSON.

**Example:** [Retrieve subjects](protocol-examples-policies-retrievesubjects.html)

### Retrieve a Policy subject

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>/subjects/<subjectId>` |

**Response**: `200`, `value` contains the subject as JSON.

**Example:** [Retrieve a subject](protocol-examples-policies-retrievesubject.html)

### Retrieve Policy resources

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>/resources` |

**Response**: `200` with resources as JSON.

**Example:** [Retrieve resources](protocol-examples-policies-retrieveresources.html)

### Retrieve a single Policy resource

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>/resources/<resource>` |

**Response**: `200`, `value` contains the resource as JSON.

**Example:** [Retrieve a resource](protocol-examples-policies-retrieveresource.html)

### Retrieve Policy entry namespaces

Retrieve the namespace patterns of a Policy entry.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/entries/<label>/namespaces` |

**Response**: `200`, `value` contains the namespace patterns as JSON array, e.g. `["com.acme", "com.acme.*"]`. An empty array means the entry applies to all namespaces.

### Retrieve Policy imports

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/imports` |

**Response**: `200`, `value` contains all imports as JSON.

**Example:** [Retrieve imports](protocol-examples-policies-retrieveimports.html)

### Retrieve a single Policy import

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/retrieve` |
| **path** | `/imports/<importedPolicyId>` |

**Response**: `200`, `value` contains the import as JSON.

**Example:** [Retrieve an import](protocol-examples-policies-retrieveimport.html)

## Delete commands

All delete responses return status `204` on success.

### Delete a Policy

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path** | `/` |

**Example:** [Delete a Policy](protocol-examples-policies-deletepolicy.html)

### Delete a Policy entry

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path** | `/entries/<label>` |

**Example:** [Delete a Policy entry](protocol-examples-policies-deletepolicyentry.html)

### Delete a single subject

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path** | `/entries/<label>/subjects/<subjectId>` |

**Example:** [Delete a subject](protocol-examples-policies-deletesubject.html)

### Delete a single resource

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path** | `/entries/<label>/resources/<resource>` |

**Example:** [Delete a resource](protocol-examples-policies-deleteresource.html)

### Delete a single Policy import

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/commands/delete` |
| **path** | `/imports/<importedPolicyId>` |

**Example:** [Delete an import](protocol-examples-policies-deleteimport.html)

## Announcements

Policy announcements use the `announcements` criterion in the topic:

```
<namespace>/<policyName>/policies/announcements/<announcement-name>
```

The Ditto Protocol representation:

{% include docson.html schema="jsonschema/protocol-announcement.json" %}

### SubjectDeletionAnnouncement

Ditto publishes this announcement when subjects of a Policy are deleted or about to be deleted.

| Field | Value |
|---|---|
| **topic** | `<namespace>/<policyName>/policies/announcements/subjectDeletion` |
| **path** | `/` |
| **value** | JSON object with `deleteAt` (ISO-8601 timestamp) and `subjectIds` (JSON array of [subject](basic-policy.html#subjects) strings). |

**Example:** [Subject deletion announcement](protocol-examples-policies-announcement-subjectDeletion.html)

## Further reading

- [Protocol specification](protocol-specification.html) -- the full message format reference
- [Protocol examples](protocol-examples.html) -- complete message examples
