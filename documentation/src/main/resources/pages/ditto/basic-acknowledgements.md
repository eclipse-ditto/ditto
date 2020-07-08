---
title: Acknowledgements / Quality of Service
keywords: acks, acknowledgement, acknowledgements, qos, at least once, delivery, guarantee
tags: [model]
permalink: basic-acknowledgements.html
---

Acknowledgements are a concept in Ditto used to signal that an [event](basic-signals-event.html) was successfully 
received/processed by either an internal Ditto functionality or an external subscriber of that event.

They can be seen as (potentially multiple) responses to a single event (e.g. after a Thing was modified). 

Acknowledgements can be used in order to solve the following cases:
* postpone a response to an API request (e.g. block an HTTP request) until one or more specific actions were performed in 
  Ditto (e.g. a modification was successfully persisted).
* postpone a response until an external subscriber connected to Ditto reports that he successfully processed an 
  [event](basic-signals-event.html) which e.g. resulted by a persistence change of Ditto. 
* provide a QoS (quality of service) guarantee of "at least once" when processing 
  messages in an end-2-end manner by e.g. technically acknowledging/settling a processed message from a message broker 
  (e.g. [AMQP 1.0](connectivity-protocol-bindings-amqp10.html) or [MQTT](connectivity-protocol-bindings-mqtt.html)) only
  after it was successfully applied to Ditto and potentially also to 3rd parties.


## Acknowledgement requests

For each Ditto API invocation *modifying the state of a twin* with a [command](basic-signals-command.html) it is 
possible to define that certain "acknowledgements" are *requested*.<br/>
This means that the Ditto collects all of those requested acknowledgements until the message is handled as successfully 
processed, applying a specified timeout interval.

