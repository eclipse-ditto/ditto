---
title: HTTP API Messages
keywords: http, api, messages, thing
tags: [http]
permalink: httpapi-messages.html
---

You send messages to and from Things and Features through the HTTP API, enabling command-and-control communication with devices.

{% include callout.html content="**TL;DR**: Send a `POST` to a Thing's or Feature's `inbox` to send it a message, or to its `outbox` to send a message from it. Use the `timeout` parameter to control how long Ditto waits for a response." type="primary" %}

{% include tip.html content="Check out the [WebSocket Messages API](protocol-specification-things-messages.html)
if you also need to *receive* or *reply* to Messages." %}

## Overview

The HTTP Messages API lets you send messages **to** and **from** Things and their Features. For the underlying concepts, see the [Messages](basic-messages.html) page. For full parameter details and response codes, see the [HTTP API Documentation](http-api-doc.html#/Messages).

## How it works

Messages flow through two paths:

* **Inbox** -- send a message **to** a Thing or Feature (the device receives it)
* **Outbox** -- send a message **from** a Thing or Feature (simulating device-originated messages)

When you send a message to a Thing's inbox, Ditto routes it to the device. If the device responds, you receive the response as the HTTP response body. If the device does not respond in time, you get a `408 Request Timeout`.

## Examples

{% include note.html content="Don't forget to replace the Authorization header
and the host when trying out the examples. Also make sure the Thing, you are sending
Messages to, is existing." %}

### Send a message to a Thing

Send a message with subject `ask` to the Thing `org.eclipse.ditto:smartcoffee`:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/inbox/messages/ask \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'Hey, how are you?'
```

If the device responds, you receive its reply as the HTTP response body.

### Control the timeout

If you do not need a response (fire-and-forget), set `timeout=0` to get an immediate `202 Accepted`:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/inbox/messages/ask?timeout=0 \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'Hey, how are you?'
```

{% include tip.html content="Use the `timeout` query parameter to specify what timeout
you expect for your Messages. A timeout of *zero* will instantly return a response, whilst
other positive values change how long Ditto will wait for an answer before responding to you." %}

### Send a message to a Feature

Target a specific Feature by including its ID in the URL path:

```bash
curl --request POST \
 --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/features/water-tank/inbox/messages/action \
 --header 'content-type: text/plain' \
 --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
 --data 'heatUp'
```

### Send a message from a Thing

Replace `inbox` with `outbox` to send a message **from** the Thing:

```bash
curl --request POST \
  --url http://localhost:8080/api/2/things/org.eclipse.ditto:smartcoffee/outbox/messages/inform \
  --header 'correlation-id: an-unique-string-for-this-message' \
  --header 'content-type: text/plain' \
  --header 'Authorization: Basic ZGl0dG86ZGl0dG8=' \
  --data 'No one used me for half an hour now. I am going to shutdown soon.'
```

## Further reading

* [Messages concepts](basic-messages.html) -- message model and routing
* [WebSocket Messages API](protocol-specification-things-messages.html) -- receive and reply to messages via WebSocket
* [HTTP API Documentation](http-api-doc.html#/Messages) -- full API reference for messages
