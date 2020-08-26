---
title: Acknowledgements / Quality of Service
keywords: acks, acknowledgement, acknowledgements, qos, at least once, delivery, guarantee
tags: [model]
permalink: basic-acknowledgements.html
---

Acknowledgements are a concept in Ditto used to indicate that a
[supported signal](#supported-signal-types--supportedsignaltypes) was successfully received or processed by either an
internal Ditto functionality or an external subscriber of that signal.

Acknowledgements can be seen as (potentially multiple) responses to a single signal like for example a twin command.
This means that Ditto collects all the [requested acknowledgements](#requesting-acknowledgements) until the signal is
successfully processed within a specified timeout interval.

## Usage scenario examples
Acknowledgements are useful for accomplishing the following example tasks:

* Postpone a response to an API request (e.g. block an HTTP request) until one or more specific actions were performed
  in Ditto (e.g. a modification was successfully persisted).
* Postpone a response until an external subscriber connected to Ditto reports that it successfully processed an 
  [event](basic-signals-event.html) which e.g. resulted by a persistence change of Ditto. 
* Provide a QoS (quality of service) guarantee of "at least once" when processing 
  messages in an end-to-end manner by e.g. technically acknowledging/settling a processed signal from a message broker 
  (e.g. [AMQP 1.0](connectivity-protocol-bindings-amqp10.html) or [MQTT](connectivity-protocol-bindings-mqtt.html)) only
  after it was successfully applied to Ditto and potentially also to 3rd parties.


## Supported signal types
For the following signal types it is possible to define that certain acknowledgements are *requested*.
* [Commands](basic-signals-command.html) *modifying the state of a twin* (Twin commands),
* Live commands,
* Live messages.


## Acknowledgement labels
A common (matching) label links acknowledgement requests and their corresponding acknowledgements.
Ditto already uses some labels for its built-in acknowledgement requests.
Those labels may not be used when sending back a custom acknowledgement.

### Built-in acknowledgement labels
Ditto provides built-in acknowledgement requests that are automatically fulfilled on certain actions within the Ditto
cluster:
* **twin-persisted**: For acknowledgement requests of twin modifying commands.
It is fulfilled when a modifying command has successfully updated the digital twin in Ditto's persistence.
It is ignored for commands in the live channel.
* **live-response**: For acknowledgement requests of live commands and live messages.
It is fulfilled when a subscriber of the live command or message sends a corresponding response.
It is ignored for commands in the twin channel.

### Custom acknowledgement labels
In addition to the [built-in](#built-in-acknowledgement-labels) acknowledgement requests, 
a supported signal can contain custom acknowledgement requests. 
A subscriber of such a signal can detect a requested acknowledgement via the `"requested-acks"` header.
If the subscriber is in charge of handling a requested acknowledgement it
[issues an acknowledgement](#issuing-acknowledgements).


## Acknowledgements (ACKs)
A single acknowledgement contains the following information:
* Acknowledgement label (one of the requested labels of the [ack requests](#acknowledgement-requests))
* Header fields
    * mandatory: **correlation-id** the same correlation ID as the one of the signal which requested the acknowledgement
    * optional: additional header fields
* Status code (HTTP status code semantic) defining whether an acknowledgement was successful or not
* Optional payload as JSON

The [Ditto Protocol specification](protocol-specification-acks.html) describes in detail what is contained.<br/>
An example of how acknowledgements in Ditto Protocol look like can be found at the
[acknowledgement examples](protocol-examples.html#acknowledgements-acks) section.


## Requesting ACKs 
With every supported signal there is the option to request acknowledgements.  
Acknowledgement requests are expressed as protocol specific header fields of signals.
The following sections explain the various ways of requesting acknowledgements.

[Events](basic-signals-event.html) emitted by Ditto will include the custom acknowledgement requests in the 
`"requested-acks"` header.

### Requesting ACKs via HTTP
Either specify the following HTTP header fields:
  * **requested-acks**: a comma separated list of [acknowledgement labels](#acknowledgement-labels).<br/>
  Example: `requested-acks: twin-persisted,my-custom-ack`.
  * **timeout**: an optional time interval (in ms, s or m) to define how long the HTTP request should wait for
  acknowledgements and block.
  Default and maximum value: `60s`.<br/>
  Examples: `timeout: 42s`, `timeout: 250ms`, `timeout: 1m`.

Or specify the header fields as query parameters to the HTTP params, e.g.:
```
PUT /api/2/things/org.eclipse.ditto:thing-1?requested-acks=twin-persisted,my-custom-ack&timeout=42s
```

The response of an HTTP request, which requested several acknowledgements, will differ from the response to an HTTP
request without acknowledgement requests.

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

### Requesting ACKs via WebSocket
Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements via
WebSocket.

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

### Requesting ACKs via connections
Acknowledgements for Ditto managed [connection sources](basic-connections.html#sources) can be requested in 2 ways: 
* specifically for each consumed supported signal as part of
  the [Ditto Protocol headers](protocol-specification.html#headers) `"requested-acks"` (as JsonArray of strings)
* by configuring the managed connection source to
  [request acknowledgements for all consumed supported signals](basic-connections.html#source-acknowledgement-requests).

#### Requesting ACKs via Ditto Protocol message
Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements
via established connections consuming messages from [sources](basic-connections.html#sources).

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

#### Requesting ACKs via connection source configuration
[Connection sources](basic-connections.html#sources) can be 
[configured to add specific acknowledgement requests](basic-connections.html#source-acknowledgement-requests) for each
consumed message of the underlying physical connection (e.g. to a message broker).

This can be used in order to ensure that e.g. all messages consumed from a single source should be processed in an 
"at least once" mode (QoS 1).
E.g. if configured that the [built-in](#built-in-acknowledgement-labels) `twin-persisted` acknowledgement is requested,
a received twin-modifying command will only be technically acknowledged to the connection channel if Ditto successfully
applied and persisted the command.

{% include note.html content="These requested acknowledgements will be appended after payload mapping is applied.<br/> 
                              This means, that in case you decided to split your message into multiple messages, all of these messages will request the same acknowledgements.<br/>
                              If this is not what you want to achieve, have a look at [how to add acknowledgement requests during payload mapping](#requesting-acks-via-ditto-protocol-message-in-payload-mapping)." %}

#### Requesting ACKs via Ditto Protocol message in payload mapping
During inbound payload mapping, you can create one or more Ditto Protocol messages. 

If you configured your connection source to add requested acknowledgements to your commands, this will cause all 
produced messages to request the same acknowledgements.<br/>
If you however want to add requested acknowledgements only to some of those created messages, you need to set the 
`"requested-acks"` header (as described in 
[Requesting ACKs via Ditto Protocol message](#requesting-acks-via-ditto-protocol-message) section) during payload
mapping for those commands you like to request an acknowledgement.


## Issuing acknowledgements
Acknowledgements are issued by subscribers of events generated by twin-modifying commands, or by subscribers of
live commands and live messages. In order to issue a single acknowledgement, a 
[Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message has to be built and sent 
back, using the same `"correlation-id"` in the [protocol headers](protocol-specification.html#headers) as contained
in the received twin event, live command or live message.

### Issuing ACKs via HTTP
It is not possible to issue acknowledgements via HTTP, because it is impossible to subscribe for twin events,
live commands or live messages via HTTP.

### Issuing ACKs via WebSocket
Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over an
established WebSocket in response to a twin event, live command or live message that contains a `"requested-acks"`
header.

### Issuing ACKs via connections
Requested acknowledgements for Ditto managed [connection targets](basic-connections.html#targets) can be issued in 2 
ways: 

* specifically for each published twin event, live command or live message by sending a
  [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) back,
  via a source of the same connection;
* by configuring the managed connection target to automatically
  [issue acknowledgements](basic-connections.html#target-issued-acknowledgement-label) for all published twin events,
  live commands and live messages that request them.

#### Issuing ACKs via Ditto Protocol acknowledgement message
Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over a  
source of the connection, in response to an event, live command or live message which contained an `"requested-acks"`
header.

#### Issuing ACKs via connection target configuration
[Connection targets](basic-connections.html#targets) can be configured to 
[issue certain acknowledgements automatically](basic-connections.html#target-issued-acknowledgement-label)
for each twin event, live command or live message published to the underlying physical connection (e.g. to a message
broker).

This can be used in order to automatically issue technical acknowledgements once an event, live command or live message
was published to an HTTP endpoint or into a message broker. When this target guarantees having processed the event
via its protocol (for HTTP for example when a status code `2xx` is returned), a successful acknowledgement is created
and returned to the requester.


## Quality of Service

### QoS 0 - at most once
By default, Ditto processes all messages/commands processed in an "at most once" (or QoS 0) semantic. 
For many of the use cases in the IoT, QoS 0 is sufficient, e.g. when processing telemetry data of a sensor:
if one sensor value is not applied to the digital twin there will soon follow the next sensor reading, and the twin will 
be eventually up to date again.

### QoS 1 - at least once
However, there are IoT use cases where it is of upmost importance that a signal is processed "at least once" (or QoS 1), 
e.g. in order to guarantee that it was persisted in the digital twin or that an [event](basic-signals-event.html)
consumer connected to Ditto did successfully receive a [notification](basic-changenotifications.html) which resulted
from a [command](basic-signals-command.html) which Ditto received and processed.

The "acknowledgements" concept documented on this page provides means by which supported signals Ditto consumes and
processes are treated with an "at least once" (or QoS 1) semantic.

[Create/modify commands](protocol-specification-things-create-or-modify.html) will technically be acknowledged on the 
sent channel (e.g. HTTP or WebSocket or any [connection type](basic-connections.html#connection-types)) when it was a
success.<br/>
If it could not be applied successfully, the signal will be negatively acknowledged.
The [status code of the acknowledgement](protocol-specification-acks.html#combined-status-code) reflects the failure. 

Based on the used channel, the acknowledgement will be translated to the capabilities of the command or live message
channel, e.g. for HTTP an HTTP response will be sent with the outcome as HTTP status (`2xx` for a successful
acknowledgement, and `4xx` for a non-successful one) together with additional details as HTTP response.

### Assure QoS until persisted in Ditto - twin-persisted
In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in a 
successful update of twin in Ditto's managed database, [request the acknowledgement](#requesting-acknowledgements) for
the [built-in "twin-persisted"](#built-in-acknowledgement-labels) acknowledgement label.

### Assure QoS until processing of a live command/message by a subscriber - live-response
In order to ensure that a live command or live message is processed by a subscriber,
[request the acknowledgement](#requesting-acknowledgements) for
the [built-in "live-response"](#built-in-acknowledgement-labels) acknowledgement label.
This acknowledgement request is fulfilled when the subscriber sends a live response or message response.

### Assure QoS until processing of a twin event or live command/message by subscribers - custom label
In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in an 
event which was consumed by an application integrating with Ditto, or that a live command or live message is consumed
without any live or message response, [request the acknowledgement](#requesting-acknowledgements) for a
[custom acknowledgement label](#custom-acknowledgement-labels).

## Interaction between headers
Three headers control how Ditto responds to a command: `response-required`, `requested-acks`, `timeout`.
* `response-required`: `true` or `false`.
   It governs whether the user gets a (detailed) reply.
* `requested-acks`: JSON array of acknowledgement requests.
   It determines the content of the response and transport-layer message settlement.
* `timeout`: Duration.
   It governs how long Ditto waits for responses and acknowledgements.

It is considered a client error if `timeout` is set to `0s` while `response-required` is `true` or `requested-acks` is
nonempty.

The following sections show how each Ditto API interprets the three headers.

### HTTP
Since an HTTP response always follows an HTTP request, the header `response-required` is interpreted as whether
the user wants a *detailed* response.
If it is set to `false`, the HTTP response consists of status line and headers without body, or with a minimal body
containing other status codes.
If acknowledgements are requested, the HTTP response is delayed until all requested acknowledgements are received.
Generally, if a request cannot be answered within the defined timeout, the HTTP response has status code 408.
A response containing successful acknowledgements (2xx) and at least one failed acknowledgement (4xx) has status code
424 (failed dependency).
In this case the status codes of all acknowledgements should be check to determine the one which caused the failure.

| API  | response-required | requested-acks | timeout   | Outcome |
| ---  | ---               | ---            | ---       | ---     |
| HTTP | false             | empty          | zero      | 202 Accepted immediately |
| HTTP | false             | empty          | non-zero  | 202 Accepted immediately |
| HTTP | false             | non-empty      | zero      | 400 Bad Request: timeout may not be zero if acknowledgements are requested |
| HTTP | false             | non-empty      | non-zero  | 202 Accepted after receiving the requested acknowledgements |
| HTTP | true              | empty          | zero      | 400 Bad Request: timeout may not be zero if response is required |
| HTTP | true              | empty          | non-zero  | Response |
| HTTP | true              | non-empty      | zero      | 400 Bad Request: timeout may not be zero if response is required |
| HTTP | true              | non-empty      | non-zero  | Aggregated response and acknowledgements |

### WebSocket
In the absence of client errors, a response is sent for a command if and only if `response-required` is set to `true`.
Ditto supports no transport-layer message settlement for WebSocket; acknowledgements are only received as text frames.
Consequently, it is considered a client error to have non-empty `requested-acks` while `response-required` is set to
`false`.

| API        | response-required | requested-acks | timeout  | Outcome |
| ---        | ---               | ---            | ---      | ---     |
| WebSocket  | false             | empty          | zero     | No response |
| WebSocket  | false             | empty          | non-zero | No response |
| WebSocket  | false             | non-empty      | zero     | Error: timeout may not be zero if acknowledgements are requested |
| WebSocket  | false             | non-empty      | non-zero | Error: WebSocket cannot send acknowledgements without a response |
| WebSocket  | true              | empty          | zero     | Error: timeout may not be zero if response is required |
| WebSocket  | true              | empty          | non-zero | Response |
| WebSocket  | true              | non-empty      | zero     | Error: timeout may not be zero if response is required |
| WebSocket  | true              | non-empty      | non-zero | Aggregated response and acknowledgements |

### Connectivity
For any incoming supported signal through a connection source, the header `response-required` determines whether a
response message is published at the reply-target of the source.
The header `requested-acks` determines the transport-layer message settlement and the content of any response message
published at the reply-target.
Examples of transport-layer message settlement mechanisms are AMQP 0.9.1 consumer acknowledgement mode, AMQP 1.0
disposition frames, and MQTT PUBACK/PUBREC/PUBREL messages for incoming PUBLISH with QoS 1 or 2.

| API          | response-required | requested-acks | timeout  | Outcome |
| ---          | ---               | ---            | ---      | ---     |
| Connectivity | false             | empty          | zero     | Nothing published at reply-target;<br/>message settled immediately |
| Connectivity | false             | empty          | non-zero | Nothing published at reply-target;<br/>message settled immediately |
| Connectivity | false             | non-empty      | zero     | Error published at reply-target: timeout may not be zero if acknowledgements are requested;<br/>message settled negatively |
| Connectivity | false             | non-empty      | non-zero | Nothing published at reply-target;<br/>message settled after receiving the requested acknowledgements |
| Connectivity | true              | empty          | zero     | Error published at reply-target: timeout may not be zero when response is required;<br/>message settled negatively |
| Connectivity | true              | empty          | non-zero | Response published at reply-target;<br/>message settled immediately |
| Connectivity | true              | non-empty      | zero     | Error published at reply-target: timeout may not be zero if response is required;<br/>message settled negatively |
| Connectivity | true              | non-empty      | non-zero | Aggregated response and acknowledgements published at reply-target;<br/>message settled after receiving the requested acknowledgements |

### Default header values
Ditto set each of the three headers `response-required`, `requested-acks`, `timeout` to a default value according to any
values of the other two headers set by the user.
The default values depend only on headers set by the user; they do not depend on each other.
Setting the default header values this way never produces any combination considered a client error unless the headers
set by the user already cause a client error.

| Header            | Default value | Default value if all three headers are not set |
| ---               | ---           | ---                                              |
| response-required | `false` if `timeout` is zero or `requested-acks` is empty, `true` otherwise | `true` |
| requested-acks    | `empty` if `timeout` is zero or `response-required` is `false`, the channel's default acknowledgement request otherwise |`["twin-persisted"]` for TWIN channel,<br/>`["live-response"]` for LIVE channel |
| timeout           | `60s` | `60s` |
