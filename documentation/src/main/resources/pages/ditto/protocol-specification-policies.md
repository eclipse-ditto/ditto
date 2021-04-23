---
title: Protocol specification for Policies
keywords: protocol, specification, general, policy, policies
tags: [protocol]
permalink: protocol-specification-policies.html
---


## Ditto Protocol topic structure for Policies

A valid topic consists of five elements, describing the policy affected by this message and the type of the message:

```
<namespace>/<policyName>/policies/commands/<action>
```

1. `namespace`: the namespace of the Policy.
2. `policyName`: the name of the Policy.
3. `group`: the group for addressing Policies is `policies`.
4. `criterion`: the type of Protocol messages addressing Policies is `commands`, 
    for [announcements](basic-signals-announcement.html) it is 
   [`announcements`](protocol-specification-policies-announcement.html).
5. `action`: the action executed on the Policy via `commands` criterion:
       [`create/modify`](protocol-specification-policies-create-or-modify.html),
       [`retrieve`](protocol-specification-policies-retrieve.html) or
       [`delete`](protocol-specification-policies-delete.html).
6. `subject`: for [announcements](basic-signals-announcement.html) the `subject` contains the announcement name
       
{% include note.html content="The topic path of the *policies* group does not contain a channel unlike the *things* group." %}
  
## Policy representation

The representation of a `Policy` is specified as follows:

{% include docson.html schema="jsonschema/policy.json" %}


## Commands

The following Policy commands are available:
* [create/modify commands](protocol-specification-policies-create-or-modify.html)
* [retrieve commands](protocol-specification-policies-retrieve.html)
* [delete commands](protocol-specification-policies-delete.html)

### Common errors to commands

Each Policy command could also result in an [error](protocol-specification-errors.html) response.  
The `"topic"` of such errors differ from the command `"topic"` - correlation is however possible via the
`"correlation-id"` header which is preserved in the error message.

The following table contains common error codes for Policy commands:

| **status** | Value                    |
|------------|--------------------------|
|    `400`   | Bad Format - The request could not be completed due to malformed request syntax. |
|    `401`   | Unauthorized - The request could not be completed due to missing authentication.       |
|    `403`   | Forbidden - The Policy could not be modified/deleted/retrieved as the requester had insufficient permissions.          |
|    `404`   | Not Found - The request could not be completed. The Policy with the given ID was not found in the context of the authenticated user.  |
|    `412`   | Precondition Failed - A precondition for reading or writing the (sub-)resource failed. This will happen for write requests, if you specified an `If-Match` or `If-None-Match` header, which fails the precondition check against the current ETag of the (sub-)resource.  |
|    `413`   | Request Entity Too Large - The created or modified Policy is larger than the configured limit (defaults to 100 kB).  |
|    `429`   | Too many modifying requests are already outstanding to a specific Policy. |

