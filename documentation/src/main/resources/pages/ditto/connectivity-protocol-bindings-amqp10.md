---
title: AMQP 1.0 protocol binding
keywords: binding, protocol, amqp, amqp10
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-amqp10.html
---

When messages are sent in [Ditto Protocol](protocol-overview.html) (as `UTF-8` encoded String payload), 
the `content-type` of AMQP 1.0 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

If messages which are not in Ditto Protocol should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the AMQP 1.0 connection in order to transform the messages. 

## AMQP 1.0 properties

Supported AMQP 1.0 properties which are interpreted in a specific way are:

* `content-type`: for defining the Ditto Protocol content-type
* `correlation-id`: for correlating request messages to responses

## Specific connection configuration

### Source format

Any `source` item defines an `addresses` array of source identifiers (e.g. Eclipse Hono's 
[Telemetry API](https://www.eclipse.org/hono/api/telemetry-api)) to consume messages from
and `authorizationContext` array that contains the authorization subjects in whose context
inbound messages are processed. These subjects may contain placeholders, see 
[placeholders](basic-connections.html#placeholder-for-source-authorization-subjects) section for more information.

```json
{
  "addresses": [
    "<source>",
    "..."
  ],
  "authorizationContext": ["ditto:inbound-auth-subject", "..."]
}
```

### Target format

An AMQP 1.0 connection requires the protocol configuration target object to have an `address` property with a source
identifier. The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more 
information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html).

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
have READ permission on the Thing that is associated with a message.

```json
{
  "address": "<target>",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject", "..."]
}
```

### Specific configuration properties

The specific configuration properties are interpreted as 
[JMS Configuration options](https://qpid.apache.org/releases/qpid-jms-0.34.0/docs/index.html#jms-configuration-options). 
Use these to customize and tweak your connection as needed.



## Establishing connecting to an AMQP 1.0 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing 
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example connection configuration to create a new AMQP 1.0 connection:

```json
{
  "id": "hono-example-connection-123",
  "connectionType": "amqp-10",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "amqps://user:password@hono.eclipse.org:5671",
  "sources": [
    {
      "addresses": [
        "telemetry/FOO"
      ],
      "authorizationContext": ["ditto:inbound-auth-subject"]
    }
  ],
  "targets": [
    {
      "address": "events/twin",
      "topics": [
        "_/_/things/twin/events"
      ],
      "authorizationContext": ["ditto:outbound-auth-subject"]
    }
  ]
}
```

## Messages

Messages consumed via the AMQP 1.0 binding are treated similar to the [WebSocket binding](httpapi-protocol-bindings-websocket.html)
meaning that the messages are expected to be [Ditto Protocol](protocol-overview.html) messages serialized as JSON (as 
shown for example in the [protocol examples](protocol-examples.html)). If your payload is not conform to the [Ditto
Protocol](protocol-overview.html), you can configure a custom [payload mapping](connectivity-mapping.html).
