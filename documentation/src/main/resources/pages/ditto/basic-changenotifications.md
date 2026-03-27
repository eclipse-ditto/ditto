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
its own mechanism for specifying filters, but all support the same filter types.

{% include note.html content="All filters are specified in URL query format, so their values should be URL-encoded.
The equal sign (=) and ampersand (&) must be encoded in any RQL filter." %}

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

Only emit events when `manufacturer` starts with "ACME & Sons" (note the encoded `&`):

```text
filter=like(attributes/manufacturer,"ACME %26 Sons*")
```

Only emit events for Thing creation and deletion:

```text
filter=and(in(topic:action,'created','deleted'),eq(resource:path,'/'))
```

See the full [RQL expression reference](basic-rql.html) for the complete query language.

## Further reading

* [Signal Enrichment](basic-enrichment.html) -- add extra context (like attributes) to events
* [Signals & Communication Pattern](basic-signals.html) -- understand the signal types
* [WebSocket Protocol Binding](httpapi-protocol-bindings-websocket.html) -- subscribe via WebSocket
* [Server Sent Events](httpapi-sse.html) -- subscribe via SSE
* [Connections](basic-connections.html) -- subscribe via managed connections
