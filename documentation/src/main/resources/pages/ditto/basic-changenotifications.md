---
title: Change notifications
keywords: change, event, feature, notification, thing, filtering, rql, push, subscribe, consume
tags: [model, rql]
permalink: basic-changenotifications.html
---

[Signals](basic-signals.html) already described what an [Event](basic-signals-event.html) in Ditto is.
Events are emitted after an entity (either a **Digital Twin** or an actual device) was changed.

At the Ditto API there are different ways for getting notified of such events:
* Via the [WebSocket API](httpapi-protocol-bindings-websocket.html) a WebSocket client gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive as [Ditto Protocol](protocol-overview.html) messages.
* Via [HTTP SSEs](httpapi-sse.html) a consumer of the SSE `EventSource` gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive directly in the format of the changed entity 
  (e.g. as [Thing JSON](basic-thing.html#model-specification) format).
* Via an established [connection](basic-connections.html) in the [connectivity](connectivity-overview.html) service


## Filtering

In order to not get all of the events an authenticated [subject](basic-auth.html) (e.g. a user added in nginx) is allowed
to see, but to filter for specific criteria, events may be filtered on the Ditto backend side.

The above mentioned different APIs provide their own mechanisms on how to define such filters, but they all share the
common functionality of based on which information events may be filtered.

{% include note.html content="All filters are specified in an URL query format, therefore their values should be URL
encoded before sending them to the backend. The equal (=) and the ampersand (&) character must be encoded in any RQL
filter!" %}

### By namespaces

Filtering may be done based on a namespace name. Each Ditto [Thing](basic-thing.html) has an ID containing a namespace 
(see also the conventions for a [Thing ID](basic-thing.html#thing-id)).

By providing the `namespaces` filter, a comma separated list of which namespaces to include in the result, only Things 
in namespaces of interest are considered and thus only events of these Things are emitted at the API.

For example, one would only subscribe for events occurring in 2 specific namespaces by defining:
```
namespaces=org.eclipse.ditto.one,org.eclipse.ditto.two
```

### By RQL expression

If filtering by namespaces it not sufficient, Ditto also allows to provide an [RQL expression](basic-rql.html) specifying a
Thing payload based condition determining which events should be emitted and which don't.

{% include note.html content="This filter is applied on the modified data of a Thing, data which was not changed will 
    not be considered when applying the filter." %}

This provides the opportunity to formulate filters like the following:

#### Examples

Only emit events when attribute "count" was changed to a value greater than 42:
```
filter=gt(attributes/count,42)
```

Only emit events for Things starting with myThing when a feature "lamp" was modified:
```
filter=and(like(thingId,"org.eclipse.ditto:myThing*"),exists(features/lamp))
```

Only emit events when the attribute "manufacturer" was changed to starting with "ACME & Sons".
The `&` must be escaped in that case:
```
filter=like(attributes/manufacturer,"ACME %26 Sons*")
```

You get the idea of how mighty this becomes by utilizing Ditto's [RQL expressions](basic-rql.html).
