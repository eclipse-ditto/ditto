---
title: Protocol specification for Things
keywords: protocol, specification, general
tags: [protocol]
permalink: protocol-specification-things.html
---


## Ditto Protocol topic structure for Things

A valid topic consists of six elements, describing the thing affected by this message and the type of the message:

```
<namespace>/<thingId>/things/<channel>/<criterion>/<action>
```

1. `namespace`: the namespace of the Thing.
2. `thingId`: the Thing ID.
3. `group`: the appropriate group for addressing Things is `things`. 
4. `channel`: the channel of the Protocol message; can either be `live` or `twin`.
5. `criterion`: the type of Protocol message; can either be `commands`, `events`, 
   [`search`](protocol-specification-things-search.html) or [`messages`](protocol-specification-things-messages.html).
6. `action`: the action executed on the Thing, only required for:
    1. Commands: [`create,`](protocol-specification-things-create.html)
       [`modify,`](protocol-specification-things-modify.html)
       [`retrieve`](protocol-specification-things-retrieve.html) or
       [`delete.`](protocol-specification-things-delete.html).
    2. Events: `created,` `modified,` `deleted.`


## Thing representation

### API version 1

The representation of a `Thing` in **API version 1** is specified as follows:

{% include docson.html schema="jsonschema/thing_v1.json" %}

### API version 2

The representation of a `Thing` in **API version 2** is specified as follows:

{% include docson.html schema="jsonschema/thing_v2.json" %}


## Common error responses for Things

These error responses can occur independent of the command that was sent:

| status | error                   | message                   |
|--------|-------------------------|---------------------------|
| `400`  | `things:id.invalid`     | The Thing ID `<thingId>` is not valid! |
| `429`  | `things:thing.toomanymodifyingrequests	`     | Too many modifying requests are already outstanding to the Thing with ID `<thingId>`. |
| `503`  | `things:thing.unavailable` | The Thing with the given ID is not available, please try again later. |
