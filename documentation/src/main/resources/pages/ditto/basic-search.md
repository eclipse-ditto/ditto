---
title: Search functionality
keywords: search, things-search, rql, query
tags: [search, rql]
permalink: basic-search.html
---

Ditto provides a search functionality as one of the services around its managed **digital twins**.

## Search index

Ditto's microservice [things-search](architecture-services-things-search.html) automatically consumes all 
[events](basic-signals-event.html) which are emitted for changes to `Things` and `Policies` and updates an for search 
optimized representation of the `Thing` data into its own database.

No custom indexes have to be defined as the structure in the database is "flattened" so that all data contained in 
[Things](basic-thing.html) can be searched for efficiently.


## Search queries

Queries can be made via Ditto's APIs ([HTTP](httpapi-search.html) or 
[Ditto Protocol](protocol-specification-things-search.html) e.g. via [WebSocket](httpapi-protocol-bindings-websocket.html)).

**Example:** Search for all things located in "living-room", reorder the list to start with the lowest thing ID as the first element, 
and return the first 5 results:
```
Filter:     eq(attributes/location,"living-room")
Sorting:    sort(+thingId)
Paging:     limit(0,5)
```


## Search count queries 

The same syntax applies for search count queries - only the [sorting](basic-rql.html#rql-sorting) and [paging](#rql-paging) makes no 
sense here, so there are not necessary to specify. 


## Namespaces

The Search supports specifying in which `namespaces` it should be searched. This may significantly improve the search 
performance when many Things of different namespaces are managed in Ditto's search index.  


## RQL

In order to apply queries when searching, Ditto uses the [RQL notation](basic-rql.html) which is also applied for other 
scenarios (e.g. filtering [notifications](basic-changenotifications.html)).


## Paging options

```
size(<count>)
```

Limits the search results to `<count>` items.

If the paging option is not explicitly specified a **default value** of _25_ is used. The **maximum** allowed count is 
_200_.

```
cursor(<cursor-id>)
```
Starts the search at the position of the cursor with ID `<cursor-id>`. The cursor ID is obtained from the field 
`cursor` of a previous response and marks the **position after the last entry** of the previous search. A response 
includes no cursor if there are no more results.

If a request has a `cursor` option, then any included `filter` or `sort` option may not differ from the original request of the cursor. Otherwise, the request is rejected.

**Example - return ten items with a cursor**
```
option=size(10),cursor(<cursor-from-previous-result>)
```

## RQL paging (deprecated)

{% include note.html content="The limit option is deprecated, it may be removed in future releases. Use [cursor-based 
paging](basic-search.html#paging-options) instead." %}

The RQL limiting part specifies which part (or "page") should be returned of a large search result set.

```
limit(<offset>,<count>)
```

Limits the search results to `<count>` items, starting with the item at index `<offset>`. 
* if the paging option is not explicitly specified, the **default** value `limit(0,25)` is used, i.e. the first `25` results are returned.
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

{% include note.html content="We recommend **not to use high offsets** (e.g. higher than 10000) for paging in API 2 
    because of potential performance degradations." %}
