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

## Common error responses for Policies

These error responses can occur independent of the command that was sent:

| status | error                   | message                   |
|--------|-------------------------|---------------------------|
| `400`  | `policies:id.invalid`     | The Policy ID `<policyId>` is not valid! |
| `429`  | `policies:policy.toomanymodifyingrequests`     | Too many modifying requests are already outstanding to the Policy with ID `<policyId>`. |
| `503`  | `policies:policy.unavailable` | The Policy with the given ID is not available, please try again later. |


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
