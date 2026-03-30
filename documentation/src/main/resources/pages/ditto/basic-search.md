---
title: Search functionality
keywords: search, things-search, rql, query
tags: [search, rql]
permalink: basic-search.html
---

Ditto provides a search service that lets you query across all managed digital twins using [RQL expressions](basic-rql.html).

{% include callout.html content="**TL;DR**: You search Things using RQL filter expressions, with results sorted and paged. The search index updates asynchronously (eventual consistency) -- typically within 1-2 seconds." type="primary" %}

## Overview

You can access the search functionality through three APIs:

| API | Access Method | Characteristics |
|---|---|---|
| [HTTP](httpapi-search.html) | HTTP request-response | Stateless |
| [Ditto protocol](protocol-specification-things-search.html) | [WebSocket](httpapi-protocol-bindings-websocket.html) and [connections](basic-connections.html) | [Reactive-streams](https://reactive-streams.org) compatible |
| [Server-sent events](httpapi-sse.html#sse-for-search-results) | [HTML5 server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html) | Streaming with resumption |

## How it works

### Search index

Ditto's [things-search](architecture-services-things-search.html) microservice automatically consumes all [events](basic-signals-event.html) emitted for changes to `Things` and `Policies`. It updates a search-optimized representation in its own database.

You do not need to define custom indexes. The database structure is "flattened" so that all data in [Things](basic-thing.html) can be searched efficiently.

### Consistency

The search index provides **eventual consistency**. Updates are written at a default interval of 1 second (configurable via `THINGS_SEARCH_UPDATER_STREAM_WRITE_INTERVAL`).

When you update a Thing and receive a success response, the search index does not reflect that change immediately. The change typically appears within 1-2 seconds.

If you need to know when a modification is reflected in the search index, request the [built-in acknowledgement](basic-acknowledgements.html#built-in-acknowledgement-labels) `search-persisted` in the command. A status code of `204` confirms successful index update. Status codes at or above `400` indicate failure.

## Search queries

You formulate search queries using the Ditto-supported subset of [RQL](basic-rql.html). Queries work through the [HTTP API](httpapi-search.html) or the [Ditto Protocol](protocol-specification-things-search.html) (e.g., via [WebSocket](httpapi-protocol-bindings-websocket.html)).

### Querying scalar JSON values

The [query property](basic-rql.html#query-property) can target scalar JSON values: booleans, numbers, or strings.

**Example** -- find all Things located in "living-room", sorted ascending by Thing ID, returning the first 5 results:

```text
Filter:     eq(attributes/location,"living-room")
Sorting:    sort(+thingId)
Paging:     size(5),cursor(CURSOR_ID)
```

### Querying JSON arrays

The query property can also target JSON arrays. Ditto indexes all values in the array, including mixed types.

Given a Thing with tags of different types:

```json
{
  "thingId": "org.eclipse.ditto:tagged-thing-1",
  "policyId": "org.eclipse.ditto:tagged-thing-1",
  "attributes": {
    "tags": [
      "misc", "no-due-date", "high-priority",
      2, 3, 5, false,
      { "room": "kitchen", "floor": 2 }
    ]
  }
}
```

You can query against scalar values in the array:

```text
eq(attributes/tags,"high-priority")   -> match
ne(attributes/tags,"high-priority")   -> no match
in(attributes/tags,"misc","other")    -> match
like(attributes/tags,"*-priority")    -> match
gt(attributes/tags,6)                 -> no match
```

You can also query JSON objects inside the array:

```text
exists(attributes/tags/room)            -> match
eq(attributes/tags/room,"kitchen")      -> match
ge(attributes/tags/floor,2)             -> match
```

## Search count queries

Count queries use the same filter syntax. Sorting and paging options are not applicable for count queries.

## Namespaces

You can restrict the search to specific `namespaces`. This can significantly improve performance when many Things from different namespaces exist in the index.

## RQL

Ditto uses [RQL notation](basic-rql.html) for search queries and other scenarios such as filtering [notifications](basic-changenotifications.html).

## Sorting and paging options

### Sorting

The [`sort` option](basic-rql.html#rql-sorting) controls the order of search results:

```text
sort(<+|-><property1>,<+|-><property2>,...)
```

If not specified, results are sorted ascending by Thing ID: `sort(+thingId)`.

### Paging with cursor

The `size` option limits results per response:

```text
size(<count>)
```

Default: **25**. Maximum: **200**.

Use cursor-based paging to iterate through large result sets:

```text
cursor(<cursor-id>)
```

The cursor ID comes from the `cursor` field of a previous response and marks the position after the last returned entry. No cursor in the response means no more results.

If a request includes a `cursor`, the `filter` and `sort` options must match the original request that produced the cursor.

**Example** -- return ten items with a cursor:

```text
option=size(10),cursor(<cursor-from-previous-result>)
```

## RQL paging (deprecated)

{% include note.html content="The limit option is deprecated, it may be removed in future releases. Use [cursor-based
paging](basic-search.html#sorting-and-paging-options) instead." %}

The `limit` option specifies which page to return:

```text
limit(<offset>,<count>)
```

Default: `limit(0,25)`. Maximum count: `200`.

**Example** -- return items 11 to 20:

```text
limit(10,10)
```

{% include note.html content="We recommend **not to use high offsets** (e.g. higher than 10000) for paging
    because of potential performance degradations." %}

## Further reading

- [RQL expressions](basic-rql.html) -- query language reference
- [Search protocol](protocol-specification-things-search.html) -- reactive-streams search via Ditto Protocol
- [HTTP search API](httpapi-search.html) -- REST-based search
