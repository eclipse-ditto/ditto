---
title: HTTP API Messages
keywords: http, api, messages, thing
tags: [http]
permalink: httpapi-messages.html
---

The HTTP API allows sending Messages **to** and **from** Things and its Features.
To dive into the basic concepts of the Messages functionality, please have a look 
at the [Messages page](basic-messages.html). 

{% include tip.html content="Check out the [WebSocket Messages API](protocol-specification-things-messages.html)
if you also need to *receive* or *reply* to Messages." %}

This page gives you a quick hands-on introduction to the HTTP Messages API. To learn
about the parameters, constraints, possible responses, etc. move over to the
[HTTP API Documentation](http-api-doc.html#/Messages).

## Using the HTTP Messages API

The following parts contain examples on how to send to and from Things and Features.
For the examples we will use some kind of smart coffee machine with the id *smartcoffee*.

{% include note.html content="Don't forget to replace the Authorization header
and the host when trying out the examples. Also make sure the Thing, you are sending
Messages to, is existing." %}

The examples use `cURL` for the HTTP requests. You can of course choose 
whatever tool you prefer to work with.

### Sending a Message to a Thing

A message is always sent **to** the **inbox** of the receiving entity.
Let us view a simple Message that asks our Thing *smartcoffee* how it is feeling
today:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/inbox/messages/ask \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'Hey, how are you?'
```

Notice we are sending the Message to the *inbox* of our Thing *org.eclipse.ditto:smartcoffee*.
The subject of the Message is *'ask'* and contains plain text as content.
Short after, we would receive a response from smartcoffee:

```text
I do not know, since i am only a coffee machine.
```

But what would happen if smartcoffee was offline or for some other reason 
could not respond to our Message? This would cause the HTTP-Request to end 
in a timeout. This is especially annoying when sending a Message for which
we don't expect a response.

This is why Ditto introduced the `timeout` query parameter to the requests.
With it, you can specify how long Ditto should wait for a response
before closing the HTTP request with a timeout:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/inbox/messages/ask.question?timeout=0 \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'Hey, how are you?'
```

You will instantly receive a `202 Accepted` from Ditto instead of the 
`408 Request Timeout` response.

With the `timeout` query parameter you can also choose a different timeout than the
default one provided by Ditto.

{% include tip.html content="Use the `timeout` query parameter to specify what timeout
you expect for your Messages. A timeout of *zero* will instantly return a response, whilst
other positive values change how long Ditto will wait for an answer before responding to you." %}

### Sending a Message to a Feature

Sending a Message to a Feature works just about the same way as sending it to a Thing.
The only difference is the URL to which you will need to send the Message. See
how we can ask the *water-tank* Feature of our Thing *smartcoffee* to heat up:

```bash
curl --request POST \
 --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/features/water-tank/inbox/messages/action \
 --header 'content-type: text/plain' \
 --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
 --data 'heatUp'
```

### Sending a Message from a Thing or Feature

Sending a Message **from** a Thing or Feature works just as you would expect.
Simply replace the *inbox* path of the URI with *outbox*. Think again of our
Thing smartcoffee, which needs to inform about something:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/outbox/messages/inform \
  --header 'correlation-id: an-unique-string-for-this-message' \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'No one used me for half an hour now. I am going to shutdown soon.'
```

