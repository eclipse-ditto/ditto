---
title: Protocol specification for Things
keywords: protocol, specification, general
tags: [protocol]
permalink: protocol-specification-things.html
---


## Ditto Protocol topic structure for Things

A valid topic consists of six elements, describing the thing affected by this message and the type of the message:

```
/<namespace>/<thingId>/things/<channel>/<criterion>/<action>
```

1. `namespace`: the namespace of the `Thing`
2. `thingId`: the `Thing ID`
3. `group`: is, when addressing `Things`, _things_ 
4. `channel`: the “channel” of the Protocol message, can be either _live_ or _twin_
5. `criterion`: the type of Protocol message, can be either of _commands_, _events_, 
   _[search](protocol-specification-things-search.html)_ or _[messages](protocol-specification-things-messages.html)_
6. `action`: the action executed on the `Thing`, only needed for:
    1. _commands_: _[create](protocol-specification-things-create.html), [modify](protocol-specification-things-modify.html),
      [retrieve](protocol-specification-things-retrieve.html), [delete](protocol-specification-things-delete.html)_
    2. _events_: _created, modified, deleted_


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
