---
title: MQTT 3.1.1 protocol binding
keywords: binding, protocol, mqtt
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-mqtt.html
---

When MQTT messages are sent in [Ditto Protocol](protocol-overview.html),
the payload should be `UTF-8` encoded strings.

If messages which are not in Ditto Protocol should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## MQTT 3.1.1 properties

MQTT 3.1.1 messages have no application headers. Transmission-relevant properties are set in the
`"headers"` field as a part of [Ditto protocol messages](protocol-specification.html#dittoProtocolEnvelope) in the
payload. 

These properties are supported:

* `correlation-id`: For correlating request messages and events. Twin events have the correlation IDs of
  [Twin commands](protocol-twinlive.html#twin) that produced them.
* `reply-to`: The value should be an MQTT topic.
  If a command sets the header `reply-to`, then its response is published at the topic equal to the header value.

## Specific connection configuration

### Source format

For an MQTT connection:

* Source `"addresses"` are MQTT topics to subscribe to. Wildcards `+` and `#` are allowed.
* `"authorizationContext"` may _not_ contain placeholders `{%raw%}{{ header:<header-name> }}{%endraw%}` as MQTT 3.1.1
  has no application headers.
* The additional field `"filters"` defines filters of MQTT messages by checking their topics against their payload.
  If at least one filter is defined, then messages are dropped if their topics do not match any of the filters.
  Filters can be specified using placeholders `{%raw%}{{ thing:id }}{%endraw%}`,
  `{%raw%}{{ thing:namespace }}{%endraw%}` or `{%raw%}{{ thing:name }}{%endraw%}`.
* The additional field `"qos"` sets the maximum Quality of Service to request when subscribing for messages. Its value
  can be `0` for at-most-once delivery, `1` for at-least-once delivery and `2` for exactly-once delivery.
  Support of any Quality of Service depends on the external MQTT broker.
  The default value is `0` (at-most-once).


```json
{
  "addresses": [
    "<mqtt_topic>",
    "..."
  ],
  "authorizationContext": ["ditto:inbound-auth-subject", "..."],
  "filters": [
    "{%raw%}telemetry/{{ thing:id }}{%endraw%}",
    "{%raw%}device/{{ thing:namespace }}/{{ thing:name }}{%endraw%}",
    "..."
  ],
  "qos": 0
}
```

### Target format

For an MQTT connection, the target address is the MQTT topic to publish events and messages to.
The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html).

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
have READ permission on the Thing that is associated with a message.

The additional field `"qos"` sets the Quality of Service with which messages are published.
Its value can be `0` for at-most-once delivery, `1` for at-least-once delivery and `2` for exactly-once delivery.
Support of any Quality of Service depends on the external MQTT broker.
The default value is `0` (at-most-once).


```json
{
  "address": "mqtt/topic/of/my/device/{%raw%}{{ thing:id }}{%endraw%}",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject", "..."],
  "qos": 0
}
```


## Establishing a connection to an MQTT 3.1.1 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example 

Connection configuration to create a new MQTT connection:

```json
{
  "id": "mqtt-example-connection-123",
  "connectionType": "mqtt",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "tcp://test.mosquitto.org:1883",
  "sources": [
    {
      "addresses": [
        "eclipse-ditto-sandbox/#"
      ],
      "authorizationContext": ["ditto:inbound-auth-subject"],
      "qos": 0,
      "filters": []
    }
  ],
  "targets": [
    {
      "address": "eclipse-ditto-sandbox/{%raw%}{{ thing:id }}{%endraw%}",
      "topics": [
        "_/_/things/twin/events"
      ],
      "authorizationContext": ["ditto:outbound-auth-subject"],
      "qos": 0
    }
  ]
}
```

## Messages

Messages consumed via the MQTT binding are treated similar to the
[WebSocket binding](httpapi-protocol-bindings-websocket.html), 
meaning that the messages are expected to be [Ditto Protocol](protocol-overview.html) messages serialized as
UTF-8-coded JSON (as shown for example in the [protocol examples](protocol-examples.html)).
If your payload does not conform to the [Ditto Protocol](protocol-overview.html) or uses any character set other
than UTF-8, you can configure a custom [payload mapping](connectivity-mapping.html).
