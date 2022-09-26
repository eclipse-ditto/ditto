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

If messages, which are not in Ditto Protocol, should be processed, a [payload mapping](connectivity-mapping.html) must
be configured for the connection in order to transform the messages.

## MQTT 5 properties

Supported MQTT 5 properties, which are interpreted in a specific way are:

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
* The required field `"qos"` sets the maximum Quality of Service to request when subscribing for messages. Its value
  can be `0` for at-most-once delivery, `1` for at-least-once delivery and `2` for exactly-once delivery.
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

MQTT 5 supports so-called user defined properties, which are defined for every message type.
In addition, Ditto extracts the following headers from each consumed message:

* `mqtt.topic`: contains the MQTT topic on which a message was received 
* `mqtt.qos`: contains the MQTT QoS value of a received message
* `mqtt.retain`: contains the MQTT retain flag of a received message
* `correlation-id`: contains the MQTT 5 "correlation data" value
* `reply-to`: contains the MQTT 5 "response topic" value
* `content-type`: contains the MQTT 5 "content type" value

The [header mapping](connectivity-header-mapping.html) applies to the supported MQTT 5 specific headers as well
as to the user defined properties, e.g.:
```json
{
  "headerMapping": {
    "topic": "{%raw%}{{ header:mqtt.topic }}{%endraw%}",
    "the-qos": "{%raw%}{{ header:mqtt.qos }}{%endraw%}",
    "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
    "device-id": "{%raw%}{{ header:device-id-user-defined-property }}{%endraw%}"
  }
}
```

#### Source acknowledgement handling

