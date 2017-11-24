---
title: WebSocket protocol binding
keywords: binding, protocol, websocket
tags: [protocol]
permalink: protocol-bindings-websocket.html
---

The Ditto Protocol message can be sent *as is* as [WebSocket](https://tools.ietf.org/html/rfc6455) message.
The Ditto Protocol JSON must be sent as `UTF-8` encoded String payload.

## Ditto WebSocket endpoint

The WebSocket endpoint is accessible at this URL:

```
ws://localhost:8080/ws/1
```

## Authenticate

A user who connects to the WebSocket endpoint can be authenticated by using

* HTTP BASIC Authentication by providing a username and the password of a user managed within nginx or
* a JSON Web Token (JWT) issued by an OpenID connect provider.

See [Authenticate](basic-auth.html) for more details.


## Send commands and get responses
   
When sending a command via WebSocket you will receive a corresponding response (the response can be related to the 
request by the `correlation-id` header).
The response indicates the success or the failure of the command and, depending on the command type, contains the result
payload.

Please find examples of commands and their response pattern at [Protocol examples.](protocol-examples.html)


## Request events/change notifications

Additionally to the response, which Ditto addresses directly to the instance which was sending the command, an event 
is generated. 
This will be delivered to all other clients with read permissions for the respective thing, feature change, etc. 
Thus in order to receive such events (triggered by commands of a 3rd-party) you will need to send a `START-SEND-EVENTS`
WebSocket message literally.

To close the events subscription, send a `STOP-SEND-EVENTS` message.
