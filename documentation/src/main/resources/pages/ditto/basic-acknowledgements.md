---
title: Acknowledgements / Quality of Service
keywords: acks, acknowledgement, acknowledgements, qos, at least once, delivery, guarantee
tags: [model]
permalink: basic-acknowledgements.html
---

Acknowledgements are a concept in Ditto used to indicate that a signal
([twin](protocol-twinlive.html) [event](basic-signals-event.html),
[live](protocol-twinlive.html) [command](basic-signals-command.html) or
[live message](protocol-specification-things-messages.html))
was successfully 
received or processed by either an internal Ditto functionality or an external subscriber of that signal.

They can be seen as (potentially multiple) responses to a single command.

Acknowledgements can be used in order to solve the following cases:

* postpone a response to an API request (e.g. block an HTTP request) until one or more specific actions were performed in 
  Ditto (e.g. a modification was successfully persisted).
* postpone a response until an external subscriber connected to Ditto reports that it successfully processed an 
  [event](basic-signals-event.html) which e.g. resulted by a persistence change of Ditto. 
* provide a QoS (quality of service) guarantee of "at least once" when processing 
  messages in an end-to-end manner by e.g. technically acknowledging/settling a processed signal from a message broker 
  (e.g. [AMQP 1.0](connectivity-protocol-bindings-amqp10.html) or [MQTT](connectivity-protocol-bindings-mqtt.html)) only
  after it was successfully applied to Ditto and potentially also to 3rd parties.


## Acknowledgement requests

For the following, it is possible to define that certain "acknowledgements" are *requested*.
- Each Ditto API invocation *modifying the state of a twin* with a [command](basic-signals-command.html)
  (i. e., twin commands),
- Live commands,
- Live messages.

This means that the Ditto collects all of those requested acknowledgements until the command or live message
is successfully processed within a specified timeout interval.

