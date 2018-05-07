---
title: Protocol specification
keywords: action, channel, criterion, digital twin, envelope, payload, protocol, specification, twin
tags: [protocol]
permalink: protocol-specification.html
---

In order to comply with the Ditto Protocol, a Protocol message must consist of

* a Ditto Protocol envelope (JSON) and
* a Ditto Protocol payload (JSON).


## Ditto Protocol

The communication protocol envelope is implicitly defined by the underlying messaging system 
(e.g. [WebSocket](httpapi-protocol-bindings-websocket.html)) used to transport/serialize the messages over the wire.
Please refer to the respective [communication protocol binding](protocol-bindings.html) for information how to encode
the data in a protocol specific way.


### Ditto Protocol envelope {#dittoProtocolEnvelope}

The Ditto Protocol envelope describes the content of the message (the affected thing entity, a message type, protocol
version etc.) and allows the message to be routed by intermediary nodes to its final destination without parsing the
actual payload.

The Protocol envelope is formatted as JSON object (`content-type=application/json`) and must correspond to the 
following JSON schema:

{% include docson.html schema="jsonschema/protocol-envelope.json" %}


### Ditto Protocol payload (JSON) {#dittoProtocolPayload}

The Ditto model payload contains the application data, e.g. an updated sensor value or a Thing in JSON representation.
See the [specification for Things](protocol-specification-things.html) for the schema of a Thing.


### Ditto Protocol response {#dittoProtocolResponse}

When sending a **command**, a **response** can be requested.
A response, can either indicate the success or the failure of the command. 
The Ditto response for a successful command has the following format:

{% include docson.html schema="jsonschema/protocol-response.json" %}

In case the execution failed an error response with information about the error is sent:

{% include docson.html schema="jsonschema/protocol-error_response.json" %}

The following sections specify in detail which fields of the Protocol envelope, payload and response are used to contain
which information.


## Topic

Protocol messages contain a [topic](protocol-specification-topic.html) which is used for
* addressing an entity,
* defining the `channel` (*twin* vs. *live*) and
* specifying what the intention of the Protocol message is.

## Headers

Protocol messages contain headers as JSON object with arbitrary content.
There are some pre-defined headers which have a special meaning for Ditto:

| Header Key | Description                    | Possible values           |
|------------|--------------------------------|---------------------------|
| `correlation-id` | Used for correlating Protocol messages (e.g. a **command** would have the same correlation-id as the sent back **response** message. | `String` |
| `version` | Determines in which schema version the sent along `payload` should be interpreted. | `Number` - currently: \[1,2\] |
| `response-required` | Configures for a sent **command** whether a **response** should be sent back. | `Boolean` - default: `true` |

## Path

Contains a JSON pointer of where to apply the [value](#value) of the Protocol message.
May also be `/` when the value contains a replacement for the complete addressed entity (e.g. a complete
[Thing](basic-thing.html) JSON).

## Value

The JSON value to apply at the specified path.

## Status

Some Protocol messages (for example **responses**) contain a HTTP status code which is stored in this field.
