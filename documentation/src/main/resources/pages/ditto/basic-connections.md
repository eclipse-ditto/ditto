---
title: Connections
keywords: connection, connectivity, mapping, connection, integration, placeholder
tags: [connectivity]
permalink: basic-connections.html
---

## Connection model

  {%
    include note.html content="To get started with connections right away, consult the
    [Manage connections](connectivity-manage-connections.html)
    page. "
  %}

You can integrate your Ditto instance with external messaging services such as 
[Eclipse Hono](https://eclipse.org/hono/), a [RabbitMQ](https://www.rabbitmq.com/) broker or an 
[Apache Kafka](https://kafka.apache.org/) broker via custom "connections".

Additionally, you may invoke foreign HTTP endpoints by using the 
[HTTP connection type](connectivity-protocol-bindings-http.html).

A connection represents a communication channel for the exchange of messages between any service and Ditto. It 
requires a transport protocol, which is used to transmit [Ditto Protocol](protocol-overview.html) messages. Ditto supports one-way and two-way
communication over connections. This enables consumer/producer scenarios as well as fully-fledged command and response
use cases. Nevertheless, those options might be limited by the transport protocol or the other endpoint's
capabilities.
 
All connections are configured and supervised via Ditto's 
[Connectivity service](architecture-services-connectivity.html). The following model defines the connection itself:

{% include docson.html schema="jsonschema/connection.json" %}


### Connection types

The top design priority of this model is to be as generic as possible, while still allowing protocol specific 
customizations and tweaks. This enables the implementations of different customizable connection types, and support 
for custom payload formats. Currently the following connection types are supported:

* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
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

Sources contain:
* several addresses (depending on the [connection type](#connection-types) those are interpreted differently, e.g. as queues, topics, etc.)
* a consumer count defining how many consumers should be attached to each source address
* an authorization context (see [authorization](#authorization)) specifying which [authorization subject](basic-acl.html#authorization-subject) is used to authorize messages from the source 
* enforcement information that allows filtering the messages that are consumed in this source
* [header mapping](connectivity-header-mapping.html) for mapping headers of source messages to internal headers

#### Source enforcement

Messages received from external systems are mapped to Ditto internal format, either by applying some custom mapping or 
the default mapping for [Ditto Protocol](protocol-overview.html) messages. 

During this mapping the digital twin of the device is determined i.e. 
which Thing is accessed or modified as a result of the message. By default no sanity check is done if this target Thing 
corresponds to the device that originally sent the message. In some use cases this might be valid, but in other scenarios 
you might want to enforce that a device only sends data to its digital twin. Note that this could also be achieved by 
assigning a specific policy to each device and use [placeholders](#placeholders) in the 
authorization subject, but this can get cumbersome to maintain for a large number of devices.

With an enforcement you can use a single policy for all devices 
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
| `{%raw%}{{ header:<name> }}{%endraw%}` | Any header from the message received via the source. | `{%raw%}{{header:device_id }}{%endraw%}`  |
| `{%raw%}{{ source:address }}{%endraw%}` | The address on which the message was received. | devices/sensors/temperature1  |


The following placeholders are available for the `filters` field:

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | Full ID composed of ''namespace'' + '':'' as a separator + ''name''  | eclipse.ditto:thing-42  |
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) | eclipse.ditto |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (i.e. second part of an ID ) | thing-42  |

Assuming a device `sensor:temperature1` pushes its telemetry data to Ditto which is stored in a Thing 
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

#### Source header mapping

For incoming messages, an optional [header mapping](connectivity-header-mapping.html) may be applied.

The JSON for a source with header mapping could like this:
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

### Targets

Targets are used to connect to messages brokers / external systems in order to publish messages **to them**.

Target messages can be of the following type:
* [messages](basic-messages.html)
* [events](basic-signals-event.html)
* [live commands/responses/events](protocol-twinlive.html)

Targets contain:
* one address (that is interpreted differently depending on the [connection type](#connection-types), e.g. as queue, topic, etc.)
* [topics](#target-topics-and-filtering) that will be sent to the target
* an authorization context (see [authorization](#authorization)) specifying which [authorization subject](basic-acl.html#authorization-subject) is used to authorize messages to the target 
* [header mapping](connectivity-header-mapping.html) for mapping headers internal headers to target headers

#### Target topics and filtering

For targets it can be configured which types of messages should be published to the target address.

In order to only consume specific events like described in [change notifications](basic-changenotifications.html), the
following parameters can additionally be provided when specifying the `topics` of a target:

| Description | Topic | Filter by namespaces | Filter by RQL expression |
|-------------|-----------------|------------------|-----------|
| Subscribe for [events/change notifications](basic-changenotifications.html) | `_/_/things/twin/events` | &#10004; | &#10004; |
| Subscribe for [messages](basic-messages.html) | `_/_/things/live/messages` | &#10004; | &#10060; |
| Subscribe for [live commands](protocol-twinlive.html) | `_/_/things/live/commands` | &#10004; | &#10060; |
| Subscribe for [live events](protocol-twinlive.html) | `_/_/things/live/events` | &#10004; | &#10004; |

The parameters are specified similar to HTTP query parameters, the first one separated with a `?` and all following ones
with `&`. You have to URL encode the filter values before using them in a configuration.

For example this way the connection session would register for all events in the namespace `org.eclipse.ditto` and which
would match an attribute "counter" to be greater than 42. Additionally it would subscribe to messages in the namespace
`org.eclipse.ditto`:
```json
{
  "address": "<target-address>",
  "topics": [
    "_/_/things/twin/events?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)",
    "_/_/things/live/messages?namespaces=org.eclipse.ditto"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject", "..."]
}
```

#### Target header mapping

For outgoing messages, an optional [header mapping](connectivity-header-mapping.html) may be applied.

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
`authorizationSubject`s must be granted the access rights by an authorization mechanism such as
[ACLs](basic-acl.html) or [Policies](basic-policy.html).

A connection target can only send data for Things to which it has READ rights, as data flows from a Thing to a target. 
A connection source can only receive data for Things to which it has WRITE rights, as data flows from a source to a Thing.

### Specific config

Some [connection types](#connection-types) require specific configuration which are not supported for other connection types.
Those are put into the `specificConfig` field.

### Mapping context

For more information on the `mappingContext` see the corresponding [Payload Mapping Documentation](connectivity-mapping.html).

## Placeholders

The configuration of a connection allows to use placeholders at certain places. This allows more fine grained control 
over how messages are consumed or where they are published to. The general syntax of a placeholder is 
`{% raw %}{{ placeholder }}{% endraw %}`. Have a look at the [placeholders concept](basic-placeholders.html) for more details on that. 
A missing placeholder results in an error which is passed back to the sender (if a _reply-to_ header was provided). 

### Placeholder for source authorization subjects

Processing the messages received via a source using the _same fixed authorization subject_ may not be 
suitable for every scenario. For example, if you want to declare fine-grained write permissions per device, this would not 
be possible with a fixed global subject. For this use case we have introduced placeholder substitution for authorization subjects of 
source addresses that are resolved when processing messages from a source. Of course, this requires the sender of the 
message to provide necessary information about the original issuer of the message. 

  {%
    include important.html content="Only use this kind of placeholder if you trust the source of the message. The value from the header is used as the **authorized subject**."
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
The placeholder is then replaced by the value from the message headers and the message is forwarded and processed under the 
subject _device:sensor-123_.
In case the header cannot be resolved or the header contains unexpected characters an exception is thrown which is sent 
back to the sender as an error message, if a valid _reply-to_ header was provided, otherwise the message is dropped.

### Placeholder for target addresses

Another use case for placeholders may be to publish Thing events or live commands and events to a target address 
containing Thing-specific information e.g. you can distribute Things from different namespaces to different target addresses.
You can use the placeholders `{% raw %}{{ thing:id }}{% endraw %}`, `{% raw %}{{ thing:namespace }}{% endraw %}` and `{% raw %}{{ thing:name }}{% endraw %}` in the target address for this purpose.
For a Thing with the ID _org.eclipse.ditto:device-123_ these placeholders would be resolved as follows:

| Placeholder | Description | Resolved value |
|--------|------------|------------|
| `thing:id`  | Full ID composed of _namespace_  `:` (as a separator), and _name_ | _org.eclipse.ditto:device-123_ |
| `thing:namespace`  | Namespace (i.e. first part of an ID)  | _org.eclipse.ditto_ |
| `thing:name` | Name (i.e. second part of an ID ) | _device-123_ |

Even more than the ones above, all mentioned [connection placeholders](basic-placeholders.html#scope-connections) may be
used in target addresses. 

Example:

Sending live commands and events to a target address that contains the Things' namespace.
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
