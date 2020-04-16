---
title: Acknowledgements
keywords: acks, acknowledgement, acknowledgements, qos, at least once
tags: [model]
permalink: basic-acknowledgements.html
---

{% include callout.html content="Available since Ditto **1.1.0**" type="primary" %}

Acknowledgements are a concept in Ditto used to signal that an [event](basic-signals-event.html) was successfully 
received/processed by either an internal Ditto functionality or an external subscriber of that event.

They can be seen as (potentially multiple) responses to a single event (e.g. after a Thing was modified). 

Acknowledgements can be used in order to solve the following cases:
* postpone a response to an API request (e.g. block an HTTP request) until one or more specific actions were performed in 
  Ditto (e.g. a modification was successfully persisted).
* postpone a response until external connected to Ditto report that they successfully processed an 
  [event](basic-signals-event.html) which e.g. resulted by a persistence change of Ditto. 
* *Outlook* (to come in Ditto 1.2.0): provide a QoS (quality of service) guarantee of "at least one" when processing 
  messages in an end-2-end manner by e.g. technically acknowledging/settling a processed message from a message broker 
  (e.g. [AMQP 1.0](connectivity-protocol-bindings-amqp10.html) or [MQTT](connectivity-protocol-bindings-mqtt.html)) only
  then when it was successfully applied to Ditto and potentially also to 3rd parties.


## Acknowledgement requests

For each Ditto API invocation *modifying the state of a twin* with a [command](basic-signals-command.html) it is 
possible to define that certain "acknowledgements" are *requested*.<br/>
This means that the Ditto collects all of those requested acknowledgements until the message is handled as successfully 
processed, applying a specified timeout interval.

Acknowledgement requests are formulated as protocol specific header fields of commands, more on that 
[later](#requesting-acknowledgements).

Acknowledgement requests will be included in the `"requested-acks"` header for by Ditto emitted 
[events](basic-signals-event.html).<br/>
The exception to that rule is that Ditto's [built-in acknowledgements](#built-in-acknowledgement-labels) will be 
excluded, so only custom ones which an external party can acknowledge are retained.


## Acknowledgement labels

Acknowledgement labels identify the acknowledgement. Some labels are already provided by Ditto and may not
be used when sending back a custom acknowledgement.

### Built-in acknowledgement labels

Ditto provides built-in acknowledgements that are automatically issued on certain actions in the Ditto cluster:
* **twin-persisted**: An acknowledgement with this label is issued when the result of a modifying command lead 
  to that the twin was successfully updated in Ditto's persistence.

### Custom acknowledgement labels

In addition to the [built-in](#built-in-acknowledgement-labels) acknowledgement requests, custom ones may contained when 
receiving an [event](basic-signals-event.html). If an event subscriber detects that an acknowledgement was requested 
(via the `"requested-acks"` header) which the subscriber feels responsible for handling, it can do so by 
[issuing an acknowledgement](#issuing-acknowledgements).


## Acknowledgements

A single Acknowledgement contains the following information:
* Acknowledgement label (one of the requested labels of the [ack requests](#acknowledgement-requests) of e.g. an event)
* Header fields
    * mandatory: **correlation-id** the same correlation-id as the one of the event which requested the acknowledgement
    * optional: additional header fields
* Status code (HTTP status code semantic) defining whether an acknowledgement was successful or not
* Optional payload as JSON

The [Ditto Protocol specification](protocol-specification-acks.html) describes in detail what is contained.<br/>
An example of how acknowledgements in Ditto Protocol look like can be 
[found here](protocol-examples.html#acknowledgements-acks).


## Requesting acknowledgements

With every API call *modifying the state of a twin* there is the option to provide requested acknowledgements.  

### Via HTTP

Either specify the following HTTP header fields:
* **requested-acks**: a comma separated list of [acknowledgement labels](#acknowledgement-labels)<br/>
  Example: *requested-acks*: twin-persisted,my-custom-ack
* timeout: an optional timeout of how long the HTTP request should wait for acknowledgements and block, default: `60s`<br/>
  Examples: *timeout*: 42s, *timeout*: 250ms

Or specify the header fields as query parameters to the HTTP params, e.g.:
```
PUT /api/2/things?requested-acks=twin-persisted,my-custom-ack&timeout=42s
```

The response of an HTTP request which requested several acknowledgements will differ from the same HTTP request without
requesting acknowledgements.

Example response when 2 acknowledgements were requested:
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
  "my-custom": {
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

### Via WebSocket

Put the `"requested-acks"` (as JsonArray of strings) and the `"timeout"` headers in the 
[Ditto Protocol headers](protocol-specification.html#headers) section of Ditto Protocol JSON.

The response will be an [acknowledgements](protocol-specification-acks.html) message.

### Via Connections

For messages received by connections via AMQP, MQTT, etc. put the `"requested-acks"` (as JsonArray of strings) and the 
`"timeout"` headers in the [Ditto Protocol headers](protocol-specification.html#headers) section of Ditto Protocol JSON.

The response will be an (aggregating) [acknowledgements](protocol-specification-acks.html#acknowledgements-aggregating) 
message.


## Issuing acknowledgements

In order to issue single acknowledgements when they were requested by a published event, an 
[Ditto Protocol acknowledgement](protocol-specification-acks.html#acknowledgement) message has to be built and sent 
back, using the same `"correlation-id"` in the [protocol headers](protocol-specification.html#headers) as the event
contained.
