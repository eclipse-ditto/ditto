---
title: Search functionality
keywords: search, things-search, rql, query
tags: [search, rql]
permalink: basic-search.html
---

Ditto provides a search functionality as one of the services around its managed **digital twins**.
The functionality is available for the following APIs.

| API | Access Method | Characteristics |
|-----|---------------|-----------------|
|[HTTP](httpapi-search.html)|HTTP request-response|Stateless|
|[Ditto protocol](protocol-specification-things-search.html)|[Websocket](httpapi-protocol-bindings-websocket.html) and [connections](basic-connections.html)| [Reactive-streams](https://reactive-streams.org) compatible |
|[Server-sent events](httpapi-sse.html#sse-api-searchthings)|[HTML5 server-sent events](https://html.spec.whatwg.org/multipage/server-sent-events.html)|Streaming with resumption|

## Search index

Ditto's microservice [things-search](architecture-services-things-search.html) automatically consumes all 
[events](basic-signals-event.html) which are emitted for changes to `Things` and `Policies` and updates an for search 
optimized representation of the `Thing` data into its own database.

No custom indexes have to be defined as the structure in the database is "flattened" so that all data contained in 
[Things](basic-thing.html) can be searched for efficiently.

## Consistency

Ditto's search index provides **eventual consistency**.

In order to reduce load to the database when processing updates in a high frequency, the search index is updated 
with a default interval of 1 second (configurable via environment variable `THINGS_SEARCH_UPDATER_STREAM_WRITE_INTERVAL`).

That means that when a thing is updated and the API (e.g. the HTTP endpoint) returns a success response, the search index
will not reflect that change in that instant. The change will most likely be reflected in the search index within
1-2 seconds. In rare cases the duration until consistency is reached again might be higher.

If it is important to know when a twin modification is reflected in the search index, request the
[built-in acknowledgement](basic-acknowledgements.html#built-in-acknowledgement-labels) `search-persisted` 
in the corresponding command.  
Search index update is successful if the status code of `search-persisted` in the command response is 204 "no content".
Status codes at or above 400 indicate failed search index update due to client or server errors.

## Search queries

Queries can be made via Ditto's APIs ([HTTP](httpapi-search.html) or 
[Ditto Protocol](protocol-specification-things-search.html) e.g. via [WebSocket](httpapi-protocol-bindings-websocket.html)).

Search queries are formulated using the by Ditto supported subset of [RQL](basic-rql.html).

### Search queries on scalar JSON values

The [query property](basic-rql.html#query-property) used in a search can contain either a scalar JSON value:
* JSON boolean
* JSON number
* JSON string

**Example:** Search for all things located in "living-room", reorder the list to start with the lowest thing ID as
the first element, and return the first 5 results:
```
Filter:     eq(attributes/location,"living-room")
Sorting:    sort(+thingId)
Paging:     size(5),cursor(CURSOR_ID)
```

### Search queries in JSON arrays

Or the [query property](basic-rql.html#query-property) used in a search it can also contain a JSON array.  
The search index will index any values in that array, even arrays if mixed types are supported.

For example, assuming that we have the following thing containing special "tags" of different types:
```json
{
  "thingId": "org.eclipse.ditto:tagged-thing-1",
  "policyId": "org.eclipse.ditto:tagged-thing-1",
  "attributes": {
    "tags": [
      "misc",
      "no-due-date",
      "high-priority",
      2,
      3,
      5,
      false,
      {
        "room": "kitchen",
        "floor": 2
      }
    ]
  }
}
```

We can formulate various different queries on different scalar values:
```
eq(attributes/tags,"high-priority")
-> match:       "high-priority" is contained

ne(attributes/tags,"high-priority")
-> no match:    "high-priority" is contained, so "ne" will not match

in(attributes/tags,"misc","something-non-matching")
-> match:       "misc" is a match

like(attributes/tags,"*-priority")
-> match:       "high-priority" string matches

ne(attributes/tags,1)
-> match:       as 1 is not part of the tags

gt(attributes/tags,6)
-> no match:    as no number > 6 is contained
```

And we can even formulate queries on JSON objects contained in the JSON array:
```
exists(attributes/tags/room)
-> match:       array contains one object having a key "room"

eq(attributes/tags/room,"kitchen")
-> match:       array contains one object with "room"="kitchen"

ge(attributes/tags/floor,2)
-> match:       array contains one object where floor is >= 2
```


## Search count queries 

The same syntax applies for search count queries - only the [sorting](basic-rql.html#rql-sorting) and 
[paging](#rql-paging-deprecated) makes no sense here, so there are not necessary to specify. 


## Namespaces

The Search supports specifying in which `namespaces` it should be searched. This may significantly improve the search 
performance when many Things of different namespaces are managed in Ditto's search index.  


## RQL

In order to apply queries when searching, Ditto uses the [RQL notation](basic-rql.html) which is also applied for other 
scenarios (e.g. filtering [notifications](basic-changenotifications.html)).


## Sorting and paging options

The [`sort` option](basic-rql.html#rql-sorting) governs the order of search results.

```
sort(<+|-><property1>,<+|-><property2>,...)
```

If not given, search results are listed in the ascending order of thing IDs, namely `sort(+thingId)`.

The `size` option
```
size(<count>)
```
limits the search results delivered in one HTTP response or one Ditto protocol message to `<count>` items.

If the paging option is not explicitly specified a **default value** of _25_ is used. 
The **maximum** allowed count is _200_.

```
cursor(<cursor-id>)
```
Starts the search at the position of the cursor with ID `<cursor-id>`. The cursor ID is obtained from the field 
`cursor` of a previous response and marks the **position after the last entry** of the previous search. A response 
includes no cursor if there are no more results.

If a request has a `cursor` option, then any included `filter` or `sort` option may not differ from the original request 
of the cursor. Otherwise, the request is rejected.

**Example - return ten items with a cursor**
```
option=size(10),cursor(<cursor-from-previous-result>)
```

## RQL paging (deprecated)

{% include note.html content="The limit option is deprecated, it may be removed in future releases. Use [cursor-based 
paging](basic-search.html#sorting-and-paging-options) instead." %}

The RQL limiting part specifies which part (or "page") should be returned of a large search result set.

```
limit(<offset>,<count>)
```

Limits the search results to `<count>` items, starting with the item at index `<offset>`. 
* if the paging option is not explicitly specified, the **default** value `limit(0,25)` is used, 
  i.e. the first `25` results are returned.
* the **maximum** allowed count is `200`.

**Example - return the first ten items**
```
limit(0,10)
```

**Example - return the items 11 to 20**
```
limit(10,10)
```
i.e. Return the next ten items (from index 11 to 20)

{% include note.html content="We recommend **not to use high offsets** (e.g. higher than 10000) for paging
    because of potential performance degradations." %}