Acknowledgement requests are expressed as protocol specific header fields of commands, more on that 
[later](#requesting-acknowledgements).

The [events](basic-signals-event.html) emitted by Ditto will include the custom acknowledgement requests in the 
`"requested-acks"` header.


## Acknowledgement labels

Acknowledgement labels identify the acknowledgement. Some labels are already engaged by Ditto for other purposes, 
and may therefore not be used when sending back a custom acknowledgement.

### Built-in acknowledgement labels

Ditto provides built-in acknowledgement requests that are automatically fulfilled on certain actions in the Ditto cluster:
* **twin-persisted**: For acknowledgement requests of twin modifying commands. It is fulfilled when a modifying
command has successfully updated the digital twin in Ditto's persistence. It is ignored for commands in the live
channel.
* **live-response**: For acknowledgement requests of live commands and live messages. It is fulfilled when a subscriber
of the live command or message sends a corresponding response. It is ignored for commands in the twin channel.

### Custom acknowledgement labels

In addition to the [built-in](#built-in-acknowledgement-labels) acknowledgement requests, 
a command or live message can contain custom acknowledgement requests. 
The subscriber can detect that an acknowledgement was requested (via the `"requested-acks"` header),
and - as far as it feels responsible for handling it - it [issues an acknowledgement](#issuing-acknowledgements).


## Acknowledgements

A single acknowledgement contains the following information:
* Acknowledgement label (one of the requested labels of the [ack requests](#acknowledgement-requests))
* Header fields
    * mandatory: **correlation-id** the same correlation-id as the one of the event which requested the acknowledgement
    * optional: additional header fields
* Status code (HTTP status code semantic) defining whether an acknowledgement was successful or not
* Optional payload as JSON

The [Ditto Protocol specification](protocol-specification-acks.html) describes in detail what is contained.<br/>
An example of how acknowledgements in Ditto Protocol look like can be found at the
[acknowledgements acks examples](protocol-examples.html#acknowledgements-acks) section.


## Requesting acknowledgements

With every API call *modifying the state of a twin* and with every live command or live message,
there is the option to request acknowledgements.  

### Requesting acks via HTTP

Either specify the following HTTP header fields:
  * **requested-acks**: a comma separated list of [acknowledgement labels](#acknowledgement-labels)<br/>
  Example: *requested-acks*: twin-persisted,my-custom-ack
  * **timeout**: an optional time (in ms, s or m) of how long the HTTP request should wait for acknowledgements and block.
  Default and maximum value: `60s`<br/>
  Examples: *timeout*: `42s`, *timeout*: `250ms`, *timeout*: `1m`

Or specify the header fields as query parameters to the HTTP params, e.g.:
```
PUT /api/2/things/org.eclipse.ditto:thing-1?requested-acks=twin-persisted,my-custom-ack&timeout=42s
```

The response of an HTTP request, which requested several acknowledgements, will differ from the response, 
where an HTTP request was not requesting acknowledgements.

Example response when 2 acknowledgements were requested and were successful. The overall HTTP status code will be 
`200` (OK) in this case:
```json
{
  "twin-persisted": {
    "status": 201,
    "payload": {
      "thingId": "org.eclipse.ditto:thing-1",
      "policyId": "org.eclipse.ditto:thing-1"
    },
    "headers": {
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093",
      "version": 2,
      "etag": "\"rev:1\"",
      "location": "http://127.0.0.1:8080/api/2/things/org.eclipse.ditto:thing-1"
    }
  },
  "my-custom-ack": {
    "status": 200,
    "payload": {
      "outcome": "green"
    },
    "headers": {
      "version": 2,
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093"
    }
  }
}
```

Example response when 2 acknowledgements were requested and one lead to a timeout. The overall HTTP status code will be 
`424` (Dependency failed) in this case:
```json
{
  "twin-persisted": {
    "status": 201,
    "payload": {
      "thingId": "org.eclipse.ditto:thing-1",
      "policyId": "org.eclipse.ditto:thing-1"
    },
    "headers": {
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093",
      "version": 2,
      "etag": "\"rev:1\"",
      "location": "http://127.0.0.1:8080/api/2/things/org.eclipse.ditto:thing-1"
    }
  },
  "my-custom-ack": {
    "status": 408,
    "payload": {
      "status": 408,
      "error": "acknowledgement:request.timeout",
      "message": "The acknowledgement request reached the specified timeout of 42,000ms.",
      "description": "Try increasing the timeout and make sure that the requested acknowledgement is sent back in time."
    },
    "headers": {
      "version": 2,
      "correlation-id": "db878735-4957-4fd9-92dc-6f09bb12a093"
    }
  }
}
```

### Requesting acks via WebSocket

Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements
via WebSocket.

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

### Requesting acks via connections

Acknowledgements for Ditto managed [connection sources](basic-connections.html#sources) can be requested in 2 ways: 

* specifically for each consumed command or live message as part of
  the [Ditto Protocol headers](protocol-specification.html#headers) `"requested-acks"` (as JsonArray of strings)
* by configuring the managed connection source to [request acknowledgements for all consumed messages](basic-connections.html#source-acknowledgement-requests)

#### Requesting acks via Ditto Protocol message

Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements
via established connections consuming messages from [sources](basic-connections.html#sources).

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

#### Requesting acks via connection source configuration

[Connection sources](basic-connections.html#sources) can be 
[configured to add specific acknowledgement requests](basic-connections.html#source-acknowledgement-requests) for each
consumed message of the underlying physical connection (e.g. to a message broker).

This can be used in order to ensure that e.g. all messages consumed from a single source should be processed in an 
"at least once" mode (QoS 1). E.g. if configured that the [built-in](#built-in-acknowledgement-labels) `twin-persisted`
acknowledgement is requested, a received twin-modifying command will only be 
technically acknowledged to the connection channel if Ditto successfully applied and persisted the command.

{% include note.html content="These requested acknowledgements will be appended after payload mapping is applied.<br/> 
                              This means, that in case you decided to split your message into multiple messages, all of these messages will request the same acknowledgements.<br/>
                              If this is not what you want to achieve, have a look at [how to add acknowledgement requests during payload mapping](#requesting-acks-via-ditto-protocol-message-in-payload-mapping)." %}

#### Requesting acks via Ditto Protocol message in payload mapping

During inbound payload mapping, you can create one or more Ditto Protocol messages. 

If you configured your connection source to add requested acknowledgements to your commands, this will cause all 
produced messages to request the same acknowledgements.<br/>
If you however want to add requested acknowledges only to some of those created messages, you need to set the 
`"requested-acks"` header (as described in 
[Requesting acks via Ditto Protocol message](#requesting-acks-via-ditto-protocol-message) section) during payload mapping for 
those commands you like to request an acknowledgement.

## Issuing acknowledgements

Acknowledgements are issued by subscribers of events generated by twin-modifying commands, or by subscribers of
live commands and live messages. In order to issue a single acknowledgement, a 
[Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message has to be built and sent 
back, using the same `"correlation-id"` in the [protocol headers](protocol-specification.html#headers) as contained
in the received twin event, live command or live message.

### Issuing acks via HTTP

It is not possible to issue acknowledgements via HTTP, because it is not possible to subscribe for twin events,
live commands or live messages via HTTP.

### Issuing acks via WebSocket

Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over an
established WebSocket in response to a twin event, live command or live message that contains a `"requested-acks"`
header.

### Issuing acks via connections

Requested acknowledgements for Ditto managed [connection targets](basic-connections.html#targets) can be issued in 2 
ways: 

* specifically for each published twin event, live command or live message by sending a
  [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) back,
  via a source of the same connection;
* by configuring the managed connection target to automatically
  [issue acknowledgements](basic-connections.html#target-issued-acknowledgement-label) for all published twin events,
  live commands and live messages that request them.

#### Issuing acks via Ditto Protocol acknowledgement message

Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over a  
source of the connection, in response to an event, which contained an `"requested-acks"` header.

#### Issuing acks via connection target configuration

[Connection targets](basic-connections.html#targets) can be configured to 
[issue certain acknowledgements automatically](basic-connections.html#target-issued-acknowledgement-label)
for each twin event, live command or live message published to
the underlying physical connection (e.g. to a message broker).

This can be used in order to automatically issue technical acknowledgements once an event, command or message
was published to an HTTP endpoint or into a message broker. When this target guarantees having processed the event
via its protocol (for HTTP for example when a status code `2xx` is returned), a successful acknowledgement is created
and returned to the requester.


## Quality of Service

### QoS 0 - at most once

By default, all messages/commands processed by Ditto are processed in an "at most once" (or QoS 0) semantic. 
For many of the use cases in the IoT QoS 0 is sufficient, e.g. when processing telemetry data of a sensor. 
If one sensor value is not applied to the digital twin there will soon follow the next sensor reading, and the twin will 
be eventually up to date again.

### QoS 1 - at least once
However, there are IoT use cases where it is of upmost importance that a message is processed "at least once" (or QoS 1), 
e.g. in order to guarantee that it was persisted in the digital twin or that an [event](basic-signals-event.html) consumer 
connected to Ditto did successfully receive a [notification](basic-changenotifications.html) which resulted from a 
[command](basic-signals-command.html) which Ditto received and processed.

The "acknowledgements" concept documented on this page provides means by which commands and live messages consumed and
processed by Ditto are treated with an "at least once" (or QoS 1) semantic.

[Create/modify commands](protocol-specification-things-create-or-modify.html) will technically be acknowledged on the 
sent channel (e.g. HTTP or WebSocket or any [connection type](basic-connections.html#connection-types)) 
when it was a success.<br/>
When it could not be applied successfully, the command will be negatively acknowledged, which will reflect in the 
[status of the acknowledgement](protocol-specification-acks.html#combined-status-code). 

Based on the used channel, the acknowledgement will be translated to the capabilities of the channel, e.g. for HTTP
an HTTP response will be sent with the outcome as HTTP status (`2xx` for a successful acknowledgement, and `4xx` for a 
non-successful one) together with additional details as HTTP response.

### Assure QoS until persisted in Ditto - twin-persisted

In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in a 
successful update of twin in Ditto's managed database, [request the acknowledgement](#requesting-acknowledgements) for
the [built-in "twin-persisted"](#built-in-acknowledgement-labels) acknowledgement label.

### Assure QoS until processing of a live command/message by a subscriber - live-response

In order to ensure that a live command or live message is processed by a subscriber,
[request the acknowledgement](#requesting-acknowledgements) for
the [built-in "live-response"](#built-in-acknowledgement-labels) acknowledgement label. This acknowledgement request
is fulfilled when the subscriber sends a live response or message response.

### Assure QoS until processing of a twin event or live command/message by subscribers - custom label

In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in an 
event which was consumed by an application integrating with Ditto, or that a live command or live message is consumed
without any live or message response,
[request the acknowledgement](#requesting-acknowledgements) for a [custom acknowledgement label](#custom-acknowledgement-labels).

## Interaction between headers

3 headers control how Ditto responds to a command: `response-required`, `requested-acks`, `timeout`.
- `response-required`: `true` or `false`. It governs whether the user gets a (detailed) reply.
- `requested-acks`: JSON array of acknowledgement requests. It governs the content of the reply and transport-layer message settlement.
- `timeout`: Duration. It governs how long Ditto waits for responses and acknowledgements.

It is considered a client error if `timeout` is set to `0s` while `response-required` is `true` or
`requested-acks` is nonempty.

Each API interprets the 3 headers as follows.

### HTTP

Since an HTTP response always follows an HTTP request, the header `response-required` is interpreted as whether
the user wants a detailed response. When it is set to `false`, the HTTP response consists of status line and headers
without body, or with a minimal body containing other status codes. When acknowledgements are requested, the HTTP
response is delayed until all requested acknowledgements are received.

|API         |response-required|requested-acks|timeout|Outcome|
|------------|-----------------|--------------|-------|-------|
|HTTP        |false            |empty         |zero   |202 Accepted immediately|
|HTTP        |false            |empty         |nonzero|202 Accepted immediately|
|HTTP        |false            |nonempty      |zero   |400 Bad Request: timeout may not be zero when acknowledgements are requested|
|HTTP        |false            |nonempty      |nonzero|202 Accepted after receiving the requested acknowledgements|
|HTTP        |true             |empty         |zero   |400 Bad Request: timeout may not be zero when response is required|
|HTTP        |true             |empty         |nonzero|Command response|
|HTTP        |true             |nonempty      |zero   |400 Bad Request: timeout may not be zero when response is required|
|HTTP        |true             |nonempty      |nonzero|Aggregated response and acknowledgements|

### Websocket

In the absence of client errors, a reply is sent for a command if and only if `response-required` is set to `true`.
Ditto supports no transport-layer message settlement for Websocket; acknowledgements are only received as text frames.
Consequently, it is considered a client error to have nonempty `requested-acks` while `response-required` is set to
`false`.

|API         |response-required|requested-acks|timeout|Outcome|
|------------|-----------------|--------------|-------|-------|
|Websocket   |false            |empty         |zero   |No reply|
|Websocket   |false            |empty         |nonzero|No reply|
|Websocket   |false            |nonempty      |zero   |Error: timeout may not be zero when acknowledgements are requested|
|Websocket   |false            |nonempty      |nonzero|Error: Websocket cannot send acknowledgements without a response|
|Websocket   |true             |empty         |zero   |Error: timeout may not be zero when response is required|
|Websocket   |true             |empty         |nonzero|Command response|
|Websocket   |true             |nonempty      |zero   |Error: timeout may not be zero when response is required|
|Websocket   |true             |nonempty      |nonzero|Aggregated response and acknowledgements|

### Connectivity

For any incoming command through a connection source, the header `response-required` determines whether a reply message
is published at the reply-target of the source. The header `requested-acks` determines the transport-layer
message settlement and the content of any reply message published at the reply-target. Examples of transport-layer
message settlement mechanisms are AMQP 0.9.1 consumer acknowledgement mode, AMQP 1.0 disposition frames,
and MQTT PUBACK/PUBREC/PUBREL messages for incoming PUBLISH with QoS 1 or 2.

|API         |response-required|requested-acks|timeout|Outcome|
|------------|-----------------|--------------|-------|-------|
|Connectivity|false            |empty         |zero   |Nothing published at reply-target; message settled immediately|
|Connectivity|false            |empty         |nonzero|Nothing published; message settled immediately|
|Connectivity|false            |nonempty      |zero   |Error published: timeout may not be zero when acknowledgements are requested; message settled negatively|
|Connectivity|false            |nonempty      |nonzero|Nothing published; message settled after receiving the requested acknowledgements|
|Connectivity|true             |empty         |zero   |Error published: timeout may not be zero when response is required; message settled negatively|
|Connectivity|true             |empty         |nonzero|Command response published; message settled immediately|
|Connectivity|true             |nonempty      |zero   |Error published: timeout may not be zero when response is required; message settled negatively|
|Connectivity|true             |nonempty      |nonzero|Aggregated response and acknowledgements published; message settled after receiving the requested acknowledgements|

### Default header values

Ditto set each of the 3 headers `response-required`, `requested-acks`, `timeout` to a default value according to any
values of the other 2 headers set by the user. The default values depend only on headers set by the user; they do not
depend on each other. Setting the default header values this way never produces any combination considered a client
error unless the headers set by the user already cause a client error.

|Header           |Default value|Default value when all 3 headers are not set|
|-----------------|-----------------|--------------|
|response-required|`false` if `timeout` is zero or `requested-acks` is empty, `true` otherwise|empty         |
|requested-acks   |`empty` if `timeout` is zero or `response-required` is `false`, the channel's default acknowledgement request otherwise|`["twin-persisted"]` for TWIN channel, `["live-response"]` for LIVE channel|
|timeout          |`60s`|`60s`|

