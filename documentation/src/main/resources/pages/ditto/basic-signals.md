---
title: Signals
keywords: command, communication, CQRS, DDD, event, EventSourcing, response, signal
tags: [signal]
permalink: basic-signals.html
---

Ditto has a concept called `Signal` which combines common functionality of
* [Commands](basic-signals-command.html),
* [Command Responses](basic-signals-commandresponse.html),
* [Error Responses](basic-signals-errorresponse.html),
* [Events](basic-signals-event.html) and
* [Announcements](basic-signals-announcement.html).

Such common functionality is for example that all those have header fields in which they can be for example correlated
to each other. 

Signals are one of the core concepts of Ditto but they mostly are used internally for communication in the Ditto
cluster.
Nevertheless it is very helpful to have a basic understanding of what the Signal types are and in which communication 
pattern they occur.


## Architectural style

Ditto uses Commands, Events,
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.cqrs}}">CQRS</a> and EventSourcing.
[This page](http://cqrs.nu/Faq) provides a quite good explanation of the basic concepts on all of those aspects:

### Command

> People request changes to the domain by sending commands. 
They are named with a verb in the imperative mood plus and may include the aggregate type, for example `ConfirmOrder`. 
Unlike an event, a command is not a statement of fact; it's only a request, and thus may be refused.
(A typical way to convey refusal is to throw an exception).

### Event

> An event represents something that took place in the domain.
They are always named with a past-participle verb, such as `OrderConfirmed`. 
It's not unusual but also not required for an event to name an aggregate or entity that it relates to; let the domain
language be your guide.<br/>
Since an event represents something in the past, it can be considered a statement of fact and used to take decisions in
other parts of the system.


## Communication pattern

1. A **command** is sent to Ditto where it is then processed.
2. Either a **success response** or an **error response** is sent back to the issuer of the **command**.
3. In addition an **event** is both persisted into the datastore and published.<br/>
   The event describes that the change was applied to an entity (e.g. a `Thing`).<br/>
   Interested parties can subscribe for such **events** and follow the evolving entity.

{% include note.html
   content="Events caused by commands from a **[connection](basic-connections.html)** or a 
            [websocket session](httpapi-protocol-bindings-websocket.html) are not published
            **to the same origin**. The connection can receive a response, but will not additionally get an event."
%}
