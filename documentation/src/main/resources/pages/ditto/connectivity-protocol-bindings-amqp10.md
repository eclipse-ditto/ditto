---
title: AMQP 1.0 protocol binding
keywords: binding, protocol, amqp, amqp10
tags: [protocol, connectivity, rql]
permalink: connectivity-protocol-bindings-amqp10.html
---

Consume messages from AMQP 1.0 endpoints via [sources](#source-format) and send messages to AMQP 1.0 endpoints via
[targets](#target-format).

## Content-type

When messages are sent in [Ditto Protocol](protocol-overview.html) (as `UTF-8` encoded String payload), 
the `content-type` of AMQP 1.0 messages must be set to:

```
application/vnd.eclipse.ditto+json
```

If messages, which are not in Ditto Protocol, should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the AMQP 1.0 connection in order to transform the messages. 

## AMQP 1.0 properties, application properties and message annotations

When set as external headers by outgoing payload or header mapping, the properties defined by AMQP 1.0 specification are
set to the corresponding header value. Conversely, the values of AMQP 1.0 properties are available for incoming payload
and header mapping as external headers. The supported AMQP 1.0 properties are:

* `message-id`
* `user-id`
* `to`
* `subject`
* `reply-to`
* `correlation-id`
* `content-type`
* `absolute-expiry-time`
* `creation-time`
* `group-id`
* `group-sequence`
* `reply-to-group-id`

External headers not on this list are mapped to AMQP application properties.
To set an application property whose name is identical to an AMQP 1.0 property, prefix it by
`amqp.application.property:`. The following [target header mapping](basic-connections.html#target-header-mapping) sets
the application property `to` to the value of the Ditto protocol header `reply-to`:
```json
{
  "headerMapping": {
    "amqp.application.property:to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  }
}
```

To read an application property whose name is identical to an AMQP 1.0 property, prefix it by
`amqp.application.property:`. The following [source header mapping](basic-connections.html#source-header-mapping) sets
the Ditto protocol header `reply-to` to the value of the application property `to`:
```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:amqp.application.property:to }}{%endraw%}"
  }
}
```

To set a message annotation, prefix it by `amqp.message.annotation:`. 
The following [target header mapping](basic-connections.html#target-header-mapping) sets
the message annotation `to` to the value of the Ditto protocol header `reply-to`:
```json
{
  "headerMapping": {
    "amqp.message.annotation:to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  }
}
```

To read a message annotation, prefix it by `amqp.message.annotation:`. 
The following [source header mapping](basic-connections.html#source-header-mapping) sets
the Ditto protocol header `reply-to` to the value of the message annotation `to`:
```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:amqp.message.annotation:to }}{%endraw%}"
  }
}
```

{% include note.html content="For now, setting or reading the AMQP 1.0 property 'content-encoding' is impossible." %}

## Specific connection configuration

The common configuration for connections in [Connections > Sources](basic-connections.html#sources) and 
[Connections > Targets](basic-connections.html#targets) applies here as well. 
Following are some specifics for AMQP 1.0 connections:

### Source format

Any `source` item defines an `addresses` array of source identifiers (e.g. Eclipse Hono's 
Telemetry API) to consume messages from,
and `authorizationContext` array that contains the authorization subjects, in whose context
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

#### Source acknowledgement handling

For AMQP 1.0 sources, when configuring 
[acknowledgement requests](basic-connections.html#source-acknowledgement-requests), consumed messages from the AMQP 1.0
endpoint are treated in the following way:

For Ditto acknowledgements with successful [status](protocol-specification-acks.html#combined-status-code):
* Acknowledges the AMQP 1.0 message with `accepted` outcome (see [AMQP 1.0 spec: 3.4.2 Accepted](http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-accepted))

For Ditto acknowledgements with mixed successful/failed [status](protocol-specification-acks.html#combined-status-code):
* If some of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery (e.g. based on a timeout):
   * Negatively acknowledges the AMQP 1.0 message with `modified[delivery-failed]` outcome (see [AMQP 1.0 spec: 3.4.5 Modified](http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-modified))
* If none of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery:
   * Negatively acknowledges the AMQP 1.0 message with `rejected` outcome (see [AMQP 1.0 spec: 3.4.3 Rejected](http://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-messaging-v1.0-os.html#type-rejected)) preventing redelivery by the AMQP 1.0 endpoint

### Target format

An AMQP 1.0 connection requires the protocol configuration target object to have an `address` property with a source
identifier. The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more 
information.

Target addresses for AMQP 1.0 are by default handled as AMQP 1.0 "queues". There is however the possibility to also 
configure AMQP 1.0 "topics" as well. In fact, the following formats for the `address` are 
supported:
* `the-queue-name` (when configuring w/o prefix, the `address` is handled as "queue")
* `queue://the-queue-name`
* `topic://the-topic-name`

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html), see 
[target topics and filtering](basic-connections.html#target-topics-and-filtering) for more information on that.

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the thing, which is associated with a message.

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

#### Target acknowledgement handling

For AMQP 1.0 targets, when configuring 
[automatically issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label), requested 
acknowledgements are produced in the following way:

Once the AMQP 1.0 client signals that the message was acknowledged by the AMQP 1.0 endpoint, the following information 
is mapped to the automatically created [acknowledgement](protocol-specification-acks.html#acknowledgement):

* Acknowledgement.status: 
   * will be `200`, if the message was successfully consumed by the AMQP 1.0 endpoint
   * will be `5xx`, if the AMQP 1.0 endpoint failed in consuming the message, retrying sending the message is feasible
* Acknowledgement.value: 
   * will be missing, for status `200`
   * will contain more information, in case that an error `status` was set


### Specific configuration properties

The specific configuration properties are interpreted as 
[JMS Configuration options](https://qpid.apache.org/releases/qpid-jms-0.40.0/docs/index.html#jms-configuration-options). 
Use these to customize and tweak your connection as needed.


### HMAC request signing

Ditto supports HMAC request signing for AMQP 1.0 connections. Find detailed information on this in
[Connectivity API > HMAC request signing](connectivity-hmac-signing.html).


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
