---
title: General
keywords: protocol, specification, general
tags: [protocol]
permalink: protocol-specification-general.html
---

## Communication pattern

The typical communication pattern when interacting with Ditto using the Ditto protocol is composed of multiple correlated messages.<br/>
Therefore, each message has a `correlation-id` header which can be used to associate related messages.

* [1] A **command** message is sent to the Bosch IoT Things service which is then processed.
* [2] The **response** given to the issuer of the **command** would be either a **success response** message or an **error response** message.<br/>
  Any response message contains a `status` and an `error` field which identify the error that occurred.
* [3] In addition an **event** message is triggered.<br/>
  The event describes that the change was applied to the **thing**.<br/>
  Interested parties can subscribe for such **events** and follow the evolving **thing**.
  
A schematic view for the communication flow:

TODO image

## Ditto protocol topic structure

A valid topic consists of five elements, describing the thing affected by this message and the type of the message:

```
<namespace>/<thingId>/<channel>/<type>/<action>
```

1. `namespace`: the namespace of the thing
2. `thingId`: the thing ID
3. `channel`: the “channel” of the message, can be either _live_ or _twin_
4. `type`: the type of message, can be either of `commands` or `events`
5. `action`: the action executed on the thing
    1. for commands: `create, modify, retrieve, delete`
    2. for events: `created, modified, deleted`

See the following pages for definitions of valid topics:

* [Create](protocol-specification-create.html)
* [Retrieve](protocol-specification-retrieve.html)
* [Search](protocol-specification-search.html)
* [Modify](protocol-specification-modify.html)
* [Delete](protocol-specification-delete.html)

## Common error responses

These error responses can occur independent of the command that was sent:

| status | error                   | message                   |
|--------|-------------------------|---------------------------|
| `400`  | `things:id.invalid`     | The Thing ID `<thingId>` is not valid! |
| `429`  | `things:thing.toomanymodifyingrequests	`     | Too many modifying requests are already outstanding to the Thing with ID `<thingId>`. |
| `503`  | `things:thing.unavailable` | The Thing with the given ID is not available, please try again later. |
