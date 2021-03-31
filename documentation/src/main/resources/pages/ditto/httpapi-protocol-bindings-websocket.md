---
title: WebSocket protocol binding
keywords: binding, protocol, websocket, http
tags: [protocol, http, rql]
permalink: httpapi-protocol-bindings-websocket.html
---

[Ditto Protocol](protocol-overview.html) messages can be sent *as is* as [WebSocket](https://tools.ietf.org/html/rfc6455) 
message. The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.


## WebSocket features

The WebSocket provides an alternative to the [HTTP API](httpapi-overview.html) in order to manage your digital twins.

The benefits of the WebSocket compared to HTTP are multiple ones:

* a single connection (socket like) is established and for commands to digital twins no further HTTP overhead 
  (e.g. HTTP headers, HTTP connection establishment) is produced which means you can get more commands/seconds 
  through the WebSocket compared to the HTTP endpoint
* as the WebSocket is a duplex connection, [change notifications](basic-changenotifications.html) can be sent via the
  WebSocket for changes to entities done in Ditto
* additionally, [messages](basic-messages.html) and [live commands/events](protocol-twinlive.html) can be exchanged 
  (sending and receiving) via multiple connected WebSocket sessions

Please keep in mind that every web WebSocket connection will receive all events and messages it is allowed to receive 
depending on the provided authentication.<br/>
There is no round-robin dispatching for WebSockets using the same authentication.

{% include warning.html content="This means that WebSockets are not meant to be used for scenarios where horizontal 
    scaling should be applied. 
    For those scenarios we suggest using the [Connectivity API](connectivity-overview.html)." 
%}

### Send commands and get responses
   
When sending a command via WebSocket you will receive a corresponding response (the response can be related to the 
request by the `correlation-id` header). <br/>
The response indicates the success or the failure of the command and, depending on the command type, contains the 
result payload.

Please find examples of commands, and their response pattern at [Protocol examples](protocol-examples.html).

### Request receiving events/change notifications

In addition to the response, which Ditto addresses directly to the instance which was sending the command, an event 
is generated. <br/>
This will be delivered to all other clients with read permissions for the respective thing, feature change, etc. 

See [request events](#request-events) for subscribing/unsubscribing for receiving change notifications.

### Request receiving messages

[Messages](basic-messages.html) can be sent both via the [HTTP API](httpapi-overview.html) and the WebSocket. 
Receiving messages and answering to them however can only be done via the WebSocket.

See [request messages](#request-messages) for subscribing/unsubscribing for receiving messages.

### Request receiving live commands + events

In order to receive [live commands and events](protocol-twinlive.html), the WebSocket API can be used. 
The Ditto Protocol messages are the same as for the "twin" channel, only with *live* as channel in the 
[topic](protocol-specification-topic.html).

See [request live commands](#request-live-commands) and [request live events](#request-live-events) for 
subscribing/unsubscribing for receiving live commands and events.


## WebSocket endpoint

The WebSocket endpoint is accessible at the following URL:

```
ws://localhost:8080/ws/2
```

### Authentication

A user who connects to the WebSocket endpoint can be authenticated by using

* HTTP BASIC Authentication by providing a username and the password of a user managed within nginx or
* a JSON Web Token (JWT) issued by an OpenID connect provider.

See [Authenticate](basic-auth.html) for more details.


## WebSocket protocol format

As defined in the [Protocol specification](protocol-specification.html) a Ditto Protocol message consists of different
information. This information is combined into a single JSON message for the WebSocket endpoint:

* [topic](protocol-specification.html#topic): JSON string with key `topic`
* [headers](protocol-specification.html#headers): JSON object with key `headers`
* [path](protocol-specification.html#path): JSON string with key `path`
* [value](protocol-specification.html#value): JSON value (e.g. JSON object, string, array, ...) with key `value`
* [status](protocol-specification.html#status) (for responses): JSON number with key `status`

The schema for Ditto Protocol message via WebSocket:

```json
{
  "topic": "<the topic>",
  "headers": {
    "correlation-id": "<a correlation-id>",
    "a-header": "<header value>"
  },
  "path": "<the path>",
  "value": {
    
  }
}
```


## WebSocket binding specific messages

The WebSocket binding defines several specific messages which are not defined in the Ditto Protocol specification.

Those are also not defined as JSON messages, but as plain text messages. All of those declare a demand for some kind
of information from the backend to be pushed into the WebSocket session.

### Request events

In order to subscribe for [events/change notifications](basic-changenotifications.html) for entities (e.g. Things), 
following text message has to be sent to the backend: `START-SEND-EVENTS`

From then on the WebSocket session will receive all change notifications it is entitled to see.

### Request messages

In order to subscribe for [messages](basic-messages.html) which can be sent from a WebSocket session to another 
WebSocket session or from the [HTTP API](httpapi-overview.html) to a WebSocket session, the following text message has 
to be sent to the backend: `START-SEND-MESSAGES`

From then on the WebSocket session will receive all messages it is entitled to see.

### Request live commands

In order to subscribe for [live commands](protocol-twinlive.html) which can be sent from a WebSocket session to another 
WebSocket session, the following text message has to be sent to the backend: `START-SEND-LIVE-COMMANDS`

From then on the WebSocket session will receive all live commands it is entitled to see.

### Request live events

In order to subscribe for [live events](protocol-twinlive.html) which can be sent from a WebSocket session to another 
WebSocket session, the following text message has to be sent to the backend: `START-SEND-LIVE-EVENTS`

From then on the WebSocket session will receive all live events it is entitled to see.

### Request policy announcements

In order to subscribe for [Policy announcements](protocol-specification-policies-announcement.html) which can be 
published to a WebSocket session, the following text message has to be sent to the backend: 
`START-SEND-POLICY-ANNOUNCEMENTS`

From then on the WebSocket session will receive all announcements related to policies related to the authenticated 
subjects of the websocket session.

### Overview

The following table shows which WebSocket protocol message are supported:

| Description | Request message | Response message |
|-------------|-----------------|------------------|
| Refresh JWT based authentication |  `JWT-TOKEN` | `-` |
| Subscribe for [Thing events/change notifications](basic-changenotifications.html) | `START-SEND-EVENTS` | `START-SEND-EVENTS:ACK` |
| Stop receiving Thing change notifications | `STOP-SEND-EVENTS` | `STOP-SEND-EVENTS:ACK` |
| Subscribe for [Thing messages](basic-messages.html) | `START-SEND-MESSAGES` | `START-SEND-MESSAGES:ACK` |
| Stop receiving Thing messages | `STOP-SEND-MESSAGES` | `STOP-SEND-MESSAGES:ACK` |
| Subscribe for [Thing live commands](protocol-twinlive.html) | `START-SEND-LIVE-COMMANDS` | `START-SEND-LIVE-COMMANDS:ACK` |
| Stop receiving Thing live commands | `STOP-SEND-LIVE-COMMANDS` | `STOP-SEND-LIVE-COMMANDS:ACK` |
| Subscribe for [Thing live events](protocol-twinlive.html) | `START-SEND-LIVE-EVENTS` | `START-SEND-LIVE-EVENTS:ACK` |
| Stop receiving Thing live commands | `STOP-SEND-LIVE-EVENTS` | `STOP-SEND-LIVE-EVENTS:ACK` |
| Subscribe for [Policy announcements](protocol-specification-policies-announcement.html) | `START-SEND-POLICY-ANNOUNCEMENTS` | `START-SEND-POLICY-ANNOUNCEMENTS:ACK` |
| Stop receiving Policy announcements | `STOP-SEND-POLICY-ANNOUNCEMENTS` | `STOP-SEND-POLICY-ANNOUNCEMENTS:ACK` |

### Authentication

Ditto closes Websocket connections when the JWT provided with the initial connect expires. To keep the connection 
open,  one can send a valid JWT via `JWT-TOKEN` protocol message. The `sub` of the new token must match the one from 
the initial connect, otherwise Ditto will close the connection.

Ditto expects the message with the JWT as a base64 encoded string provided with the paramter `?jwtToken=<token>`, e.g.:
```
JWT-TOKEN?jwtToken=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

### Enrichment

When extra fields should be added to outgoing messages on the WebSocket channel, an `extraFields` parameter can be 
added to the request message. This is supported for all request messages:

| Description | Request message | [Enrich by extra fields](basic-enrichment.html) |
|-------------|-----------------|------------------|
| Subscribe for [Thing events/change notifications](basic-changenotifications.html) | `START-SEND-EVENTS` | &#10004; |
| Subscribe for [Thing messages](basic-messages.html) | `START-SEND-MESSAGES` | &#10004; |
| Subscribe for [Thing live commands](protocol-twinlive.html) | `START-SEND-LIVE-COMMANDS` | &#10004; |
| Subscribe for [Thing live events](protocol-twinlive.html) | `START-SEND-LIVE-EVENTS` | &#10004; |
| Subscribe for [Policy announcements](protocol-specification-policies-announcement.html) | `START-SEND-POLICY-ANNOUNCEMENTS` | &#10060; |

Analog to the [filtering](#filtering) the parameter is defined like an HTTP query parameter, e.g.:
```
START-SEND-EVENTS?extraFields=attributes/counter,features/ConnectionStatus
START-SEND-MESSAGES?extraFields=attributes
```

### Filtering

In order to only consume specific events like described in [change notifications](basic-changenotifications.html), the
following parameters can additionally be provided when sending the WebSocket protocol messages:

| Description | Request message | [Filter by namespaces](basic-changenotifications.html#by-namespaces) | [Filter by RQL expression](basic-changenotifications.html#by-rql-expression) |
|-------------|-----------------|------------------|-----------|
| Subscribe for [Thing events/change notifications](basic-changenotifications.html) | `START-SEND-EVENTS` | &#10004; | &#10004; |
| Subscribe for [Thing messages](basic-messages.html) | `START-SEND-MESSAGES` | &#10004; | &#10060; |
| Subscribe for [Thing live commands](protocol-twinlive.html) | `START-SEND-LIVE-COMMANDS` | &#10004; | &#10060; |
| Subscribe for [Thing live events](protocol-twinlive.html) | `START-SEND-LIVE-EVENTS` | &#10004; | &#10004; |
| Subscribe for [Policy announcements](protocol-specification-policies-announcement.html) | `START-SEND-POLICY-ANNOUNCEMENTS` | &#10004; | &#10060; |

The parameters are specified similar to HTTP query parameters, the first one separated with a `?` and all following ones
with `&`.  You have to URL encode the filter values before using them in a configuration.

For example this way the WebSocket session would register for all events in the namespace `org.eclipse.ditto` and which
would match an attribute "counter" to be greater than 42:
```
START-SEND-EVENTS?namespaces=org.eclipse.ditto&filter=gt(attributes/counter,42)
```

The filtering may be also used in combination with an [enrichment](#enrichment), e.g.:
```
START-SEND-EVENTS?extraFields=attributes&filter=gt(attributes/counter,42)
```
