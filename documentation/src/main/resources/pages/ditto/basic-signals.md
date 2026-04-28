---
title: Signals & Communication Pattern
keywords: command, communication, CQRS, DDD, event, EventSourcing, response, signal, announcement
tags: [signal]
permalink: basic-signals.html
---

Signals are the messages that flow through Ditto. Every interaction -- creating a Thing, querying
a property, receiving a change notification -- is carried by a signal.

{% include callout.html content="**TL;DR**: Ditto uses five signal types: Commands (requests to read or change data),
Command Responses (success replies), Error Responses (failure replies), Events (records of changes
that already happened), and Announcements (advance notices of upcoming changes)." type="primary" %}

## Overview

Ditto follows the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> and
Event Sourcing architectural patterns.
[This page](https://cqrs.nu/Faq) provides a good explanation of the basic concepts:

> People request changes to the domain by sending commands.
They are named with a verb in the imperative mood plus and may include the aggregate type, for example
`ConfirmOrder`. Unlike an event, a command is not a statement of fact; it's only a request, and thus may be refused.

> An event represents something that took place in the domain.
They are always named with a past-participle verb, such as `OrderConfirmed`.
Since an event represents something in the past, it can be considered a statement of fact and used to take decisions in
other parts of the system.

Every change goes through a well-defined flow:

1. A **Command** arrives requesting a change or a query.
2. Ditto processes it and sends back either a **Command Response** (success) or an **Error Response**
   (failure).
3. If the command modified data, Ditto persists an **Event** and publishes it to subscribers.

{% include note.html
   content="Events caused by commands from a **[connection](basic-connections.html)** or a
            [WebSocket session](httpapi-protocol-bindings-websocket.html) are not published
            **to the same origin**. The connection receives the response, but not the event."
%}

All signal types share common header fields (like correlation IDs) that let you trace a request
through its entire lifecycle.

## Commands

Commands are requests to change or retrieve data from a digital twin or a connected device. Every
command targets a specific entity by its ID (for example, a Thing ID).

See [Commands detail](basic-signals-command.html) for the full command lifecycle.

### Modify commands

Modify commands change the state of a digital twin or trigger an action on a device. In CQRS
terminology, these are the "commands" (write side).

Related protocol specifications:
* [Create/Modify protocol specification](protocol-specification-things-create-or-modify.html)
* [Merge protocol specification](protocol-specification-things-merge.html)
* [Delete protocol specification](protocol-specification-things-delete.html)

### Query commands

Query commands retrieve data without changing anything. In CQRS terminology, these are the
"queries" (read side).

Related protocol specification:
* [Retrieve protocol specification](protocol-specification-things-retrieve.html)

## Command Responses

A command response is the reply to a command. It tells you whether the operation succeeded:

* For **modify commands**: the response confirms the change was applied (for example, `201 Created`
  or `204 No Content`).
* For **query commands**: the response contains the requested data.

If something goes wrong, you receive an [error response](#error-responses) instead.

See [Command Responses detail](basic-signals-commandresponse.html) for response structure and examples.

## Error Responses

When a command or [message](basic-messages.html) fails, Ditto sends an error response explaining
what went wrong. Failures can happen for many reasons:

* Missing permissions
* Entity not found
* Invalid input data
* Internal server errors

The [Ditto Protocol for Errors](protocol-specification-errors.html) defines the error response format.
See also [Error Responses detail](basic-signals-errorresponse.html).

Example error responses:
* [Things error responses](protocol-examples-errorresponses.html)
* [Policies error responses](protocol-examples-policies-errorresponses.html)

## Events

Events record that something **already happened**. They are past tense and irreversible -- the
change is already persisted.

See [Events detail](basic-signals-event.html) for event structure and persistence.

Events serve three purposes in Ditto:

1. **Persistence** -- appended to the event journal (event sourcing) as the source of truth
2. **Internal coordination** -- published within the Ditto cluster so services can react (for
   example, the search index updates itself based on events)
3. **External notification** -- delivered to authorized subscribers via the
   [WebSocket API](httpapi-protocol-bindings-websocket.html),
   [HTTP Server Sent Events](httpapi-sse.html), and
   [connection targets](basic-connections.html#targets) as
   [change notifications](basic-changenotifications.html)

## Announcements

Announcements signal that something **is about to happen**. Unlike events, announcements are
forward-looking and are **not** persisted.

For example, before a [Policy subject expires](basic-policy.html#expiring-subjects), Ditto can
publish an announcement so your application can react -- perhaps by renewing the subject's
credentials.

Announcements are published to authorized subscribers via the
[WebSocket API](httpapi-protocol-bindings-websocket.html) and
[connection targets](basic-connections.html#targets).

See [Announcements detail](basic-signals-announcement.html) for announcement types and examples.

## Communication pattern summary

| Step | Signal type | Direction | Persisted? |
|------|-------------|-----------|------------|
| 1 | Command | Client to Ditto | No |
| 2a | Command Response | Ditto to client | No |
| 2b | Error Response | Ditto to client (on failure) | No |
| 3 | Event | Ditto to subscribers | Yes |
| -- | Announcement | Ditto to subscribers (preemptive) | No |

## Further reading

* [Change Notifications](basic-changenotifications.html) -- subscribe to events via different APIs
* [Signal Enrichment](basic-enrichment.html) -- add extra context to events
* [Messages](basic-messages.html) -- send arbitrary payloads to/from devices
* [Ditto Protocol specification](protocol-specification.html) -- wire format for all signal types