For MQTT 5 sources, when configuring 
[acknowledgement requests](basic-connections.html#source-acknowledgement-requests), consumed messages from the MQTT 5
broker are treated in the following way:

For Ditto acknowledgements with successful [status](protocol-specification-acks.html#combined-status-code):
* Acknowledges the received MQTT 5 message

For Ditto acknowledgements with mixed successful/failed [status](protocol-specification-acks.html#combined-status-code):
* If some of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery (e.g. based on a timeout):
   * based on the [specificConfig](#specific-configuration) [reconnectForDelivery](#reconnectforredelivery) either 
      * closes and reconnects the MQTT connection in order to immediately receive unACKed QoS 1/2 messages again 
      * or simply doesn't acknowledge the received MQTT 5 message resulting in a redelivery of a QoS > 0 message by the MQTT broker
* If none of the aggregated [acknowledgements](basic-acknowledgements.html#acknowledgements-acks) require redelivery:
   * acknowledges the received MQTT 5 message as redelivery does not make sense

In order to enable acknowledgement processing only for MQTT messages received with QoS 1/2, the following configuration
has to be applied:
```json
{
  "addresses": [
    "<mqtt_topic>",
    "..."
  ],
  "authorizationContext": [
    "ditto:inbound-auth-subject",
    "..."
  ],
  "qos": 1,
  "acknowledgementRequests": {
    "includes": [],
    "filter": "fn:filter(header:mqtt.qos,'ne','0')"
  }
}
```

### Target format

For an MQTT connection, the target address is the MQTT topic to publish events and messages to.
The target address may contain placeholders; see
[placeholders](basic-connections.html#placeholder-for-target-addresses) section for more information.

Further, `"topics"` is a list of strings, each list entry representing a subscription of
[Ditto protocol topics](protocol-specification-topic.html).

Outbound messages are published to the configured target address if one of the subjects in `"authorizationContext"`
has READ permission on the thing, which is associated with a message.

The additional field `"qos"` sets the Quality of Service with which messages are published.
Its value can be `0` for at-most-once delivery, `1` for at-least-once delivery and `2` for exactly-once delivery.
Support of any Quality of Service depends on the external MQTT broker.

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

MQTT 5 supports so-called user defined properties, which are defined for every message type.
The [header mapping](connectivity-header-mapping.html) applies to the supported MQTT 5 specific headers as well as to 
the user defined properties.

The following headers have a special meaning in that the values are applied directly to the published message:
* `mqtt.topic`: overwrites the topic configured for the target 
* `mqtt.qos`: overwrites the qos level configured in the target 
* `mqtt.retain`: controls whether the MQTT retain flag is set on the published message  

#### Target acknowledgement handling

For MQTT 5 targets, when configuring 
[automatically issued acknowledgement labels](basic-connections.html#target-issued-acknowledgement-label), requested 
acknowledgements are produced in the following way:

Once the MQTT 5 client signals that the message was acknowledged by the MQTT 5 broker, the following information 
is mapped to the automatically created [acknowledgement](protocol-specification-acks.html#acknowledgement):
* Acknowledgement.status: 
   * will be `200`, if the message was successfully ACKed by the MQTT 5 broker
   * will be `503`, if the MQTT 5 broker ran into an error before an acknowledgement message was received
* Acknowledgement.value: 
   * will be missing, for status `200`
   * will contain more information, in case that an error `status` was set

### Specific Configuration

The MQTT 5 binding offers additional [specific configurations](basic-connections.html#specific-configuration) 
to apply for the used MQTT client.

Overall example JSON of the MQTT `"specificConfig"`:
```json
{
  "id": "mqtt-example-connection-123",
  "connectionType": "mqtt-5",
  "connectionStatus": "open",
  "failoverEnabled": true,
  "uri": "tcp://test.mosquitto.org:1883",
  "specificConfig": {
    "clientId": "my-awesome-mqtt-client-id",
    "reconnectForRedelivery": false,
    "cleanSession": false,
    "separatePublisherClient": false,
    "publisherId": "my-awesome-mqtt-publisher-client-id",
    "reconnectForRedeliveryDelay": "5s",
    "lastWillTopic": "my/last/will/topic",
    "lastWillQos": 1,
    "lastWillRetain": false,
    "lastWillMessage": "my last will message"
  },
  "sources": ["..."],
  "targets": ["..."]
}
```

#### clientId

Overwrites the default MQTT client id.

Default: not set - the ID of the Ditto [connection](basic-connections.html) is used as MQTT client ID. 

If the connection's `clientCount` is 2 or more,
the ID of each connectivity service instance is appended to the client ID to prevent clients from having the same
ID. Otherwise the broker will disconnect the already-connected client every time another client with the same
ID connects.

#### reconnectForRedelivery

Configures that the MQTT connection re-connects whenever a consumed message (via a connection source) with QoS 1 
("at least once") 2 ("exactly once") is processed but cannot be [acknowledged](#source-acknowledgement-handling) successfully.<br/>
That causes that the MQTT broker will re-publish the message once the connection reconnected.   
If configured to `false`, the MQTT message is simply acknowledged (`PUBACK` or `PUBREC`, `PUBREL`).

Default: `false`

Handle with care: 
* when set to `true`, incoming QoS 0 messages are lost during the reconnection phase
* when set to `true` and there is also an MQTT target configured to publish messages,
  the messages to be published during the reconnection phase are lost
   * to fix that, configure `"separatePublisherClient"` also to `true` in order to publish via another MQTT connection
* when set to `false`, MQTT messages with QoS 1 and 2 are redelivered based on the MQTT broker's strategy,
  but may not be redelivered at all as the MQTT specification does not require unacknowledged messages to be redelivered
  without reconnection of the client

#### cleanSession

Configure the MQTT client's `cleanStart` flag. (The flag is called `cleanStart` but the option is `cleanSession` to
be consistent with MQTT 3 specific config.)

Default: `false`

#### separatePublisherClient

Configures whether to create a separate physical client and connection to the MQTT broker for publishing messages, or not.
If configured to `true`, a single Ditto connection would open 2 MQTT connections/sessions: one for subscribing 
and one for publishing.  
If configured to `false`, the same MQTT connection/session is used both: for subscribing to messages, and for publishing 
messages.

Default: `false`

#### publisherId

Configures a specific MQTT client ID for the case that `"separatePublisherClient"` is enabled.

Default: 
* if client ID is configured, `clientId` + `"p"`
* if no client ID is configured, `connectionId` + `"p"`

#### reconnectForRedeliveryDelay

Configures how long to wait before reconnecting a consumer client for redelivery when `"reconnectForRedelivery"`
and `separatePublisherClient` are both enabled. The minimum value is `1s`.

Default: `2s`

#### keepAlive

Configures the keep alive time interval (in seconds) in which the client sends a ping to the broker
if no other MQTT packets are sent during this period of time. It is used to determine if the connection is still up.

Default: `60s` [see here](https://hivemq.github.io/hivemq-mqtt-client/docs/mqtt-operations/connect/#keep-alive)

#### lastWillTopic

Configures the topic which should be used on Last Will. This field is mandatory when Last Will should be enabled.

#### lastWillQos

Configures the QoS which should be used on Last Will:
- `0` = QoS 0 (“at most once”)
- `1` = QoS 1 (“at least once”)
- `2` = QoS 2 (“exactly once”)

Default: `0`

#### lastWillRetain

Configures if clients which are newly subscribed to the topic chosen in [Last Will topic](#lastwilltopic) will
receive this message immediately after they subscribe.

Default: `false`

#### lastWillMessage

Configures the message which should be published when the connection is disconnected ungracefully from the broker.
The message will be published as UTF8-encoded text on the topic chosen in [Last Will topic](#lastwilltopic).

Default: empty string

### Configure Last Will message

To notify other clients when the connection is disconnected ungracefully the [Last Will feature](https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901033) 
can be used. The message which will be published, is specified in the connection and stored in the broker when it 
connects. The message contains a topic, retained message flag, QoS, and the text payload to be published. These can be 
configured in the [Specific Configuration](#specific-configuration) of the connection. 

{% include note.html content="This feature is enabled if the _last will topic_ is set." %}

## Establishing a connection to an MQTT 5 endpoint

Ditto's [Connectivity service](architecture-services-connectivity.html) is responsible for creating new and managing
existing connections.

This can be done dynamically at runtime without the need to restart any microservice using a
[Ditto DevOps command](installation-operating.html#devops-commands).

Example: 

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

Here is an example MQTT connection, which checks the broker certificate and authenticates by a client certificate.

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
