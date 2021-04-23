---
title: Protocol specification for Things
keywords: protocol, specification, general
tags: [protocol]
permalink: protocol-specification-things.html
---


## Ditto Protocol topic structure for Things

A valid topic consists of six elements, describing the thing affected by this message and the type of the message:

```
<namespace>/<thingName>/things/<channel>/<criterion>/<action>
```

1. `namespace`: the namespace of the Thing.
2. `thingName`: the name of the Thing.
3. `group`: the appropriate group for addressing Things is `things`. 
4. `channel`: the channel of the Protocol message; can either be `live` or `twin`.
5. `criterion`: the type of Protocol message; can either be `commands`, `events`, 
   [`search`](protocol-specification-things-search.html) or [`messages`](protocol-specification-things-messages.html).
6. `action`: the action executed on the Thing, only required for:
    1. Commands: [`create/modify`](protocol-specification-things-create-or-modify.html),
       [`merge`](protocol-specification-things-merge.html),
       [`retrieve`](protocol-specification-things-retrieve.html) or
       [`delete`](protocol-specification-things-delete.html).
    2. Events: `created`, `modified`, `merged`, `deleted.`


## Thing representation

The representation of a `Thing` in **API version 2** is specified as follows:

{% include docson.html schema="jsonschema/thing_v2.json" %}


## Commands

The following Thing commands are available:
* [create/modify commands](protocol-specification-things-create-or-modify.html)
* [merge commands](protocol-specification-things-merge.html)
* [retrieve commands](protocol-specification-things-retrieve.html)
* [delete commands](protocol-specification-things-delete.html)

### Common errors to commands

Each Thing command could also result in an [error](protocol-specification-errors.html) response.  
The `"topic"` of such errors differ from the command `"topic"` - correlation is however possible via the
`"correlation-id"` header which is preserved in the error message.

The following table contains common error codes for Thing commands:

| **status** | Value                    |
|------------|--------------------------|
|    `400`   | Bad Format - The request could not be completed due to malformed request syntax. |
|    `401`   | Unauthorized - The request could not be completed due to missing authentication.       |
|    `403`   | Forbidden - The Thing could not be modified as the requester had insufficient permissions ('WRITE' is required).          |
|    `404`   | Not Found - The request could not be completed. The Thing with the given ID was not found in the context of the authenticated user.  |
|    `412`   | Precondition Failed - A precondition for reading or writing the (sub-)resource failed. This will happen for write requests, if you specified an `If-Match` or `If-None-Match` header, which fails the precondition check against the current ETag of the (sub-)resource. |
|    `413`   | Request Entity Too Large - The created or modified entity is larger than the configured limit (defaults to 100 kB).  |
|    `429`   | Too many modifying requests are already outstanding to a specific Thing. |
