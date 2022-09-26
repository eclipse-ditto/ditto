---
title: Protocol specification
keywords: action, channel, criterion, digital twin, envelope, payload, protocol, specification, twin
tags: [protocol]
permalink: protocol-specification.html
---

In order to comply with the Ditto Protocol, a protocol message must consist of

* a Ditto Protocol envelope (JSON) and
* a Ditto Protocol payload (JSON).


## Ditto Protocol

The communication protocol envelope is implicitly defined by the underlying messaging system 
(e.g. [WebSocket](httpapi-protocol-bindings-websocket.html)) used to transport/serialize the messages over the wire.
Please refer to the respective [communication protocol binding](protocol-bindings.html) for information how to encode
the data in a protocol specific way.

### Content-type

Ditto Protocol messages are identified by the 
[IANA registered](https://www.iana.org/assignments/media-types/application/vnd.eclipse.ditto+json) "Content-Type":

```
application/vnd.eclipse.ditto+json
```

### Ditto Protocol envelope {#dittoProtocolEnvelope}

The Ditto Protocol envelope describes the content of the message (the affected thing entity, a message type, protocol
version etc.) and allows the message to be routed by intermediary nodes to its final destination, without parsing the
actual payload.

The Protocol envelope is formatted as JSON object (`content-type=application/json`) and must correspond to the 
following JSON schema:

{% include docson.html schema="jsonschema/protocol-envelope.json" %}


### Ditto Protocol payload (JSON) {#dittoProtocolPayload}

The Ditto model payload contains the application data, e.g. an updated sensor value or a Thing in JSON representation.
 
* See [specification for Things](protocol-specification-things.html)
* See [specification for Policies](protocol-specification-policies.html) 
* See [specification for Connections](protocol-specification-connections.html) 


### Ditto Protocol response {#dittoProtocolResponse}

When sending a **command**, a **response** can be requested.
A response can indicate either the success or the failure of the command. 
The Ditto response for a successful command has the following format:

{% include docson.html schema="jsonschema/protocol-response.json" %}

In case the execution failed, an error response with information about the error is sent:

{% include docson.html schema="jsonschema/protocol-error_response.json" %}

The following sections specify in detail, which fields of the protocol envelope, payload, and response use to contain
which information.


## Topic

Protocol messages contain a [topic](protocol-specification-topic.html), which is used for
* addressing an entity,
* defining the `channel` (*twin* vs. *live*) and
* specifying what the intention of the Protocol message is.

## Headers

Protocol messages contain headers as JSON object with arbitrary content.
The keys of the JSON object are the header names; the values are the header values.

The header names are *case-insensitive* and *case-preserving* in the following sense:
- **Case-insensitive**: Capitalization of header names does not affect evaluation of the headers by Ditto;
setting `correlation-id` or `CORRELATION-ID` has the same effect. If 2 headers differing only in capitalization
are set, Ditto's behavior is **not defined**.
- **Case-preserving**: Capitalization of headers by the sender of a Ditto protocol message is visible to the receiver.
An exception to case-preservation are the headers of HTTP requests and responses,
since they are themselves not case-sensitive.

There are some pre-defined headers, which have a special meaning for Ditto:

| Header Key                      | Description                                                                                                                                  | Possible values                                                                                                           |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `content-type`                  | The content-type which describes the [value](#value) of Ditto Protocol messages.                                                             | `String`                                                                                                                  |
| `correlation-id`                | Used for correlating protocol messages (e.g. a **command** would have the same correlation-id as the sent back **response** message).        | `String`                                                                                                                  |
| `ditto-originator`              | Contains the first authorization subject of the command that caused the sending of this message. Set by Ditto.                               | `String`                                                                                                                  |
| `if-match`                      | Has the same semantics as defined for the [HTTP API](httpapi-concepts.html#conditional-requests).                                            | `String`                                                                                                                  |
| `if-none-match`                 | Has the same semantics as defined for the [HTTP API](httpapi-concepts.html#conditional-requests).                                            | `String`                                                                                                                  |
| `response-required`             | Configures for a **command** whether or not a **response** should be sent back.                                                              | `Boolean` - default: `true`                                                                                               |
| `requested-acks`                | Defines which [acknowledgements](basic-acknowledgements.html) are requested for a command processed by Ditto.                                | `JsonArray` of `String` - default: `["twin-persisted"]`                                                                   |
| `ditto-weak-ack`                | Marks [weak acknowledgements](basic-acknowledgements.html) issued by Ditto.                                                                  | `Boolean` - default: `false`                                                                                              |
| `timeout`                       | Defines how long the Ditto server should wait, e.g. applied when waiting for requested acknowledgements.                                     | `String` - e.g.: `42s` or `250ms` or `1m` - default: `60s`                                                                |
| `version`                       | Determines in which schema version the `payload` should be interpreted.                                                                      | `Number` - currently possible: \[2\] - default: `2`                                                                       |
| `put-metadata`                  | Determines which Metadata information is stored in the thing.                                                                                | `JsonArray` of `JsonObject`s containing [metadata](basic-metadata.html) to apply.                                         |
| `condition`                     | The condition to evaluate before applying the request.                                                                                       | `String` containing [condition](basic-conditional-requests.html) to apply.                                                |
| `live-channel-condition`        | The condition to evaluate before retrieving thing data from the device.                                                                      | `String` containing [live channel condition](basic-conditional-requests.html#live-channel-condition) to apply.            |
| `live-channel-timeout-strategy` | The strategy to apply when a [live](protocol-twinlive.html#live) command was not answered by the actual device within the defined `timeout`. | `fail`: let the request fail with a 408 timeout error - `use-twin`: fall back to the twin, retrieving the persisted data. |
| `at-historical-revision`        | The historical revision to retrieve an entity at, using the [history capabilities](basic-history.html).                                      | `Number` - a long value of the revision to retrieve.                                                                      |
| `at-historical-timestamp`       | The historical timestamp in ISO-8601 format to retrieve an entity at, using the [history capabilities](basic-history.html).                  | `String` containing an ISO-8601 formatted timestamp.                                                                      |
| `historical-headers`            | Contains the historical header when using `at-historical-*` headers to retrieve an entity at a certain history point.                        | `JsonObject` of the headers which were configured to be persisted as historical headers.                                  |

Custom headers of messages through the [live channel](protocol-twinlive.html#live) are delivered verbatim. When naming 
custom headers, it is best to attach a prefix specific to your application, that does not conflict with Ditto or
HTTP protocol, for example the prefix `ditto-*`.
* [Permanent HTTP headers](https://www.iana.org/assignments/message-headers/message-headers.xml) are to be avoided.
* Ditto uses the following headers internally. If these headers are set in a Protocol message, they will be ignored 
  and will not be delivered.
  ```
  channel
  ditto-*
  raw-request-url
  read-subjects
  subject
  timeout-access
  ```

The interaction between the headers `response-required`, `requested-acks` and `timeout` is documented
[here](basic-acknowledgements.html#interaction-between-headers).


## Path

Contains a JSON pointer of where to apply the [value](#value) of the protocol message.
May also be `/` when the value contains a replacement for the complete addressed entity (e.g. a complete
[Thing](basic-thing.html) JSON).

## Value

The JSON value to apply at the specified path.

## Status

Some protocol messages (for example **responses**) contain an HTTP status code which is stored in this field.

## Extra

When using [signal enrichment](basic-enrichment.html), in order to ask for `extraFields` to be included, the
Ditto Protocol message contains a field `extra` containing a JSON object with the selected extra fields.
