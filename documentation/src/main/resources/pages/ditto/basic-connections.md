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

{% include note.html content="If you use basic auth from the HTTP API, prefix authorization subjects with `nginx:` (e.g., `nginx:ditto`). See [Basic Authentication](basic-auth.html#authorization-context-in-devops-commands)." %}

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
    "includes": ["twin-persisted", "{%raw%}{{connection:id}}{%endraw%}:my-custom-ack"],
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

A source may define a reply target to publish responses to incoming commands. The reply target's
address and header mapping are defined within the reply target, while its payload mapping is
inherited from the parent source.

To publish responses at the address from the incoming command's `reply-to` header, configure source
header mapping and reply target together. If an incoming command lacks the `reply-to` header, no
response is published:

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

**Static response diversion** -- redirect all responses to a fixed connection:

```json
{
  "addresses": ["commands/sensor"],
  "authorizationContext": ["ditto:sensor-commands"],
  "headerMapping": {
    "divert-response-to-connection": "analytics-connection",
    "divert-expected-response-types": "response,error"
  },
  "replyTarget": {
    "enabled": true,
    "address": "responses/sensor"
  }
}
```

Where:
- `divert-response-to-connection`: Target connection ID for diversion
- `divert-expected-response-types`: Comma-separated list of response types to divert

**Dynamic response diversion** -- route based on message content using a JavaScript payload mapper:

```json
{
  "addresses": ["commands/+"],
  "authorizationContext": ["ditto:device-commands"],
  "headerMapping": {
    "divert-expected-response-types": "response,error"
  },
  "payloadMapping": ["response-router"]
}
```