Acknowledgement requests are expressed as protocol specific header fields of commands, more on that 
[later](#requesting-acknowledgements).

The [events](basic-signals-event.html) emitted by Ditto will include the custom acknowledgement requests in the 
`"requested-acks"` header.


## Acknowledgement labels

Acknowledgement labels identify the acknowledgement. Some labels are already engaged by Ditto for other purposes, 
and may therefore not be used when sending back a custom acknowledgement.

### Built-in acknowledgement labels

Ditto provides built-in acknowledgements that are automatically issued on certain actions in the Ditto cluster:
* **twin-persisted**: An acknowledgement with this label is issued, when a modifying command has successfully 
updated the digital twin in Ditto's persistence.

### Custom acknowledgement labels

In addition to the [built-in](#built-in-acknowledgement-labels) acknowledgement requests, 
a received [event](basic-signals-event.html) can contain
custom acknowledgement labels. 
The event subscriber can detect that an acknowledgement was requested 
(via the `"requested-acks"` header), and - as far as it feels responsible for handling it - it  
[issues an acknowledgement](#issuing-acknowledgements).


## Acknowledgements

A single acknowledgement contains the following information:
* Acknowledgement label (one of the requested labels of the [ack requests](#acknowledgement-requests) of e.g. an event)
* Header fields
    * mandatory: **correlation-id** the same correlation-id as the one of the event which requested the acknowledgement
    * optional: additional header fields
* Status code (HTTP status code semantic) defining whether an acknowledgement was successful or not
* Optional payload as JSON

The [Ditto Protocol specification](protocol-specification-acks.html) describes in detail what is contained.<br/>
An example of how acknowledgements in Ditto Protocol look like can be found at the
[acknowledgements acks examples](protocol-examples.html#acknowledgements-acks) section.


## Requesting acknowledgements

With every API call *modifying the state of a twin* there is the option to provide requested acknowledgements.  

### Via HTTP

Either specify the following HTTP header fields:
  * **requested-acks**: a comma separated list of [acknowledgement labels](#acknowledgement-labels)<br/>
  Example: *requested-acks*: twin-persisted,my-custom-ack
  * timeout: an optional time (in ms, s or m) of how long the HTTP request should wait for acknowledgements and block. Default: `60s`<br/>
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

### Requesting acknowledgements via WebSocket

Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements
via WebSocket.

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

### Requesting acknowledgements via connections

Acknowledgements for Ditto managed [connection sources](basic-connections.html#sources) can be requested in 2 ways: 

* specifically for each consumed message as part of the [Ditto Protocol headers](protocol-specification.html#headers) `"requested-acks"` (as JsonArray of strings)
* by configuring the managed connection source to [request acknowledgements for all consumed messages](basic-connections.html#source-acknowledgement-requests)

#### Requesting via Ditto Protocol message

Together with a received Ditto [command](basic-signals-command.html) in [Ditto Protocol](protocol-specification.html),
`"requested-acks"` (as JsonArray of strings) and `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) can be included in order to request acknowledgements
via established connections consuming messages from [sources](basic-connections.html#sources).

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.

#### Requesting via connection source configuration

[Connection sources](basic-connections.html#sources) can be 
[configured to add certain acknowledgement requests](basic-connections.html#source-acknowledgement-requests) for each
consumed message of the underlying physical connection (e.g. to a message broker).

This can be used in order to ensure that e.g. all messages consumed from a single source should be processed in an 
"at least once" mode (QoS 1). E.g. if configured that the [built-in](#built-in-acknowledgement-labels) `twin-persisted`
acknowledgement is requested, a received message (e.g. a [Ditto command](basic-signals-command.html)) will only be 
technically acknowledged to the connection channel if Ditto successfully applied and persisted the command.
---
**Note** These requested acknowledgements will be appended after payload mapping is applied. This means that in case you decided to split your message into multiple messages, all of these messages will request the same acknowledgements.
If this is not what you want to achieve, have a look at [how to add acknowledgement requests during payload mapping](#requesting-via-ditto-protocol-message-in-payload-mapping).
---

#### Requesting via Ditto Protocol message in payload mapping

During inbound payload mapping you create one ore more Ditto Protocol messages. If you configured your connection source to add requested acknowledgements to your commands, this will cause all produced messages to request the same acknowledges. If you however want to add requested acknowledges only to some of those created messages you need to set the `"requested-acks"` header, as described in [Requesting via Ditto Protocol message](#requesting-via-ditto-protocol-message) section, during payload mapping for those commands, you like to request an acknowledgement.

## Issuing acknowledgements

In order to issue a single acknowledgement when requested by a published event, an 
[Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message has to be built and sent 
back, using the same `"correlation-id"` in the [protocol headers](protocol-specification.html#headers) as the event
contained.

### Issuing acknowledgements via WebSocket

Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over the  
established WebSocket in response to an event which contained an `"requested-acks"` header.

### Issuing acknowledgements via connections

Requested acknowledgements for Ditto managed [connection targets](basic-connections.html#targets) can be issued in 2 
ways: 

* specifically for each published message/event as a response to the event by sending a
  [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) back via a source of the connection
* by configuring the managed connection target to automatically
  [issue acknowledgements for all successfully published messages](basic-connections.html#target-issue-acknowledgement-label)

#### Issuing via sending Ditto Protocol acknowledgement message

Create and send the [Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message over a  
source of the connection in response to an event which contained an `"requested-acks"` header.

#### Issuing via connection target configuration

[Connection targets](basic-connections.html#targets) can be configured to 
[issue certain acknowledgements automatically](basic-connections.html#target-issue-acknowledgement-label)
for each published message of the underlying physical connection (e.g. to a message broker).

This can be used in order to automatically issue technical acknowledgements once an [event](basic-signals-event.html)
was published to an HTTP endpoint or into a message broker. When this target guarantees having processed the event
via its protocol (for HTTP for example when a status code `2xx` is returned), a successful acknowledgement is created
and returned to the requester.


## Quality of Service

By default, all messages/commands processed by Ditto are processed in an "at most once" (or QoS 0) semantic. 
For many of the use cases in the IoT that is sufficient, e.g. when processing telemetry data gathered by a sensor. 
If one sensor value is not applied to the digital twin there will soon follow the next sensor reading, and the twin will 
be eventually up to date again.

However, there are IoT use cases where it is of upmost importance that a message is processed "at least once" (or QoS 1), 
e.g. in order to guarantee that it was persisted in the digital twin or that an [event](basic-signals-event.html) consumer 
connected to Ditto did successfully receive a [notification](basic-changenotifications.html) which resulted from a 
[command](basic-signals-command.html) which Ditto received and processed.

The "acknowledgements" concept documented on this page provides means that messages (e.g. commands) consumed and 
processed by Ditto are treated with an "at least once" (or QoS 1) semantic.

[Create/modify commands](protocol-specification-things-create-or-modify.html) will technically be acknowledged on the 
sent channel (e.g. HTTP or WebSocket or any [connection type](basic-connections.html#connection-types)) 
when it was a success.<br/>
When it could not be applied successfully, the command will be negatively acknowledged which will reflect in the 
[status of the acknowledgement](protocol-specification-acks.html#combined-status-code). 

Based on the used channel, the acknowledgement will be translated to the capabilities of the channel, e.g. for HTTP
an HTTP response will be sent with the outcome as HTTP status (`2xx` for a successful acknowledgement and `4xx` for a 
non successful one) together with additional details as HTTP response.

### QoS until persisted in Ditto

In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in a 
successful update of twin in Ditto's managed database, [request the acknowledgement](#requesting-acknowledgements) for
the [built-in "twin-persisted"](#built-in-acknowledgement-labels) acknowledgement label.

### QoS until an event was published

In order to ensure that a [create/modify command](protocol-specification-things-create-or-modify.html) resulted in an 
event which was consumed by an application integrating with Ditto, 
[request the acknowledgement](#requesting-acknowledgements) for a [custom acknowledgement label](#custom-acknowledgement-labels).
