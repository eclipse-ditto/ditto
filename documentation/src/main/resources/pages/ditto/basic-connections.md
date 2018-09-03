---
title: Connections
keywords: connection, connectivity, mapping, connection, integration
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
[Eclipse Hono](https://eclipse.org/hono/) or a [RabbitMQ](https://www.rabbitmq.com/) broker via custom "connections". 

A connection represents a communication channel for the exchange of messages between any service and Ditto. It 
requires a transport protocol, which is used to transmit [Ditto Protocol] messages. Ditto supports one-way and two-way
 communication over connections. This enables consumer/producer scenarios as well as fully-fledged command and response
 use cases. Nevertheless, those options might be limited by the transport protocol or the other endpoint's
 capabilities.
 
All connections are configured and supervised via Ditto's 
[Connectivity service](architecture-services-connectivity.html). The following model defines the connection itself:

{% include docson.html schema="jsonschema/connection.json" %}

The top design priority of this model is to be as generic as possible, while still allowing protocol specific 
customizations and tweaks. This enables the implementations of different customizable connection types, and support 
for custom payload formats. Currently the following connection types are supported:


* [AMQP 0.9.1](connectivity-protocol-bindings-amqp091.html)
* [AMQP 1.0](connectivity-protocol-bindings-amqp10.html)
* [MQTT 3.1.1](connectivity-protocol-bindings-mqtt.html)
 
 
The `sources` and `targets` identifier format depends on the `connectionType` and has therefore `connectionType` 
specific limitations. Those are documented with the corresponding protocol bindings.

A connection is initiated by the connectivity service. This obviates the need for client authorization, because
Ditto becomes the client in this case. Nevertheless, to access resources within Ditto, the connection must know on 
whose behalf it is acting. This is controlled via the configured `authorizationContext`, which holds a list of
self-assigned authorization subjects. Before a connection can access a Ditto resource, one of its 
`authorizationSubject`s must be granted the access rights by an authorization mechanism such as
[ACLs](basic-acl.html) or [Policies](basic-policy.html).

For more information on the `mappingContext` see the corresponding [Payload Mapping Documentation](connectivity-mapping.html)

## Placeholders

The configuration of a connection allows to use placeholders at certain places. This allows more fine grained control 
over how messages are consumed or where they are published to. The general syntax of a placeholder is 
`{% raw %}{{ placeholder }}{% endraw %}`. A missing placeholder results in an error which is passed back to the sender (if a _reply-to_
 header was provided). Which placeholder values are available depends on the context where the placeholder is used. 

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
For a Thing with the ID _org.eclipse.ditto:device-123_ these placeholders are resolved as follows:

| Placeholder | Description | Resolved value |
|--------|------------|------------|
| `thing:id`  | Full ID composed of _namespace_  `:` (as a separator), and _name_ | org.eclipse.ditto:device-123 |
| `thing:namespace`  | Namespace (i.e. first part of an ID)  | org.eclipse.ditto |
| `thing:name` | Name (i.e. second part of an ID ) | device-123 |


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

[Connectivity API]: connectivity-overview.html
[Ditto Protocol]: protocol-overview.html
