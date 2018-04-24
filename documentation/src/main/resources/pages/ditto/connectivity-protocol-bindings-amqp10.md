---
title: AMQP 1.0 protocol binding
keywords: binding, protocol, amqp, amqp10
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-amqp10.html
---

The Ditto Protocol message can be sent *as is* as **AMQP 1.0** message.
The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.

The `content-type` of AMQP 1.0 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

## AMQP 1.0 properties

Supported AMQP 1.0 properties which are interpreted in a specific way are:

* `content-type`: for defining the Ditto Protocol content-type
* `correlation-id`: for correlating request messages to responses

## Connection Source/Target format

The `sources` defines an array of sources (e.g. Hono's [Telemetry API](https://www.eclipse.org/hono/api/telemetry-api)) to consume messages from.

## Establishing connecting to an AMQP 1.0 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto operations command](installation-operating.html#connectivity-service-commands).

Example connection configuration to create a new AMQP 1.0 connection:

```json
{
  "id": "hono-example-connection-123",
  "connectionType": "amqp-10",
  "authorizationSubject": "<<<my-subject-id-included-in-policy-or-acl>>>",
  "failoverEnabled": true,
  "uri": "amqps://user:password@hono.eclipse.org:5671",
  "sources": [
    {
      "addresses": [
        "telemetry/FOO"
      ]
    }
  ],
  "targets": [
    {
      "address": "events/twin",
      "topics": [
        "_/_/things/twin/events"
      ]
    }
  ],
  "mappingContext": {
    "mappingEngine": "JavaScript",
    "options": {
      "incomingScript": "..",
      "outgoingScript": ".."
    }
  }
}
```

## Messages

Messages consumed via the AMQP 1.0 binding are treated similar to the [WebSocket binding](httpapi-protocol-bindings-websocket.html)
meaning that the messages are expected to be [Ditto Protocol](protocol-overview.html) messages serialized as JSON (as 
shown for example in the [protocol examples](protocol-examples.html)).