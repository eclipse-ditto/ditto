---
title: Connections
keywords: connection, connectivity, mapping, connection, integration, placeholder, qos, at least once, delivery, guarantee
tags: [connectivity]
permalink: basic-connections.html
---

## Connection model

  {%
    include note.html content="To get started with connections right away, consult the
    [Manage connections](connectivity-manage-connections.html) page. "
  %}

You can integrate your Ditto instance with external messaging services such as 
[Eclipse Hono](https://eclipse.org/hono/), a [RabbitMQ](https://www.rabbitmq.com/) broker or an 
[Apache Kafka](https://kafka.apache.org/) broker via custom "connections".

Additionally, you may invoke foreign HTTP endpoints by using the 
[HTTP connection type](connectivity-protocol-bindings-http.html).

A connection represents a communication channel for the exchange of messages between any service and Ditto. 
It requires a transport protocol, which is used to transmit [Ditto Protocol](protocol-overview.html) messages. 
Ditto supports one-way and two-way communication over connections. This enables consumer/producer scenarios 
as well as fully-fledged command and response use cases. Nevertheless, those options might be limited by 
the transport protocol or the other endpoint's capabilities.
 
All connections are configured and supervised via Ditto's 
[Connectivity service](architecture-services-connectivity.html). The following model defines the connection itself:

{% include docson.html schema="jsonschema/connection.json" %}


### Connection types

The top design priority of this model is to be as generic as possible, while still allowing protocol specific 
customizations and tweaks. This enables the implementations of different customizable connection types, and support 
for custom payload formats. Currently, the following connection types are supported:

* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
* [MQTT 5](connectivity-protocol-bindings-mqtt5.html)
* [HTTP 1.1](connectivity-protocol-bindings-http.html)
* [Kafka 2.x](connectivity-protocol-bindings-kafka2.html)

The `sources` and `targets` address formats depends on the `connectionType` and has therefore `connectionType` 
specific limitations. Those are documented with the corresponding protocol bindings.

### Sources

Sources are used to connect to message brokers / external systems in order to consume messages **from them**.

Source messages can be of the following type:
* [commands](basic-signals-command.html)
* [messages](basic-messages.html)
* [live commands/responses/events](protocol-twinlive.html)
* [acknowledgements](protocol-specification-acks.html)

Sources contain:
* several addresses (depending on the [connection type](#connection-types) those are interpreted differently, 
  e.g. as queues, topics, etc.),
* a consumer count defining how many consumers should be attached to each source address,
* an authorization context (see [authorization](#authorization)) specifying which 
  [authorization subject](basic-policy.html#subjects) is used to authorize messages from the source,
* enforcement information that allows filtering the messages that are consumed in this source,
* [acknowledgement requests](basic-acknowledgements.html#requesting-acks) this source requires in order 
  to ensure QoS 1 ("at least once") processing of consumed messages before technically acknowledging them to the channel,
* declared labels of [acknowledgements](protocol-specification-acks.html) the source is allowed to send,
* [header mapping](connectivity-header-mapping.html) for mapping headers of source messages to internal headers, and
* a reply-target to configure publication of any responses of incoming commands.

#### Source enforcement

Messages received from external systems are mapped to Ditto internal format, either by applying some custom mapping or 
the default mapping for [Ditto Protocol](protocol-overview.html) messages. 

During this mapping the digital twin of the device is determined i.e. 
which thing is accessed or modified as a result of the message. By default, no sanity check is done if this target 
thing corresponds to the device that originally sent the message. In some use cases this might be valid, but 
in other scenarios you might want to enforce that a device only sends data to its digital twin. 
Note that this could also be achieved by assigning a specific policy to each device and use [placeholders](#placeholders) 
in the authorization subject, but this can get cumbersome to maintain for a large number of devices.

With an enforcement, you can use a single policy for all devices 
and still make sure that a device only modifies its associated digital twin. Enforcement is only feasible if the message
contains the verified identity of the sending device (e.g. in a message header). This verification has to be done by the
external system e.g. by properly authenticating the devices and providing the identity in the messages sent to Ditto.

The enforcement configuration consists of two fields:
* `input`: Defines where device identity is extracted.
* `filters`: Defines the filters that are matched against the input. At least one filter must match the input value, 
otherwise the message is rejected.

The following placeholders are available for the `input` field:

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ header:<name> }}{%endraw%}` | Any header from the message received via the source (case-insensitive). | `{%raw%}{{header:device_id }}{%endraw%}`  |
| `{%raw%}{{ source:address }}{%endraw%}` | The address on which the message was received. | devices/sensors/temperature1  |


The following placeholders are available for the `filters` field:

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | Full ID composed of ''namespace'' + '':'' as a separator + ''name''  | eclipse.ditto:thing-42  |
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) | eclipse.ditto |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (i.e. second part of an ID ) | thing-42  |

Assuming a device `sensor:temperature1` pushes its telemetry data to Ditto which is stored in a thing named
`sensor:temperature1`. The device identity is provided in a header field `device_id`. To enforce that the device can 
only send data to the Thing `sensor:temperature1` the following enforcement configuration can be used: 
```json
{
  "addresses": [ "telemetry/hono_tenant" ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "enforcement": {
    "input": "{%raw%}{{ header:device_id }}{%endraw%}",
    "filters": [ "{%raw%}{{ thing:id }}{%endraw%}" ]
  }
}
```

Note: This example assumes that there is a valid user named `ditto:inbound-auth-subject` in Ditto.
If you want to use a user for the basic auth (from the [HTTP API](connectivity-protocol-bindings-http.html)) use 
the prefix `nginx:`, e.g. `nginx:ditto`.
See [Basic Authentication](basic-auth.html#authorization-context-in-devops-commands) for more information.

#### Source acknowledgement requests

A source can configure, that for each incoming message additional 
[acknowledgement requests](basic-acknowledgements.html#requesting-acks) are added. 

That is desirable whenever incoming messages should be processed with a higher "quality of service" than the default, 
which is "at most once" (or QoS 0).

In order to process messages from sources with an "at least once" (or QoS 1) semantic, configure the source's 
`"acknowledgementRequests/includes"` to add the 
["twin-persisted"](basic-acknowledgements.html#built-in-acknowledgement-labels) acknowledgement request, which will 
cause that a consumed message over this source will technically be acknowledged, it the twin was 
successfully updated/persisted by Ditto.

How the technical acknowledgment is done is specific for the used [connection type](#connection-types) and documented 
in scope of that connection type.

In addition to the `"includes"` defining which acknowledgements to request for each incoming message, the optional 
`"filter"` holds an [fn:filter()](basic-placeholders.html#function-library) function defining when to request 
acknowledgements at all for an incoming message. This filter is applied on both acknowledgements: those 
[requested in the message](basic-acknowledgements.html#requesting-acks-via-ditto-protocol-message) and the ones requested 
via the configured `"includes"` array.

The JSON for a source with acknowledgement requests could look like this. The `"filter"` in the example causes that 
acknowledgements are only requested if the "qos" header was either not present or does not equal `0`:
```json
{
  "addresses": [
    "<source>"
  ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "qos": "{%raw%}{{ header:qos }}{%endraw%}"
  },
  "acknowledgementRequests": {
    "includes": [
      "twin-persisted",
      "receiver-connection-id:my-custom-ack"
    ],
    "filter": "fn:filter(header:qos,'ne','0')"
  }
}
```

#### Source declared acknowledgement labels

The acknowledgements sent via a source must have their labels declared in the field `declardAcks` as a JSON array.<br/>
If the label of an acknowledgement is not in the `declaredAcks` array, then the acknowledgement is rejected with
an error. The declared labels must be prefixed by the connection ID followed by a colon or the 
`{%raw%}{{connection:id}}{%endraw%}` placeholder followed by a colon. For example:
```json
{%raw%}
{
  "addresses": [
    "<source>"
  ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "declaredAcks": [
    "{{connection:id}}:my-custom-ack"
  ]
}
{%endraw%}
```

#### Source header mapping

For incoming messages, an optional [header mapping](connectivity-header-mapping.html) may be applied.
Mapped headers are added to the headers of the Ditto protocol message obtained by payload mapping.
The default [Ditto payload mapper](connectivity-mapping.html#ditto-mapper) does not retain any external header;
in this case all Ditto protocol headers come from the header mapping.

The JSON for a source with header mapping could look like this:
```json
{
  "addresses": [
    "<source>"
  ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "correlation-id": "{%raw%}{{ header:message-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}"
  }
}
```

#### Source reply target

A source may define a reply target to publish the responses of incoming commands.
For a reply target, the address and header mapping are defined in itself, whereas its payload mapping is inherited
from the parent source, because a payload mapping definition specifies the transformation for both: incoming and outgoing
messages.

For example, to publish responses at the target address equal to the `reply-to` header of incoming commands,
define source header mapping and reply target as follows. If an incoming command does not have the `reply-to` header,
then its response is dropped.
```json
{
  "headerMapping": {
    "reply-to": "{%raw%}{{ header:reply-to }}{%endraw%}"
  },
  "replyTarget": {
    "enabled": true,
    "address": "{%raw%}{{ header:reply-to }}{%endraw%}"
  }
}
```

The reply target may contain its own header mapping (`"headerMapping"`) in order to map response headers.

In addition, the reply target contains the expected response types (`"expectedResponseTypes"`) which should be 
published to the reply target.<br/>
The following reply targets are available to choose from:
* **response**: Send back successful responses (e.g. responses after a Thing was successfully modified, 
  but also responses for [query commands](basic-signals-command.html#query-commands)). 
  Includes positive [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating).  
* **error**: Send back error responses (e.g. thing not modifiable due to lacking permissions)
* **nack**: If negative [acknowledgement](protocol-specification-acks.html#acknowledgements-aggregating) responses should be delivered.

This is an example `"replyTarget"` containing both header mapping and expected response types:
```json
{
  "replyTarget": {
    "enabled": true,
    "address": "{%raw%}{{ header:reply-to }}{%endraw%}",
    "headerMapping": {
      "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}"
    },
    "expectedResponseTypes": [
      "response",
      "error",
      "nack"
    ]
  }
}
```


### Targets

Targets are used to connect to messages brokers / external systems in order to publish messages **to them**.

Target messages can be of the following type:
* [Thing messages](basic-messages.html)
* [Thing events](basic-signals-event.html)
* [Thing live commands/responses/events](protocol-twinlive.html)
* [Policy announcements](protocol-specification-policies-announcement.html)
* [Connection announcements](protocol-specification-connections-announcement.html)

Targets contain:
* one address (that is interpreted differently depending on the [connection type](#connection-types), e.g. as queue, topic, etc.),
* [topics](#target-topics-and-filtering) that will be sent to the target,
* an authorization context (see [authorization](#authorization)) specifying which 
  [authorization subject](basic-policy.html#subjects) is used to authorize messages to the target, and
* [header mapping](connectivity-header-mapping.html) to compute external headers from Ditto protocol headers.


#### Target topics and filtering

Which types of messages should be published to the target address, can be defined via configuration.

In order to only consume specific events like described in [change notifications](basic-changenotifications.html), the
following parameters can additionally be provided when specifying the `topics` of a target:

| Description | Topic | [Filter by namespaces](basic-changenotifications.html#by-namespaces) | [Filter by RQL expression](basic-changenotifications.html#by-rql-expression) |
|-------------|-----------------|------------------|-----------|
| Subscribe for [Thing events/change notifications](basic-changenotifications.html) | `_/_/things/twin/events` | &#10004; | &#10004; |
| Subscribe for [Thing messages](basic-messages.html) | `_/_/things/live/messages` | &#10004; | &#10004; |
| Subscribe for [Thing live commands](protocol-twinlive.html) | `_/_/things/live/commands` | &#10004; | &#10060; |
| Subscribe for [Thing live events](protocol-twinlive.html) | `_/_/things/live/events` | &#10004; | &#10004; |
| Subscribe for [Policy announcements](protocol-specification-policies-announcement.html) | `_/_/policies/announcements` | &#10004; | &#10060; |
| Subscribe for [Connection announcements](protocol-specification-connections-announcement.html) | `_/_/connections/announcements` | &#10060; | &#10060; |

The parameters are specified similar to HTTP query parameters, the first one separated with a `?` and all following ones
with `&`. You need to URL-encode the filter values before using them in a configuration.

For example, this way the connection session would register for all events in the namespace `org.eclipse.ditto` and which
would match an attribute "counter" to be greater than 42. Additionally, it would subscribe to messages in the namespace
`org.eclipse.ditto`:
```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/twin/events?extraFields=attributes/placement&filter=gt(attributes/placement,'Kitchen')",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject", "..."]
}
```

#### Target topics and enrichment

When extra fields should be added to outgoing messages on a connection, an `extraFields` parameter can be added
to the topic. This is supported for all topics:

| Description | Topic | [Enrich by extra fields](basic-enrichment.html) |
|-------------|-----------------|------------------|
| Subscribe for [Thing events/change notifications](basic-changenotifications.html) | `_/_/things/twin/events` | &#10004; |
| Subscribe for [Thing messages](basic-messages.html) | `_/_/things/live/messages` | &#10004; |
| Subscribe for [Thing live commands](protocol-twinlive.html) | `_/_/things/live/commands` | &#10004; |
| Subscribe for [Thing live events](protocol-twinlive.html) | `_/_/things/live/events` | &#10004; |
| Subscribe for [Policy announcements](protocol-specification-policies-announcement.html) | `_/_/policies/announcements` | &#10060; |
| Subscribe for [Connection announcements](protocol-specification-connections-announcement.html) | `_/_/connections/announcements` | &#10060; |

Example:
```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?extraFields=attributes/placement",
    "_/_/things/live/messages?extraFields=features/ConnectionStatus"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject", "..."]
}
```

#### Target issued acknowledgement label

A target can be configured to automatically [issue acknowledgements](basic-acknowledgements.html#issuing-acknowledgements) 
for each published/emitted message, once the underlying channel confirmed 
that the message was successfully received. 

That is desirable whenever outgoing messages (e.g. [events](basic-signals-event.html)) are handled in scope of a command 
sent with an "at least once" (QoS 1) semantic in order to only acknowledge that command, if the event was successfully
forwarded into another system.

For more details on that topic, please refer to the [acknowledgements](basic-acknowledgements.html) section.

Whether an outgoing message is treated as successfully sent or not is specific for the used 
[connection type](#connection-types) and documented in scope of that connection type.

The issued acknowledgement label must be prefixed by the connection ID followed by a colon or the 
`{%raw%}{{connection:id}}{%endraw%}` placeholder followed by a colon.<br/>
The JSON for a target with issued acknowledgement labels could look like this:
```json
{%raw%}
{
  "address": "<target>",
  "topics": [
    "_/_/things/twin/events"
  ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "issuedAcknowledgementLabel": "{{connection:id}}:my-custom-ack"
}
{%endraw%}
```

#### Target header mapping

For outgoing messages, an optional [header mapping](connectivity-header-mapping.html) may be applied.
Mapped headers are added to the external headers.
The default [Ditto payload mapper](connectivity-mapping.html#ditto-mapper) does not define any external header;
in this case, all external headers come from the header mapping.

The JSON for a target with header mapping could like this:
```json
{
  "address": "<target>",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto"
  ],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {
    "message-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
    "content-type": "{%raw%}{{ header:content-type }}{%endraw%}",
    "subject": "{%raw%}{{ topic:subject }}{%endraw%}",
    "reply-to": "all-replies"
  }
}
```


### Authorization

A connection is initiated by the connectivity service. This obviates the need for client authorization, because
Ditto becomes the client in this case. Nevertheless, to access resources within Ditto, the connection must know on 
whose behalf it is acting. This is controlled via the configured `authorizationContext`, which holds a list of
self-assigned authorization subjects. Before a connection can access a Ditto resource, one of its 
`authorizationSubject`s must be granted the access rights by the authorization mechanism of a 
[Policies](basic-policy.html).

A connection target can only send data for things to which it has READ rights, as data flows from a thing to a target. 
A connection source can only receive data for things to which it has WRITE rights, as data flows from a source to a thing.

### Specific configuration

Some [connection types](#connection-types) require specific configuration, which is not supported for other connection types.
Those are put into the `specificConfig` field.

### Payload Mapping

For more information on mapping message payloads see the corresponding [Payload Mapping Documentation](connectivity-mapping.html).

## Placeholders

The configuration of a connection allows to use placeholders at certain places. This allows more fine-grained control 
over how messages are consumed or where they are published to. The general syntax of a placeholder is 
`{% raw %}{{ placeholder }}{% endraw %}`. Have a look at the [placeholders concept](basic-placeholders.html) for 
more details on that. 

### Placeholder for source authorization subjects

Processing the messages received via a source using the _same fixed authorization subject_ may not be 
suitable for every scenario. For example, if you want to declare fine-grained write permissions per device, this would 
not be possible with a fixed global subject. For this use case, we have introduced placeholder substitution for 
authorization subjects of source addresses that are resolved when processing messages from a source.
Of course, this requires the sender of the 
message to provide necessary information about the original issuer of the message. 

  {%
    include important.html content="Only use this kind of placeholder if you trust the source of the message. The value from the header is used as the **authorized subject**." additionalStyle=""
  %}
                                                                           
You can access any header value of the incoming message by using a placeholder like `{% raw %}{{ header:name }}{% endraw %}`.

Example:

Assuming the messages received from the source _telemetry_ contain a `device_id` header (e.g. _sensor-123_), 
you may configure your source's authorization subject as follows:
```json
   {
      "id": "auth-subject-placeholder-example",
      "sources": [
        {
          "addresses": [ "telemetry" ],
          "authorizationContext": ["device:{% raw %}{{ header:device_id }}{% endraw %}"]
        }
      ]
  }
```
The placeholder is then replaced by the value from the message headers and the message is forwarded and processed under 
the subject _device:sensor-123_.
In case the header cannot be resolved or the header contains unexpected characters, an exception is thrown, which is sent 
back to the sender as an error message, if a valid _reply-to_ header was provided, otherwise the message is dropped.

### Placeholder for target addresses

Another use case for placeholders may be to publish twin events or live commands and events to a target address 
containing thing-specific information e.g. you can distribute things from different namespaces to different target addresses.
You can use the placeholders `{% raw %}{{ thing:id }}{% endraw %}`, `{% raw %}{{ thing:namespace }}{% endraw %}` 
and `{% raw %}{{ thing:name }}{% endraw %}` in the target address for this purpose.
For a thing with the ID _org.eclipse.ditto:device-123_ these placeholders would be resolved as follows:

| Placeholder | Description | Resolved value |
|--------|------------|------------|
| `thing:id`  | Full ID composed of _namespace_  `:` (as a separator), and _name_ | _org.eclipse.ditto:device-123_ |
| `thing:namespace`  | Namespace (i.e. first part of an ID)  | _org.eclipse.ditto_ |
| `thing:name` | Name (i.e. second part of an ID ) | _device-123_ |

Additionally to the placeholders mentioned above, all documented 
[connection placeholders](basic-placeholders.html#scope-connections) may be
used in target addresses. However, if any placeholder in the target address fails to resolve, then the message will be
dropped.

Example:

Sending live commands and events to a target address that contains the thing's namespace.
```json
   {
      "id": "target-placeholder-example",
      "targets": [
        {
          "addresses": [ "live/{% raw %}{{ thing:namespace }}{% endraw %}" ],
          "authorizationContext": ["ditto:auth-subject"],
          "topics": [ "_/_/things/live/events", "_/_/things/live/commands" ]
        }
      ]
  }
```

## SSH tunneling

Ditto supports tunneling a connection by establishing an SSH tunnel and using it to connect to the actual endpoint.

See [SSH tunneling](connectivity-ssh-tunneling.html) on how to setup and configure SSH tunneling with Ditto. 
