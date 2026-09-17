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
one `filter` parameter holding an [RQL expression](basic-rql.html) and any number of `fn-filter` parameters,
each holding a placeholder pipeline expression (see
[filtering with placeholder functions](#filtering-with-placeholder-functions) below). The parameter **name**
tells the two apart -- `filter` is always RQL, `fn-filter` is always a placeholder pipeline. **All** given
filters must match for a signal to be published (**AND** semantics). Topic filters given in this string form are
URL-decoded when parsed and are **not** re-encoded when the connection is stored, so `%xx` sequences are
decoded once per parse (e.g. `%7C` becomes a literal `|` in a compared value) and a `+` is decoded to a space
-- avoid `+` and a literal `%` in compared values and RQL `like` patterns altogether, as neither can be
carried through the stored form (`%2B` is decoded to `+` on the first parse and to a space when the stored
topic is parsed again; a bare `%` in a stored value, e.g. from `%25`, fails to decode when the stored topic
is parsed again). This applies to RQL `like` patterns and pipeline compared values alike:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/twin/events?extraFields=attributes/placement&filter=gt(attributes/placement,'Kitchen')",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto",
    "_/_/things/live/commands?fn-filter=header:ditto-originator|fn:filter('ne','some:excluded-subject')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

If a target's `topics` array lists several topic entries, they are evaluated independently and
combined with **OR** semantics -- a signal is published (once) as soon as it matches *any one* listed
topic (each with its own namespace/RQL/pipeline filters).

### Filtering with placeholder functions

In addition to (or instead of) the RQL `filter` parameter, a target topic may carry `fn-filter` parameters,
each holding a placeholder pipeline from the [function library](basic-placeholders.html#function-library)
which ends with [`fn:filter()`](basic-placeholders.html#function-library). Such a pipeline is evaluated per
outbound signal against that signal's headers, topic, entity, and time -- see
[connection target topic filter placeholders](basic-placeholders.html#scope-connection-target-topic-filter)
for the full list -- instead of against thing/event *data*. Because of that, an `fn-filter` also works for
topics for which an RQL filter cannot meaningfully match, such as `_/_/things/live/commands` (marked &#10060; for
"RQL `filter`" in the table above).

The two parameters are told apart by their **name**, never by their content: `filter` always holds an RQL
expression and `fn-filter` always holds a placeholder pipeline. A pipeline placed in `filter` is rejected at
connection creation/update time with an error pointing to `fn-filter`; an RQL expression placed in `fn-filter`
is rejected as an invalid pipeline.

#### The shape of an fn-filter

An `fn-filter` always has the same shape -- the **placeholder** whose value is filtered, optional
value-transforming `fn:` stages, and exactly one **`fn:filter`** as the last stage:

```text
<placeholder>[|fn:<value stage>...]|fn:filter('<rqlFunction>',<comparedValue>)
```

```text
fn-filter=header:ditto-originator|fn:filter('ne','some:subject')
fn-filter=header:ditto-originator|fn:filter('like','integration:*')
fn-filter=header:ditto-originator|fn:lower()|fn:filter('eq','some:subject')
fn-filter=header:ditto-originator|fn:filter('exists','true')
```

* `<rqlFunction>` is one of the [RQL functions](basic-placeholders.html#rql-functions) `eq`, `ne`, `like` and
  `exists`, given as a quoted constant.
* `<comparedValue>` is a quoted constant or another placeholder (e.g. `fn:filter('eq',header:expected)`); for
  `exists` it is `'true'`.
* `fn:filter` always filters the value of the pipeline in front of it -- the value to filter is never passed to
  `fn:filter` as a parameter.

The publish decision of an `fn-filter` is binary:
* the pipeline **resolves** to a value -- the target topic is **published**
* the pipeline stays **unresolved** -- the target topic is **suppressed**

Think of `fn:filter` as the stage that returns the boolean publish decision: it keeps the pipeline *resolved*
(publish) when its condition holds and leaves it *unresolved* (suppress) otherwise.

{% include important.html content="An `fn-filter` must **start with the placeholder** to filter. The
function-first form known from other placeholder scopes, `fn:filter(header:ditto-originator,'ne','some:subject')`,
is rejected at connection creation/update time: with the placeholder *inside* the function, a header which is
absent for a signal is filtered as the empty value, so `ne` would be satisfied and the topic would publish
every signal lacking the header. With the placeholder in front, an absent header always suppresses the topic
(see [absent header behavior](#absent-header-behavior))." additionalStyle="" %}

The primary use case is suppressing events caused by a given subject, or caused by another connection:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?fn-filter=header:ditto-originator|fn:filter('ne','some:excluded-subject')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

* `header:ditto-originator` resolves to the first authorization subject of the request that caused
  the signal.
* `header:ditto-origin` resolves to the ID of the connection that originally caused the signal, e.g.
  `fn-filter=header:ditto-origin|fn:filter('ne','some-other-connection-id')`.
  Ditto already suppresses signals a connection caused itself by default; filtering on
  `ditto-origin` is only needed to additionally exclude signals caused by *other* connections.

#### Combining conditions with AND

An `fn-filter` tests one value with one `fn:filter`. To require several conditions, **repeat the `fn-filter`
parameter** -- every expression must match for the topic to be published:

```text
fn-filter=header:ditto-originator|fn:filter('ne','some:subject')&fn-filter=header:ditto-origin|fn:filter('ne','some-connection-id')
```

An RQL `filter` can be combined with `fn-filter` parameters on one topic, again with **AND** semantics -- the
RQL expression and every pipeline must match:

```text
filter=gt(attributes/counter,42)&fn-filter=header:ditto-originator|fn:filter('ne','some:subject')
```

`filter` may be given at most **once** per topic -- combine several RQL conditions into a single expression
with `and(...)`.

#### Combining conditions with OR

To publish when **one or another header is present**, let `fn:default(<placeholder>)` fall back to the second
header when the first is absent:

```text
fn-filter=header:first|fn:default(header:second)|fn:filter('exists','true')
```

The same fallback works with any `rqlFunction`, but keep in mind that it is a *coalesce*: the `fn:filter` tests
the **first present** value only -- `header:second` is not looked at when `header:first` is present.

For an OR of arbitrary conditions, list the topic **several times** in the target's `topics` array, each entry
with its own filters. A signal is published -- **once** -- as soon as it matches any one entry:

```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?fn-filter=header:ditto-originator|fn:filter('eq','some:subject')",
    "_/_/things/twin/events?fn-filter=header:ditto-origin|fn:filter('like','integration-*')"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

If such entries define different [`extraFields`](#target-topics-and-enrichment) and several of them match, the
signal is enriched with the `extraFields` of one matching entry only -- use the same `extraFields` on all
entries of an OR.

#### Absent header behavior

Since the pipeline evaluates per signal, the filtered header may be absent for a given signal (for
example, `ditto-originator` is absent for signals with no authenticated causing subject, such as
those from Ditto's own internal processing paths). An absent header always **suppresses** the topic, regardless
of the `rqlFunction`: the leading placeholder does not resolve, so the `fn:filter` never matches -- also for
`ne`. The same holds for a placeholder used as `<comparedValue>` which does not resolve.

To publish on an absent header, opt in explicitly by supplying a value for that case with an `fn:default(...)`
stage before the `fn:filter`:

```text
fn-filter=header:ditto-originator|fn:default('none')|fn:filter('ne','some:subject')
fn-filter=header:ditto-originator|fn:default('none')|fn:filter('eq','none')
```

The first publishes signals which carry another originator **or none at all**, the second only signals
without an originator (pick a default value which cannot occur as a real value).

#### Restrictions

All of the following is checked at connection creation/update time.

* `filter` only accepts an RQL expression -- a placeholder pipeline is rejected with an error pointing to
  `fn-filter`.
* `fn-filter` must start with a placeholder (`header:...`, `topic:...`, `thing-json:...`, ...) and every
  further stage must be an `fn:` function call. An expression starting with `fn:`, an RQL expression, or a
  leading placeholder without a name (e.g. `header:`) is rejected.
* An `fn-filter` contains exactly one `fn:filter(...)` stage, as its last stage: a bare placeholder
  (`fn-filter=header:ditto-originator`; write `header:ditto-originator|fn:filter('exists','true')` instead), a
  pipeline ending with a value-producing stage (e.g. `fn:upper()`) or with an `fn:default(...)` (which would
  discard the filter's decision), and a second `fn:filter` stage ([repeat the `fn-filter`
  parameter](#combining-conditions-with-and) instead) are rejected. So is an `fn:delete()` stage at any
  position, because the topic would never publish.
* The `fn:filter` stage must have the form `fn:filter('<rqlFunction>',<comparedValue>)`: the 3-parameter form
  and `fn:filter(<value>,'exists')`, which pass the value to filter as a parameter, are rejected, as is a
  placeholder used as `rqlFunction`. The `rqlFunction` name must be one of the case-sensitive
  [`eq`, `ne`, `like`, `exists` RQL functions](basic-placeholders.html#rql-functions) (so `'NE'` or `'neq'` are
  rejected). The constant compared value `'exists'` cannot be used (`fn:filter` would take it for its
  `fn:filter(<value>,'exists')` form), and neither can `fn:filter('exists','false')`, which would never match
  because the filtered value always exists -- see [absent header behavior](#absent-header-behavior) for how to
  publish on an absent value.
* An `fn-filter` may contain at most **10** `fn:` stages; the number of `fn-filter` parameters is not limited.
* `filter`, `namespaces` and `extraFields` may be given at most **once** per topic; repeating one of them is
  rejected as an invalid topic. Only `fn-filter` is repeatable.
* Pipeline placeholders never see fields added via [`extraFields`
  enrichment](#target-topics-and-enrichment) -- they only ever see the signal's own headers, topic,
  entity, and time. Unlike RQL, an `fn-filter` cannot filter on enriched, unchanged data. The
  [`thing-json` placeholder](basic-placeholders.html#thing-json-placeholder) sees only the data carried by
  the event itself (for live commands and messages it never resolves) and -- because a function parameter's
  placeholder prefix must not contain a dash -- can only be used as the leading placeholder
  (`thing-json:attributes/x|fn:filter('eq','y')`), not as `<comparedValue>` or inside `fn:default(...)`.
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
expression in `filter` instead is no fallback either -- older instances reject a pipeline there as an
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
