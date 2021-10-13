---
title: Change notifications
keywords: change, event, feature, notification, thing, filtering, rql, push, subscribe, consume, enrich, extra
tags: [model, rql]
permalink: basic-changenotifications.html
---

[Signals](basic-signals.html) already described what an [Event](basic-signals-event.html) in Ditto is.
Events are emitted after an entity (either a **digital twin** or an actual device) was changed.

At the Ditto API there are different ways for getting notified of such events:
* Via the [WebSocket API](httpapi-protocol-bindings-websocket.html) a WebSocket client gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive as [Ditto Protocol](protocol-overview.html) messages.
* Via [HTTP SSEs](httpapi-sse.html) a consumer of the SSE `EventSource` gets all Events the authenticated subject
  (e.g. a user) is [authorized](basic-auth.html) to receive directly in the format of the changed entity 
  (e.g. as [Thing JSON](basic-thing.html#model-specification) format).
* Via an established [connection](basic-connections.html) in the [connectivity](connectivity-overview.html) service


## Filtering

In order to not get all of the events an [authenticated subject](basic-auth.html#authenticated-subjects) 
(e.g. a user added in nginx) is allowed to see, but to filter for specific criteria, 
events may be filtered on the Ditto backend side before they are sent to an event receiver.

The above mentioned different APIs provide their own mechanisms on how to define such filters, but they all share the
common functionality of based on which information events may be filtered.

{% include note.html content="All filters are specified in a URL query format, therefore their values should be URL
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

If filtering by namespaces is not sufficient, Ditto also allows to provide an [RQL expression](basic-rql.html) 
specifying:

* a Thing payload based condition determining which events should be emitted and which should not
* a filter based on the fields of the [Ditto Protocol](protocol-specification.html) message which should be filtered, 
  e.g.:
   * using the `topic:action` placeholder as query property, filtering for lifecycle events (`created`, `deleted`) 
     and other filter options on the [Ditto Protocol topic](protocol-specification-topic.html) can be done
   * using the `resource:path` placeholder as query property, filtering based on the affected 
     [Ditto Protocol path](protocol-specification.html#path) of a Ditto Protocol message can be done
   * for all supported placeholders, please refer to the 
     [placeholders documentation](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages)

{% include note.html content="This filter is *by default* applied on the modified data of a Thing.<br/>
    Data which was not changed will only be considered when it was 
    [enriched via \"extraFields\"](basic-enrichment.html)." %}

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

Only emit events for Thing creation and deletion:
```
filter=and(in(topic:action,'created','deleted'),eq(resource:path,'/'))
```

You get the idea of how mighty this becomes by utilizing Ditto's [RQL expressions](basic-rql.html).
