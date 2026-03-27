---
title: Connections Overview
keywords: connection, connectivity, mapping, integration, placeholder, qos, at least once, delivery, guarantee
tags: [connectivity]
permalink: basic-connections.html
---

Connections let you integrate Ditto with external messaging systems so that devices can exchange data with their digital twins through protocols like AMQP, MQTT, HTTP, and Kafka.

{% include callout.html content="**TL;DR**: A connection is a managed communication channel between Ditto and an external system. You configure sources to consume inbound messages and targets to publish outbound messages, with authorization, enforcement, and payload mapping applied automatically." type="primary" %}

## Overview

You integrate your Ditto instance with external messaging services -- such as
[Eclipse Hono](https://eclipse.org/hono/), [RabbitMQ](https://www.rabbitmq.com/),
[Apache Kafka](https://kafka.apache.org/), or any HTTP endpoint -- by creating connections.

A connection represents a communication channel that uses a transport protocol to transmit
[Ditto Protocol](protocol-overview.html) messages. Ditto supports one-way and two-way communication,
enabling consumer/producer scenarios as well as full command-and-response workflows.

All connections are configured and supervised by Ditto's
[Connectivity service](architecture-services-connectivity.html).

To create and manage connections, use the [HTTP API](connectivity-manage-connections.html) or
[DevOps piggyback commands](connectivity-manage-connections-piggyback.html).

## Connection model

The following schema defines the connection model:

{% include docson.html schema="jsonschema/connection.json" %}

## Supported connection types

Ditto supports these connection types:

* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
* [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* [HTTP 1.1](connectivity-protocol-bindings-http.html)
* [Kafka 2.x](connectivity-protocol-bindings-kafka2.html)

The format of `sources` and `targets` addresses depends on the `connectionType` and is documented
in each protocol binding page.

## Sources

Sources consume messages **from** external systems. Inbound messages can be:

* [Commands](basic-signals-command.html)
* [Messages](basic-messages.html)
* [Live commands/responses/events](protocol-twinlive.html)
* [Acknowledgements](protocol-specification-acks.html)

A source contains:

* **addresses** -- interpreted as queues, topics, etc. depending on the [connection type](#supported-connection-types)
* **consumerCount** -- how many consumers attach to each address
* **authorizationContext** -- [authorization subjects](basic-policy.html#subjects) used to authorize inbound messages (see [Authorization](#authorization))
* **enforcement** -- filters to verify that a device only modifies its own digital twin
* **acknowledgementRequests** -- controls [QoS 1 processing](#source-acknowledgement-requests)
* **declaredAcks** -- labels of [acknowledgements](protocol-specification-acks.html) this source may send
* **headerMapping** -- maps external headers to internal headers (see [Header mapping](connectivity-header-mapping.html))
* **replyTarget** -- where to publish responses to incoming commands

### Source enforcement

By default, Ditto does not verify whether the device identity in an inbound message matches the
targeted thing. You can add enforcement to ensure a device only modifies its own digital twin.

Enforcement requires that the external system provides a verified device identity (for example, in a
message header).

The enforcement configuration has two fields:

* `input` -- where the device identity is extracted from
* `filters` -- patterns matched against the input; at least one must match or the message is rejected

**Placeholders for `input`:**

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ header:<name> }}{%endraw%}` | Any header from the received message (case-insensitive) | `{%raw%}{{header:device_id }}{%endraw%}`  |
| `{%raw%}{{ source:address }}{%endraw%}` | The address the message was received on | devices/sensors/temperature1  |

**Placeholders for `filters`:**

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | Full ID (namespace + name)  | eclipse.ditto:thing-42  |
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (first part of ID) | eclipse.ditto |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (second part of ID) | thing-42  |

**Example:** Device `sensor:temperature1` provides its identity in a `device_id` header. To enforce
that it can only write to its own twin:

```json
{
  "addresses": ["telemetry/hono_tenant"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "enforcement": {
    "input": "{%raw%}{{ header:device_id }}{%endraw%}",
    "filters": ["{%raw%}{{ thing:id }}{%endraw%}"]
  }
}
```

### Source acknowledgement requests

To process inbound messages with "at least once" (QoS 1) semantics instead of the default "at most
once" (QoS 0), configure `acknowledgementRequests/includes` to request the
["twin-persisted"](basic-acknowledgements.html#built-in-acknowledgement-labels) acknowledgement.
The message is then technically acknowledged only after the twin is successfully persisted.

The optional `filter` field uses an [fn:filter()](basic-placeholders.html#function-library) expression
to control when acknowledgements are requested:

```json
{
  "addresses": ["<source>"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "qos": "{%raw%}{{ header:qos }}{%endraw%}"
  },
  "acknowledgementRequests": {
    "includes": ["twin-persisted"],
    "filter": "fn:filter(header:qos,'ne','0')"
  }
}
```

### Source declared acknowledgement labels

Acknowledgements sent via a source must have their labels declared in the `declaredAcks` array.
Labels must be prefixed by the connection ID (or `{%raw%}{{connection:id}}{%endraw%}`) followed by a colon:

```json
{
  "addresses": ["<source>"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "declaredAcks": [
    "{%raw%}{{connection:id}}{%endraw%}:my-custom-ack"
  ]
}
```

### Source header mapping

You can apply an optional [header mapping](connectivity-header-mapping.html) to inbound messages.
Mapped headers are added to the Ditto protocol message produced by payload mapping:

```json
{
  "addresses": ["<source>"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "correlation-id": "{%raw%}{{ header:message-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}"
  }
}
```

### Source reply target

A source may define a reply target to publish responses to incoming commands. The reply target
inherits its payload mapping from the parent source.

```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  },
  "replyTarget": {
    "enabled": true,
    "address": "{%raw%}{{ header:reply-to }}{%endraw%}",
    "headerMapping": {
      "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}"
    },
    "expectedResponseTypes": ["response", "error", "nack"]
  }
}
```

The `expectedResponseTypes` control which responses are published:

* **response** -- successful responses and positive acknowledgements
* **error** -- error responses
* **nack** -- negative acknowledgements

### Response diversion

Sources can redirect responses to different connections instead of the configured reply target using
special header mapping keys. See [Response diversion](connectivity-response-diversion.html) for details.

```json
{
  "headerMapping": {
    "divert-response-to-connection": "target-connection-id",
    "divert-expected-response-types": "response,error,nack"
  }
}
```

## Targets

Targets publish messages **to** external systems. Outbound messages can be:

* [Thing events](basic-signals-event.html)
* [Thing messages](basic-messages.html)
* [Live commands/responses/events](protocol-twinlive.html)
* [Policy announcements](protocol-specification-policies-announcement.html)
* [Connection announcements](protocol-specification-connections-announcement.html)

A target contains:

* **address** -- interpreted as a queue, topic, etc. depending on the [connection type](#supported-connection-types)
* **topics** -- which [message types](#target-topics-and-filtering) to publish
* **authorizationContext** -- [authorization subjects](basic-policy.html#subjects) that must have READ permission
* **headerMapping** -- maps Ditto protocol headers to external headers

### Target topics and filtering

You define which message types to publish via the `topics` array. You can filter by
[namespaces](basic-changenotifications.html#by-namespaces) and
[RQL expressions](basic-changenotifications.html#by-rql-expression):

| Topic | Namespace filter | RQL filter |
|-------|:---:|:---:|
| `_/_/things/twin/events` | &#10004; | &#10004; |
| `_/_/things/live/messages` | &#10004; | &#10004; |
| `_/_/things/live/commands` | &#10004; | &#10060; |
| `_/_/things/live/events` | &#10004; | &#10004; |
| `_/_/policies/announcements` | &#10004; | &#10060; |
| `_/_/connections/announcements` | &#10060; | &#10060; |

Filter parameters use HTTP query parameter syntax (`?` for the first, `&` for subsequent). URL-encode
filter values before using them:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

### Target topics and enrichment

You can add extra fields to outgoing messages with the `extraFields` parameter.
See [signal enrichment](basic-enrichment.html) for details:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?extraFields=attributes/placement"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

### Target issued acknowledgement label

A target can automatically [issue an acknowledgement](basic-acknowledgements.html#issuing-acknowledgements)
once the channel confirms successful delivery. The label must be prefixed by the connection ID
or `{%raw%}{{connection:id}}{%endraw%}`:

```json
{
  "address": "<target>",
  "topics": ["_/_/things/twin/events"],
  "authorizationContext": ["ditto:outbound-auth-subject"],
  "issuedAcknowledgementLabel": "{%raw%}{{connection:id}}{%endraw%}:my-custom-ack"
}
```

### Target header mapping

You can apply an optional [header mapping](connectivity-header-mapping.html) to outgoing messages:

```json
{
  "address": "<target>",
  "topics": ["_/_/things/twin/events"],
  "authorizationContext": ["ditto:outbound-auth-subject"],
  "headerMapping": {
    "message-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
    "subject": "{%raw%}{{ topic:subject }}{%endraw%}"
  }
}
```

## Authorization

Ditto initiates connections as a client, so no client authorization is needed from the external
system. However, to access Ditto resources, each connection must specify an `authorizationContext`
with self-assigned authorization subjects. These subjects must be granted access through
[Policies](basic-policy.html).

* A **target** can only send data for things to which it has **READ** rights
* A **source** can only receive data for things to which it has **WRITE** rights

## Placeholders

Connection configurations support placeholders with the syntax
`{% raw %}{{ placeholder }}{% endraw %}`. See the [placeholders concept](basic-placeholders.html)
for full details.

### Placeholder for source authorization subjects

You can use header placeholders in source authorization subjects to apply per-device permissions:

{%
  include important.html content="Only use this kind of placeholder if you trust the source of the message. The value from the header is used as the **authorized subject**." additionalStyle=""
%}

```json
{
  "id": "auth-subject-placeholder-example",
  "sources": [{
    "addresses": ["telemetry"],
    "authorizationContext": ["device:{% raw %}{{ header:device_id }}{% endraw %}"]
  }]
}
```

### Placeholder for target addresses

You can use thing placeholders in target addresses to route messages by namespace or device:

| Placeholder | Description | Resolved value |
|--------|------------|------------|
| `thing:id`  | Full ID (namespace:name) | `org.eclipse.ditto:device-123` |
| `thing:namespace`  | Namespace  | `org.eclipse.ditto` |
| `thing:name` | Name | `device-123` |

All [connection placeholders](basic-placeholders.html#scope-connections) are also available.
If any placeholder fails to resolve, the message is dropped.

```json
{
  "id": "target-placeholder-example",
  "targets": [{
    "addresses": ["live/{% raw %}{{ thing:namespace }}{% endraw %}"],
    "authorizationContext": ["ditto:auth-subject"],
    "topics": ["_/_/things/live/events", "_/_/things/live/commands"]
  }]
}
```

## Specific configuration

Some connection types require protocol-specific settings in the `specificConfig` field. See each
protocol binding page for details.

## Payload mapping

You can transform message payloads between external formats and Ditto Protocol using
[payload mapping](connectivity-mapping.html).

## SSH tunneling

Ditto supports tunneling connections through SSH. See [SSH tunneling](connectivity-ssh-tunneling.html)
for setup instructions.

## Further reading

* [Managing connections](connectivity-manage-connections.html) -- create, modify, and monitor connections
* [Payload mapping](connectivity-mapping.html) -- transform message payloads
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [TLS certificates](connectivity-tls-certificates.html) -- secure connections with TLS
* [Acknowledgements](basic-acknowledgements.html) -- configure delivery guarantees
