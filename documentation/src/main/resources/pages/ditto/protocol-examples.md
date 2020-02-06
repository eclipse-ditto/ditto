---
title: Protocol examples
keywords: protocol, examples
tags: [protocol]
permalink: protocol-examples.html
---

The structure of the examples in this section is as follows:

## Command

Each example always starts with a command message that initiates an operation at Ditto (e.g. create a thing, retrieve a thing).

```json
{
  "topic": "com.acme/xdk_58/things/twin/commands/modify",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/"
  ...
}
```

## Response

A command always has a response which either reports the success or the failure. The example contains the success response.
See Thing Error responses for examples of messages that will be returned in case of an error.

```json
{
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": {
    ...
  },
  "status": 204
}
```

## Event

If Ditto triggers an event (e.g. Thing created, Attribute modified) as a result of the executed command, an example of such an event is also demonstrated.

```json
{
  "topic": "com.acme/thing_id_3141/things/twin/events/modified",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": {
    ...
  },
  "revision": 1
}
```

### ACK (Acknowledgement)
A command issuer can require a response and specify the acknowledgements (ACKs) which have to be successfully fulfilled
to regard the command as successfully executed.
Below an example is given for a successfully fulfilled ACK (status 200).

```json
{
  "topic": "com.acme/thing_name_3141/things/twin/acks/custom-ack",
  "headers": {
    "correlation-id": "a780b7b5-fdd2-4864-91fc-80df6bb0a636"
  },
  "path": "/",
  "value": {
    ...
  },
  "status": 200
}
```