```javascript
function mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType) {
  var parsedPayload = JSON.parse(textPayload);
  var dittoHeaders = {
    "correlation-id": headers["correlation-id"],
    "divert-response-to": determineTargetConnection(headers, parsedPayload),
    "divert-expected-response-types": "response,error"
  };
  return Ditto.buildDittoProtocolMsg(
    namespace, name, group, channel, criterion, action, path,
    dittoHeaders, value
  );
}

function determineTargetConnection(headers, payload) {
  if (payload.priority === "high") {
    return "priority-processing-connection";
  } else if (payload.deviceType === "sensor") {
    return "sensor-analytics-connection";
  } else {
    return "default-processing-connection";
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
[namespaces](basic-changenotifications.html#filter-by-namespace) and
[RQL expressions](basic-changenotifications.html#filter-by-rql-expression):

| Topic | Namespace filter | RQL filter |
|-------|:---:|:---:|
| `_/_/things/twin/events` | &#10004; | &#10004; |
| `_/_/things/live/messages` | &#10004; | &#10004; |
| `_/_/things/live/commands` | &#10004; | &#10060; |
| `_/_/things/live/events` | &#10004; | &#10004; |
| `_/_/policies/announcements` | &#10004; | &#10060; |
| `_/_/connections/announcements` | &#10060; | &#10060; |

Filter parameters use HTTP query parameter syntax (`?` for the first, `&` for subsequent). The
`filter` parameter may be given **twice** on one topic -- at most one RQL expression and at most one
placeholder pipeline expression (`fn:...`); all given filters must match for a signal to be
published (**AND** semantics, see
[filtering with placeholder functions](#filtering-with-placeholder-functions) below). URL-encode
filter values before using them: topic filters given in this string form are URL-decoded when parsed,
so a literal `+` (decoded to a space) or `%xx` sequence in a compared value must itself be
URL-encoded -- this applies to RQL `like` patterns and pipeline compared values alike:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/twin/events?extraFields=attributes/placement&filter=gt(attributes/placement,'Kitchen')",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

If a target's `topics` array lists several topic entries, they are evaluated independently and
combined with **OR** semantics -- a signal is published as soon as it matches *any one* listed
topic (each with its own namespace/RQL/pipeline filter).

### Filtering with placeholder functions

In addition to (or instead of) an RQL expression, a target topic may carry `filter` parameters
holding a placeholder function invocation from the
[function library](basic-placeholders.html#function-library), most commonly
[`fn:filter()`](basic-placeholders.html#function-library). Such a pipeline expression is
evaluated per outbound signal against that signal's headers, topic, entity, and time -- see
[connection target topic filter placeholders](basic-placeholders.html#scope-connection-target-topic-filter)
for the full list -- instead of against thing/event *data*. Because of that, a pipeline filter also works for topics for which an
RQL filter cannot meaningfully match, such as `_/_/things/live/commands` (marked &#10060; for
"RQL filter" in the table above).

A pipeline expression:
* **resolves** (produces a value) -- the target topic is **published**
* stays **unresolved** (the filter drops the value) -- the target topic is **suppressed**

The primary use case is suppressing events caused by a given subject, or caused by another
connection. Each is a standalone filter (do **not** combine them as two separate `topics` entries --
that would be an OR, publishing whenever *either* condition holds; see below on how to combine
conditions with AND):

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?filter=fn:filter(header:ditto-originator,'ne','some:excluded-subject')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

* `header:ditto-originator` resolves to the first authorization subject of the request that caused
  the signal.
* `header:ditto-origin` resolves to the ID of the connection that originally caused the signal, e.g.
  `filter=fn:filter(header:ditto-origin,'ne','some-other-connection-id')`.
  Ditto already suppresses signals a connection caused itself by default; filtering on
  `ditto-origin` is only needed to additionally exclude signals caused by *other* connections.

A `filter` query parameter whose (trimmed) value starts with `fn:` is a pipeline filter; anything
else is treated as RQL. Several `fn:` stages can be chained with `|` inside the pipeline filter.
Each stage only runs if the previous one resolved (matched), so chaining is **AND**: every stage
must match for the pipeline to resolve, for example:

```text
filter=fn:filter(header:ditto-originator,'ne','some:subject')|fn:filter(header:ditto-origin,'ne','some-connection-id')
```

An RQL filter and a pipeline filter can be combined as two `filter` parameters, again with **AND**
semantics -- the RQL expression and the pipeline must both match:

```text
filter=gt(attributes/counter,42)&filter=fn:filter(header:ditto-originator,'ne','some:subject')
```

At most **one** of a topic's `filter` parameters may be an RQL expression -- combine several RQL
conditions into a single expression with `and(...)` instead. Likewise at most **one** may be a
pipeline expression -- combine several pipeline conditions by chaining `fn:` stages with `|` inside
it.

#### Absent header behavior

Since the pipeline evaluates per signal, a referenced header may be absent for a given signal (for
example, `ditto-originator` is absent for signals with no authenticated causing subject, such as
those from Ditto's own internal processing paths). The outcome then depends on the `rqlFunction`:

| `rqlFunction` | Outcome when the header is absent |
|---------------|------------------------------------|
| `eq`          | dropped (suppressed) |
| `ne`          | **published** |
| `like`        | dropped, unless the pattern itself matches the empty string (e.g. `'*'`) |
| `exists` (2-param form, e.g. `fn:filter(header:x,'exists')`) | dropped |

{% include important.html content="`ne` on an absent header resolves to **published**, not
suppressed -- this is the opposite of what `eq` does and easy to get wrong. For example,
`fn:filter(header:ditto-originator,'ne','some:subject')` also publishes any signal that never
carries a `ditto-originator` header at all -- because 'absent' trivially satisfies 'not equal to
some:subject'. If only signals that actually carry the header should be affected, combine the
pipeline with an additional `exists` stage." additionalStyle="" %}

#### Restrictions

* A pipeline filter parameter must always start with `fn:` -- a bare leading placeholder
  (e.g. `filter=header:ditto-originator`) is **not** a valid pipeline filter; place the placeholder
  as a function parameter instead, as shown above.
* As with any placeholder pipeline, every stage after the first must itself be an `fn:` function
  call -- a bare placeholder cannot appear mid-pipeline.
* A pipeline filter may contain at most **10** `fn:` stages; exceeding the limit is rejected at
  connection creation/update time.
* A topic accepts at most **one** RQL `filter` parameter and at most **one** pipeline `filter`
  parameter; a second one of either kind is rejected at connection creation/update time. Combine
  RQL conditions with `and(...)` and pipeline conditions by chaining stages with `|`.
* An unrecognized `rqlFunction` name (i.e. anything other than `eq`, `ne`, `like`, `exists`) is
  **not** rejected at connection creation/update time -- that filter simply never matches at
  runtime. Double-check spelling.
* Pipeline placeholders never see fields added via [`extraFields`
  enrichment](#target-topics-and-enrichment) -- they only ever see the signal's own headers, topic,
  entity, and time. Unlike RQL, a pipeline filter cannot filter on enriched, unchanged data.
* Pipeline filters should only reference headers that are stable for the signal's lifetime (such as
  `ditto-originator` or `ditto-origin`): internal bookkeeping headers such as `requested-acks` are
  mutated while the signal is processed and are not reliable filter inputs.
* As with RQL, no `filter` at all -- pipeline, RQL, or combined -- can be set on
  `_/_/policies/announcements` or `_/_/connections/announcements` (see the table above); it is
  silently ignored if present.
* A filter-suppressed signal produces no user-visible log entry, the same as with a pure RQL filter
  today. Only when evaluating a pipeline filter *fails* (rather than simply not matching) is a
  failure entry recorded in the [connection logs](connectivity-manage-connections.html#connection-logs).

{% include warning.html content="Only start using `fn:...` filters -- and in particular **repeated**
`filter` parameters -- once **all** instances of your connectivity service run a Ditto version that
supports them. Older instances cannot even *parse* a topic string carrying more than one `filter`
parameter: a connection persisted with the new syntax breaks connection loading on such instances,
it is not merely rejected." %}

### Target topics and enrichment

You can add extra fields to outgoing messages with the `extraFields` parameter.
See [signal enrichment](basic-enrichment.html) for details.

Not all topics support enrichment:

| Topic | Extra fields |
|-------|:---:|
| `_/_/things/twin/events` | &#10004; |
| `_/_/things/live/messages` | &#10004; |
| `_/_/things/live/commands` | &#10004; |
| `_/_/things/live/events` | &#10004; |
| `_/_/policies/announcements` | &#10060; |
| `_/_/connections/announcements` | &#10060; |

Example:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?extraFields=attributes/placement",
    "_/_/things/live/messages?extraFields=features/ConnectionStatus"
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
    "subject": "{%raw%}{{ topic:subject }}{%endraw%}",
    "reply-to": "all-replies"
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
