---
title: Protocol Specification
keywords: protocol, specification
tags: [protocol]
permalink: protocol-specification.html
---

## General protocol structure and envelopes

In order to comply with the Ditto protocol, a message must consist of the following three parts:

* A communication protocol envelope (e.g. AMQP, WebSocket)
* A Ditto protocol envelope (JSON)
* A Ditto protocol payload (JSON)


## Communication protocol envelope

The communication protocol envelope is defined by the underlying messaging system used to 
transport/serialize the messages over the wire.<br/>
Messages are supported from a system to Ditto, and from Ditto towards another system.<br/>
Please refer to the respective communication protocol binding for information how to encode the data in a protocol specific way.

See section [Protocol bindings](protocol-bindings.html).


## Ditto protocol envelope

The Ditto protocol envelope describes the content of the message (the affected thing entity, a message type, protocol version etc.) and allows the message to be routed by intermediary nodes to its final destination without parsing the actual payload.

The protocol envelope is formatted as JSON (`content-type=application/json`) and must correspond to the following JSON schema:

TODO include JSON structure


## Ditto protocol payload (JSON)

The Ditto model payload contains the application data e.g. an updated sensor value.

TODO include JSON structure


## Ditto protocol response

When sending a **command**, a **response** can be requested.
A response, can either indicate the success or the failure of the command. 
The Ditto response for a successful command has the following format:

TODO include JSON structure

In case the execution failed an error response with information about the error is sent:

TODO include JSON structure
