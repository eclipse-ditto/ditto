---
title: Change Notifications
keywords: change, event, feature, notification, thing, filtering, rql, push, subscribe, consume, enrich, extra
tags: [model, rql]
permalink: basic-changenotifications.html
---

Change notifications deliver [events](basic-signals-event.html) to your application whenever a
digital twin or device state changes.

{% include callout.html content="**TL;DR**: Subscribe to change notifications via WebSocket, Server Sent Events (SSE),
or connections. Filter by namespace or RQL expression to receive only the events you care about." type="primary" %}

## How to receive change notifications

Ditto publishes events through three channels. Each delivers events to authenticated subjects that
have the required [authorization](basic-auth.html):

| Channel | Format | Use case |
|---------|--------|----------|
| [WebSocket API](httpapi-protocol-bindings-websocket.html) | [Ditto Protocol](protocol-overview.html) messages | Bidirectional communication from browser or backend clients |
| [HTTP SSE](httpapi-sse.html) | Changed entity JSON (for example, [Thing JSON](basic-thing.html#model-specification)) | Lightweight, read-only streaming in browsers |
| [Connections](basic-connections.html) | Configurable via [connectivity](connectivity-overview.html) | Server-to-server integration with message brokers |

## Filtering

You can filter events on the Ditto backend before they reach your application. Each API provides
its own mechanism for specifying filters. All of them support filtering by namespace and by RQL
expression; [connections](basic-connections.html#filtering-with-placeholder-functions) additionally
support filtering by placeholder pipeline.

{% include note.html content="All filters are specified in URL query format, so their values should be URL-encoded.
The equal sign (=) and ampersand (&) must be encoded in any RQL filter. The target topics of
[connections](basic-connections.html#target-topics-and-filtering) are an exception: there the decoded value is
what gets stored, so write the filter expressions unencoded -- `&`, `+` and `%` cannot be used in filter values
of a target topic at all, an encoded `&` (`%26`) does not survive the stored form." %}

### Filter by namespace

Provide a comma-separated list of namespaces to receive events only from Things in those
namespaces:

```text
namespaces=org.eclipse.ditto.one,org.eclipse.ditto.two
```

### Filter by RQL expression

For more granular control, use an [RQL expression](basic-rql.html) to filter based on:

* **Thing data** -- filter on the modified values in the event payload
* **Ditto Protocol fields** -- filter on message metadata using
  [placeholders](basic-placeholders.html#scope-rql-expressions-when-filtering-for-ditto-protocol-messages):
    * `topic:action` -- filter for lifecycle events (`created`, `deleted`)
    * `resource:path` -- filter by the affected [resource path](protocol-specification.html#path)

{% include note.html content="The RQL filter applies to the *modified* data by default. Unchanged data is only
considered when it has been [enriched via extraFields](basic-enrichment.html)." %}

### Examples

Only emit events when `count` changes to a value greater than 42:

```text
filter=gt(attributes/count,42)
```

Only emit events for Things starting with "myThing" when the "lamp" feature changes:

```text
filter=and(like(thingId,"org.eclipse.ditto:myThing*"),exists(features/lamp))
```

Only emit events when `manufacturer` starts with "ACME & Sons" (note the encoded `&`; not usable in a
connection target topic, see the note above):

```text
filter=like(attributes/manufacturer,"ACME %26 Sons*")
```

Only emit events for Thing creation and deletion:

```text
filter=and(in(topic:action,'created','deleted'),eq(resource:path,'/'))
```

See the full [RQL expression reference](basic-rql.html) for the complete query language.

### Filter by placeholder pipeline (connections only)

[Connections](basic-connections.html) additionally accept placeholder pipelines in separate, repeatable
`fn-filter` parameters alongside (or instead of) the RQL `filter`. Each one starts with the placeholder to
filter and ends with an `fn:filter(...)` stage; all given filters must match:

```text
fn-filter=topic:action|fn:filter('eq','modified')
```

Any placeholder available for the signal can be filtered that way, not only thing data -- see
[Filtering with placeholder functions](basic-connections.html#filtering-with-placeholder-functions).
This is not available for the WebSocket API or SSE, which only support the namespace and RQL filters.

## Further reading

* [Signal Enrichment](basic-enrichment.html) -- add extra context (like attributes) to events
* [Signals & Communication Pattern](basic-signals.html) -- understand the signal types
* [WebSocket Protocol Binding](httpapi-protocol-bindings-websocket.html) -- subscribe via WebSocket
* [Server Sent Events](httpapi-sse.html) -- subscribe via SSE
* [Connections](basic-connections.html) -- subscribe via managed connections
