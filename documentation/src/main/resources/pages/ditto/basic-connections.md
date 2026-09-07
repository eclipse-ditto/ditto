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
[namespaces](basic-changenotifications.html#filter-by-namespace),
[RQL expressions](basic-changenotifications.html#filter-by-rql-expression) and
[placeholder pipelines](#filtering-with-placeholder-functions):

| Topic | Namespace filter | RQL `filter` | `fn-filter` |
|-------|:---:|:---:|:---:|
| `_/_/things/twin/events` | &#10004; | &#10004; | &#10004; |
| `_/_/things/live/messages` | &#10004; | &#10004; | &#10004; |
| `_/_/things/live/commands` | &#10004; | &#10060; | &#10004; |
| `_/_/things/live/events` | &#10004; | &#10004; | &#10004; |
| `_/_/policies/announcements` | &#10004; | &#10060; | &#10060; |
| `_/_/connections/announcements` | &#10060; | &#10060; | &#10060; |

Filter parameters use HTTP query parameter syntax (`?` for the first, `&` for subsequent). A topic may carry
one `filter` parameter holding an [RQL expression](basic-rql.html) and one `fn-filter` parameter holding a
placeholder pipeline expression (see
[filtering with placeholder functions](#filtering-with-placeholder-functions) below). The parameter **name**
tells the two apart -- `filter` is always RQL, `fn-filter` is always a placeholder pipeline. If both are given,
both must match for a signal to be published (**AND** semantics). URL-encode filter values before using them:
topic filters given in this string form are URL-decoded when parsed, so a literal `+` (decoded to a space) or
`%xx` sequence in a compared value must itself be URL-encoded -- this applies to RQL `like` patterns and
pipeline compared values alike:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/twin/events?extraFields=attributes/placement&filter=gt(attributes/placement,'Kitchen')",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto",
    "_/_/things/live/commands?fn-filter=fn:filter(header:ditto-originator,'ne','some:excluded-subject')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

If a target's `topics` array lists several topic entries, they are evaluated independently and
combined with **OR** semantics -- a signal is published as soon as it matches *any one* listed
topic (each with its own namespace/RQL/pipeline filter).

### Filtering with placeholder functions

In addition to (or instead of) the RQL `filter` parameter, a target topic may carry an `fn-filter` parameter
holding a placeholder pipeline from the [function library](basic-placeholders.html#function-library) whose
last stage is [`fn:filter()`](basic-placeholders.html#function-library). Such a pipeline is evaluated per
outbound signal against that signal's headers, topic, entity, and time -- see
[connection target topic filter placeholders](basic-placeholders.html#scope-connection-target-topic-filter)
for the full list -- instead of against thing/event *data*. Because of that, an `fn-filter` also works for
topics for which an RQL filter cannot meaningfully match, such as `_/_/things/live/commands` (marked &#10060; for
"RQL `filter`" in the table above).

The two parameters are told apart by their **name**, never by their content: `filter` always holds an RQL
expression and `fn-filter` always holds a placeholder pipeline. A pipeline placed in `filter` (a value
starting with `fn:`) is rejected at connection creation/update time with an error pointing to `fn-filter`; an
RQL expression placed in `fn-filter` is rejected as an invalid pipeline.

The publish decision of an `fn-filter` is binary:
* the pipeline **resolves** to a value -- the target topic is **published**
* the pipeline stays **unresolved** (or its value is deleted) -- the target topic is **suppressed**

{% include important.html content="The **last stage** of an `fn-filter` must be a filtering stage, i.e.
`fn:filter(...)`. Think of it as the stage that returns the boolean publish decision: `fn:filter` keeps the
pipeline *resolved* (publish) when its condition holds and leaves it *unresolved* (suppress) otherwise.
A trailing value-producing stage cannot add anything to that decision and is therefore pointless --
`fn:upper()`, `fn:lower()`, `fn:trim()` and the like pass the outcome of the preceding `fn:filter` through
unchanged, a trailing `fn:default(...)` even overrides it and makes the topic **always** publish (it resolves
every unresolved pipeline), and a trailing `fn:delete()` makes the topic **never** publish. A pipeline without
any `fn:filter` stage (e.g. a bare `header:ditto-originator`) merely publishes whenever the placeholder
resolves." additionalStyle="" %}

The primary use case is suppressing events caused by a given subject, or caused by another connection. Each is
a standalone `fn-filter` (do **not** combine them as two separate `topics` entries -- that would be an OR,
publishing whenever *either* condition holds; see below on how to combine conditions with AND):

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?fn-filter=fn:filter(header:ditto-originator,'ne','some:excluded-subject')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

* `header:ditto-originator` resolves to the first authorization subject of the request that caused
  the signal.
* `header:ditto-origin` resolves to the ID of the connection that originally caused the signal, e.g.
  `fn-filter=fn:filter(header:ditto-origin,'ne','some-other-connection-id')`.
  Ditto already suppresses signals a connection caused itself by default; filtering on
  `ditto-origin` is only needed to additionally exclude signals caused by *other* connections.

#### Function-first and placeholder-first pipelines

An `fn-filter` may start directly with a function -- the placeholder is then a function parameter -- or with a
placeholder that feeds the pipeline:

```text
fn-filter=fn:filter(header:ditto-originator,'ne','some:subject')
fn-filter=header:ditto-originator|fn:filter('ne','some:subject')
```

Both publish a signal whose `ditto-originator` header differs from `some:subject`. They differ when the header
is **absent** (see below): the placeholder-first form never resolves without the header and therefore
suppresses the signal, whereas the function-first form applies `ne` to the missing value and publishes.

Several `fn:` stages can be chained with `|`. Each stage only runs if the previous one resolved (matched), so
chaining is **AND**: every stage must match for the pipeline to resolve, for example:

```text
fn-filter=fn:filter(header:ditto-originator,'ne','some:subject')|fn:filter(header:ditto-origin,'ne','some-connection-id')
```

An RQL `filter` and an `fn-filter` can be combined on one topic, again with **AND** semantics -- the RQL
expression and the pipeline must both match:

```text
filter=gt(attributes/counter,42)&fn-filter=fn:filter(header:ditto-originator,'ne','some:subject')
```

Each of the two parameters may be given at most **once** per topic -- combine several RQL conditions into a
single expression with `and(...)`, and several pipeline conditions by chaining `fn:` stages with `|`.

#### Absent header behavior

Since the pipeline evaluates per signal, a referenced header may be absent for a given signal (for
example, `ditto-originator` is absent for signals with no authenticated causing subject, such as
those from Ditto's own internal processing paths). For the **function-first** form
`fn:filter(header:x,rqlFunction,...)` the outcome depends on the `rqlFunction`:

| `rqlFunction` | Outcome when the header is absent |
|---------------|------------------------------------|
| `eq`          | dropped (suppressed) |
| `ne`          | **published** |
| `like`        | dropped, unless the pattern itself matches the empty string (e.g. `'*'`) |
| `exists` (2-param form, e.g. `fn:filter(header:x,'exists')`) | dropped |

For the **placeholder-first** form `header:x|fn:filter(...)` an absent header always **drops** the signal,
regardless of the `rqlFunction`: the leading placeholder does not resolve, so a following `fn:filter` never
matches (only an intervening `fn:default('...')` could supply a value).

{% include important.html content="`ne` on an absent header resolves to **published**, not
suppressed -- this is the opposite of what `eq` does and easy to get wrong. For example,
`fn:filter(header:ditto-originator,'ne','some:subject')` also publishes any signal that never
carries a `ditto-originator` header at all -- because 'absent' trivially satisfies 'not equal to
some:subject'. If only signals that actually carry the header should be affected, use the placeholder-first
form shown above or add an `exists` stage." additionalStyle="" %}

#### Restrictions

* `filter` only accepts an RQL expression -- a value starting with `fn:` is rejected at connection
  creation/update time with an error pointing to `fn-filter`.
* `fn-filter` must be a placeholder pipeline: it starts with a placeholder (`header:...`, `topic:...`, ...)
  or with an `fn:` function call, and every further stage must be an `fn:` function call -- a bare placeholder
  cannot appear mid-pipeline. An RQL expression in `fn-filter`, or a leading placeholder without a name
  (e.g. `header:`), is rejected at connection creation/update time.
* The last stage of an `fn-filter` must be `fn:filter(...)` (see above); this is not enforced, a pipeline that
  ends with a value-producing stage is accepted but pointless.
* An `fn-filter` may contain at most **10** `fn:` stages; exceeding the limit is rejected at
  connection creation/update time.
* Each of `filter` and `fn-filter` may be given at most **once** per topic; a repeated query parameter makes
  the topic string unparseable.
* An unrecognized `rqlFunction` name (i.e. anything other than the
  [`eq`, `ne`, `like`, `exists` RQL functions](basic-placeholders.html#rql-functions)) is
  **not** rejected at connection creation/update time -- that filter simply never matches at
  runtime. Double-check spelling.
* Pipeline placeholders never see fields added via [`extraFields`
  enrichment](#target-topics-and-enrichment) -- they only ever see the signal's own headers, topic,
  entity, and time. Unlike RQL, an `fn-filter` cannot filter on enriched, unchanged data. The
  [`thing-json` placeholder](basic-placeholders.html#thing-json-placeholder) sees only the data carried by
  the event itself (for live commands and messages it never resolves) and -- because a function parameter's
  placeholder prefix must not contain a dash -- can only be used as the leading stage of a placeholder-first
  pipeline (`thing-json:attributes/x` followed by `fn:filter('eq','y')`), not inside `fn:filter(...)`.
* An `fn-filter` should only reference headers that are stable for the signal's lifetime (such as
  `ditto-originator` or `ditto-origin`): internal bookkeeping headers such as `requested-acks` are
  mutated while the signal is processed and are not reliable filter inputs.
* As with RQL, neither `filter` nor `fn-filter` can be set on `_/_/policies/announcements` or
  `_/_/connections/announcements` (see the table above); both are silently ignored if present.
* A filter-suppressed signal produces no user-visible log entry, the same as with a pure RQL filter.
  Only when evaluating an `fn-filter` *fails* (rather than simply not matching) is a failure entry recorded
  in the [connection logs](connectivity-manage-connections.html#connection-logs).

{% include warning.html content="Only start using `fn-filter` once **all** instances of your connectivity
service run a Ditto version that supports it. Older instances do not know the parameter and silently
**ignore** it: such an instance publishes the topic without applying the pipeline filter at all. (Placing the
expression in `filter` instead is no fallback either -- older instances reject an `fn:` value there as an
invalid RQL expression.)" %}

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
