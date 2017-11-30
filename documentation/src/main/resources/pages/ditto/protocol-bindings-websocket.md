---
title: WebSocket protocol binding
keywords: binding, protocol, websocket
tags: [protocol]
permalink: protocol-bindings-websocket.html
---

The Ditto Protocol message can be sent *as is* as [WebSocket](https://tools.ietf.org/html/rfc6455) message.
The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.


## WebSocket features

The WebSocket provides an alternative to the [HTTP API](httpapi-overview.html) in order to manage your Digital Twins.

The benefits of the WebSocket compared to HTTP are multiple ones:
* a single connection (socket like) is established and for commands to Digital Twins no further HTTP overhead 
  (e.g. HTTP headers, HTTP connection establishment) is produced which means you can get more commands/seconds 
  through the WebSocket compared to the HTTP endpoint
* as the WebSocket is a duplex connection, [change notifications](basic-changenotifications.html) can be sent via the
  WebSocket for changes to entities done in Ditto
* additionally, [messages](basic-messages.html) and [live commands/events](protocol-twinlive.html) can be exchanged 
  (sending and receiving) via multiple connected WebSocket sessions 

### Send commands and get responses
   
When sending a command via WebSocket you will receive a corresponding response (the response can be related to the 
request by the `correlation-id` header). <br/>
The response indicates the success or the failure of the command and, depending on the command type, contains the result
payload.

Please find examples of commands and their response pattern at [Protocol examples](protocol-examples.html).

### Request receiving events/change notifications

Additionally to the response, which Ditto addresses directly to the instance which was sending the command, an event 
is generated. <br/>
This will be delivered to all other clients with read permissions for the respective thing, feature change, etc. 

See [request events](#request-events) for subscribing/unsubscribing for receiving change notifications.

### Request receiving messages

[Messages](basic-messages.html) can be sent both via the [HTTP API](httpapi-overview.html) and the WebSocket. Receiving
messages and answering to them however can only be done via the WebSocket.

See [request messages](#request-messages) for subscribing/unsubscribing for receiving messages.

### Request receiving live commands + events

In order to receive [live commands and events](protocol-twinlive.html), the WebSocket API can be used. The Ditto Protocol
messages are the same as for the "twin" channel, only with *live* as channel in the 
[topic](protocol-specification-topic.html).

See [request live commands](#request-live-commands) and [request live events](#request-live-events) for 
subscribing/unsubscribing for receiving live commands and events.


## WebSocket endpoint

The WebSocket endpoint is accessible at these URLs (depdening on which API version to use):

```
ws://localhost:8080/ws/1
ws://localhost:8080/ws/2
```

### Authentication

A user who connects to the WebSocket endpoint can be authenticated by using

* HTTP BASIC Authentication by providing a username and the password of a user managed within nginx or
* a JSON Web Token (JWT) issued by an OpenID connect provider.

See [Authenticate](basic-auth.html) for more details.


## WebSocket protocol format

As in the [Protocol specification](protocol-specification.html) defined a Ditto Protocol message consists of different
information. These information are combined into a single JSON message for the WebSocket endpoint:
* [topic](protocol-specification.html#topic): JSON string with key `topic`
* [headers](protocol-specification.html#headers): JSON object with key `headers`
* [path](protocol-specification.html#path): JSON string with key `path`
* [value](protocol-specification.html#value): JSON value (e.g. JSON object, string, array, ...) with key `value`
* [status](protocol-specification.html#status) (for responses): JSON number with key `status`

The schema in for Ditto Protocol message via WebSocket:

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
following text message has to be sent to the backend:

```
START-SEND-EVENTS
``` 

This message is acknowledged by Ditto by sending back:

```
START-SEND-EVENTS:ACK
``` 

From this point on the WebSocket session will receive all change notifications it is entitled to see.

In order to stop receiving change notifications, following text message has to be sent to the backend:
                                               
```
STOP-SEND-EVENTS
``` 

This message is acknowledged by Ditto by sending back:

```
STOP-SEND-EVENTS:ACK
``` 

### Request messages

In order to subscribe for [messages](basic-messages.html) which can be sent from WebSocket session to other WebSocket 
session or from the [HTTP API](httpapi-overview.html) to a WebSocket session, following text message has to be sent to 
the backend:

```
START-SEND-MESSAGES
``` 

This message is acknowledged by Ditto by sending back:

```
START-SEND-MESSAGES:ACK
``` 

From this point on the WebSocket session will receive all messages it is entitled to see.

In order to stop receiving messages, following text message has to be sent to the backend:
                                               
```
STOP-SEND-MESSAGES
``` 

This message is acknowledged by Ditto by sending back:

```
STOP-SEND-MESSAGES:ACK
``` 

### Request live commands

In order to subscribe for [live commands](protocol-twinlive.html) which can be sent from WebSocket session to other 
WebSocket session, following text message has to be sent to the backend:

```
START-SEND-LIVE-COMMANDS
``` 

This message is acknowledged by Ditto by sending back:

```
START-SEND-LIVE-COMMANDS:ACK
``` 

From this point on the WebSocket session will receive all live commands it is entitled to see.

In order to stop receiving live commands, following text message has to be sent to the backend:
                                               
```
STOP-SEND-LIVE-COMMANDS
``` 

This message is acknowledged by Ditto by sending back:

```
STOP-SEND-LIVE-COMMANDS:ACK
``` 

### Request live events

In order to subscribe for [live events](protocol-twinlive.html) which can be sent from WebSocket session to other 
WebSocket session, following text message has to be sent to the backend:

```
START-SEND-LIVE-EVENTS
``` 

This message is acknowledged by Ditto by sending back:

```
START-SEND-LIVE-EVENTS:ACK
``` 

From this point on the WebSocket session will receive all live events it is entitled to see.

In order to stop receiving live events, following text message has to be sent to the backend:
                                               
```
STOP-SEND-LIVE-EVENTS
``` 

This message is acknowledged by Ditto by sending back:

```
STOP-SEND-LIVE-EVENTS:ACK
``` 
