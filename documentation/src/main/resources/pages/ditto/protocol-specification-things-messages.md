---
title: Things - Messages protocol specification
keywords: protocol, specification, messages, thing
tags: [protocol]
permalink: protocol-specification-things-messages.html
---

Messages within the Ditto Protocol allow sending, receiving and responding to 
Messages. They contain an arbitrary *payload*, so you can choose what content 
fits your solution best. If you want to learn more about the basic concepts of the Messages 
functionality, please have a look at the [Messages page](basic-messages.html).

{% include tip.html content="If you only need to send Messages, but don't need to receive
 or respond, you could also use the [HTTP Messages API](httpapi-messages.html)" %} 

 
## Messages protocol

The Messages protocol is part of the [Ditto Protocol](protocol-specification.html) and therefore 
conforms to its specification. This section describes how the protocol envelope can be filled
for sending Messages. If you want to jump right into using the API, head over to the
next section that describes how to [use the messages API](#using-the-messages-api).

There are three protocol parameters that have special meaning for Messages:
* `topic` : *{namespace}*/*{thingId}*/things/live/messages/*{messageSubject}*
* `path` : *{addressedPartOfThing}*/*{mailbox}*/messages/*{messageSubject}*
* `headers` : The headers for Messages must include *content-type*

<br/>

The `topic` definition for Messages needs the *namespace* and *thingId*
of the Thing you're sending Messages to. The *messageSubject* describes the Message
and must conform to the *path* as described in [RFC-3986](https://tools.ietf.org/html/rfc3986).
Examples for valid topics are:
* `org.eclipse.ditto/smartcoffee/things/live/messages/ask/question`
* `com.example/smarthome/things/live/messages/turnoff`

<br/>

The `path` also contains the *messageSubject* that describes the Message.
*mailbox* can be *inbox* (Message is sent to a Thing) or *outbox* (Message is
sent from a Thing). The *addressedPartOfThing* tells which part of the Thing
is addressed. `/features/water-tank` would address the water-tank Feature of 
the Thing, while `` would address the whole Thing. Valid paths are e.g.:
* `/inbox/messages/ask/question`
* `/features/lights/inbox/messages/turnoff`
* `/features/smokedetector/outbox/messages/smokedetected`

<br>

In the `headers` of the envelope the Messages API requires:
* `content-type` : The type of the payload you are sending, e.g. *text/plain*

## Using the Messages API

The following parts contain examples that will show you how to leverage the Messages API. 
In the examples we will use some kind of smart coffee machine with the id *smartcoffee*.

{% include note.html content="We encourage you to play around with the examples. You can use
the WebSocket binding to do so. Make sure the Thing you are sending Messages to is existing and has
 the correct access rights." %}

### Sending a Message to a Thing

When sending a Message to a Thing, we send it to the inbox of the receiving entity.<br/>
What follows is a simple example Message that asks our Thing *smartcoffee* how it is feeling today:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-message"
    },
    "path": "/inbox/messages/ask",
    "value": "Hey, how are you?"
}
```

Notice that our `topic` adheres to the [Ditto Protocol topic definition](protocol-specification-topic.html)
with *messages* as the criterion, *live* as channel, and the message-subject as action.

We encourage you to always send a `correlation-id` with your Messages.
This is especially important, since the WebSocket Ditto Protocol binding
sends messages in a fire-and-forget manner. Ditto wouldn't know who to 
respond to if there was no `correlation-id` set in the Message.

{% include tip.html content="If you want to receive the response to 
a Message, make sure to always send a `correlation-id` with it." %}

The response we would get from our coffee machine could look something like this:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "correlation-id": "a-unique-string-for-this-message",
        "auth-subjects": ["ditto", "nginx:ditto"],
        "content-type": "text/plain",
        "version": 1,
    },
    "path": "/inbox/messages/ask",
    "value": "I do not know, since i am only a coffee machine.",
    "status": 418
}
```

The answer of the coffee machine has the same `topic` and `correlation-id`
as the original message. As we can see, the response does not only contain a
`value` but also the `status` of the response
which is based on the [HTTP status codes](protocol-specification.html#status). 
Notice, that the `path` of the Message has changed from *inbox* to *outbox*, 
which means the Message was sent *from* the Thing.
Ditto automatically added some headers that we can ignore for now.

### Receiving a message

To be able to show how to receive Messages, we need to use one of the provided Ditto Protocol
bindings. We will use the WebSocket binding for now. With it, it is amazingly easy to
receive Messages sent *to* or *from* Things. You only need to fulfill these *three* simple requirements:

1. Having an open connection to the Ditto WebSocket
2. Having sent the [WebSocket binding specific message](httpapi-protocol-bindings-websocket.html#request-events) 
`START-SEND-MESSAGES` to the WebSocket to be able to retrieve Messages
3. You are allowed ([authorized](basic-auth.html)) to receive Messages 

If we have a user *ditto* that has `READ` permission on *smartcoffee*, we could receive Messages
for it using a local Ditto instance using simple JavaScript:

```javascript
// connect to the WebSocket
var websocket = new WebSocket('ws://ditto:ditto@localhost:8080/ws/1');
websocket.onmessage(function(message) {
    console.log('received message data: ' +  message.data);
});
websocket.onopen(function(ws) {
    // register for receiving messages
    ws.send('START-SEND-MESSAGES');
});
```

If we would send the Message described in [Sending a Message to a Thing](#sending-a-message-to-a-thing)
to the WebSocket, our JavaScript receiver would receive the following data:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "correlation-id": "demo-6qaal9l",
        "auth-subjects": ["ditto", "nginx:ditto"],
        "content-type": "text/plain",
        "version": 1
    },
    "path": "/inbox/messages/ask",
    "value": "Hey, how are you?"
}
```

The content is almost the same, except for automatically added headers that we can ignore.
In the next part you can learn how to respond to this Message.


### Responding to a Message

After [sending a Message to a Thing](#sending-a-message-to-a-thing) and 
[receiving the Message](#receiving-a-message), we are able to respond to the
Message we received. To do this, we can re-use the relevant Message contents 
and change the type from incoming to outgoing. Here is a simple JavaScript
function that shows how you could respond to a given Message. It takes
the original Message, the response payload and status code and returns the
response Message you can send using a Ditto Protocol binding.

```javascript
createTextResponse = function(originalMessage, payload, statusCode) {
    var topic = originalMessage.topic;
    var correlationId = originalMessage.headers["correlation-id"];
    var outboxPath = originalMessage.path.replace("inbox", "outbox");
    
    return {
      "topic": topic,
      "headers": {
          "correlation-id": correlationId,
          "content-type": "text/plain"
      },
      "path": outboxPath,
      "status": statusCode,
      "value": payload
    };
};
```

With this method you could create a simple text response, and send it
using e.g. the WebSocket binding. Since the original Message and the response
Message have the same `correlation-id`, the issuer would receive your response:
 
```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/ask",
    "headers": {
        "correlation-id": "demo-6qaal9l",
        "auth-subjects": ["ditto", "nginx:ditto"],
        "content-type": "text/plain",
        "version": 1
    },
    "path": "/inbox/messages/ask",
    "status": 418,
    "value": "I don't know since i am only a coffee machine"
}
```

### Talking to Features

When sending Messages to or from Features, almost everything stays the same as with
Things. The `path` in the JSON and an additional `feature-id` header are the only parts to change. 
A Message to a Feature could therefore have the following JSON:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/heatUp",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-message"
    },
    "path": "/features/water-tank/inbox/messages/heatUp",
    "value": "47"
}
```

### Sending and handling Claim Messages

Claim Messages are handled like standard Messages with the difference that the message subject is *claim* and they 
can only be sent to a Thing. As the purpose of claiming is to gain access to a Thing, you do not require `WRITE` 
permission to send a Claim Message to a Thing. This however means for a receiver that incoming Claim Messages have to be 
carefully verified before granting access to a Thing. 

A Claim Message to gain access to our smart coffee machine might look like this:

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/claim",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-claim-message"
    },
    "path": "/inbox/messages/claim",
    "value": "some-claiming-secret"
}
```

After verifying the Message, in particular the correctness of the payload, the smart coffee machine could grant access 
to the issuer by setting an additional permission and respond with a *status* of `200` or `204` (as you can see the 
`path` changed to *outbox* and the `direction` is now *from*, same as above for the Thing Message):

```json
{
    "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/claim",
    "headers": {
        "content-type": "text/plain",
        "correlation-id": "a-unique-string-for-this-claim-message"
    },
    "path": "/inbox/messages/claim",
    "status": 204
}
```

In case the Claim Message does not contain the required information the smart coffee machine can reject the 
claim request by *NOT* granting access and responding with a status different from `200` or `204`.
