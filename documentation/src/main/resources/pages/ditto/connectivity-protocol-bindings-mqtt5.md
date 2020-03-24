---
title: MQTT 5 protocol binding
keywords: binding, protocol, mqtt, mqtt5
tags: [protocol, connectivity]
permalink: connectivity-protocol-bindings-mqtt5.html
---

Consume messages from MQTT 5 brokers via [sources](#source-format) and send messages to MQTT 5 brokers via 
[targets](#target-format).

## Content-type

When MQTT messages are sent in [Ditto Protocol](protocol-overview.html),
the payload should be `UTF-8` encoded strings.

If messages which are not in Ditto Protocol should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## MQTT 5 properties

Supported MQTT 5 properties which are interpreted in a specific way are:

* `9 (0x09) Correlation Data`: For correlating request messages and events. Twin events have the correlation IDs of
  [Twin commands](protocol-twinlive.html#twin) that produced them. Stored in the ditto protocol header `correlation-id`.
* `8 (0x08) Response Topic`: The MQTT topic a requests response is expected in.
  If a command sets the header `reply-to`, then its response is published at the topic equal to the header value.
* `3 (0x03) Content Type`: The UTF-8 encoded string representation of the payloads content MIME type.

## Specific connection configuration

The common configuration for connections in [Connections > Sources](basic-connections.html#sources) and 
[Connections > Targets](basic-connections.html#targets) applies here as well. 
Following are some specifics for MQTT connections:

### Source format

For an MQTT connection:

* Source `"addresses"` are MQTT topics to subscribe to. Wildcards `+` and `#` are allowed.
* `"authorizationContext"` array that contains the authorization subjects in whose context
inbound messages are processed. These subjects may contain placeholders, see 
[placeholders](basic-connections.html#placeholder-for-source-authorization-subjects) section for more information.
* The optional field `"qos"` sets the maximum Quality of Service to request when subscribing for messages. Its value
  can be `0` for at-most-once delivery, `1` for at-least-once delivery and `2` for exactly-once delivery.
  The default value is `2` (exactly-once).
  Support of any Quality of Service depends on the external MQTT broker

```json
{
  "addresses": [
    "<mqtt_topic>",
    "..."
  ],
  "authorizationContext": ["ditto:inbound-auth-subject", "..."],
  "qos": 2
}
```

#### Source header mapping

MQTT 5 supports so-called user defined properties, which are defined for every message type. The [header mapping](connectivity-header-mapping.html) applies to the supported MQTT 5 specific headers as well as to the user defined properties.

### Target format

For an MQTT connection, the target address is the MQTT topic to publish events and messages to.
The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html).

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the Thing, that is associated with a message.

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

#### Target header mapping

MQTT 5 supports so-called user defined properties, which are defined for every message type. The [header mapping](connectivity-header-mapping.html) applies to the supported MQTT 5 specific headers as well as to the user defined properties.

In order to provide user defined properties for the targets of the MQTT 5 connector, use the [specific configs](basic-connections.html#specific-config) field of the connector configuration. The entries are interpreted as key-value-pairs and directly set as user properties.

## Establishing a connection to an MQTT 5 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example 

Connection configuration to create a new MQTT connection:

```json
{
  "id": "mqtt-example-connection-12",
  "connectionType": "mqtt-5",
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

## Client-certificate authentication

Ditto supports certificate-based authentication for MQTT connections. Consult 
[Certificates for Transport Layer Security](connectivity-tls-certificates.html)
for how to set it up.

Here is an example MQTT connection that checks the broker certificate and authenticates by a client certificate.

```json
{
  "id": "mqtt-example-connection-124",
  "connectionType": "mqtt-5",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "ssl://test.mosquitto.org:8884",
  "validateCertificates": true,
  "ca": "-----BEGIN CERTIFICATE-----\n<test.mosquitto.org certificate>\n-----END CERTIFICATE-----",
  "credentials": {
    "type": "client-cert",
    "cert": "-----BEGIN CERTIFICATE-----\n<signed client certificate>\n-----END CERTIFICATE-----",
    "key": "-----BEGIN PRIVATE KEY-----\n<client private key>\n-----END PRIVATE KEY-----"
  },
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
