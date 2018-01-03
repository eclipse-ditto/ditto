---
title: HTTP API search
keywords: http, api, search, query, rql
tags: [http, search]
permalink: httpapi-search.html
---

The [search aspect](basic-search.html) of Ditto can be accessed via an HTTP API.

{% include note.html content="Find the HTTP API reference at the 
    [Search resources](http-api-doc.html?urls.primaryName=api2#/Search)." %}

The concepts of the [RQL filter](basic-search.html#rql-filter), [RQL sorting](basic-search.html#rql-sorting) and 
[RQL paging](basic-search.html#rql-paging) are mapped to HTTP as query parameters which are added to `GET` requests
to the search endpoint:

```
http://localhost:8080/api/<1|2>/search/things
```

If the [filter](#filter) parameter is omitted, the result contains all `Things` the authenticated user is 
[allowed to read](basic-auth.html).


## Query parameters

In order to define for which `Things` to search, the `filter` query parameter has to be added.<br/>
In order to change the sorting and limit the result (also to do paging), the `options` parameter has to be added.

Complex example:
```
GET .../search/things?filter=eq(attributes/location,"living-room")&option=sort(+thingId),limit(0,5)
```


## Search count
Search counts can be made against this endpoint:

```
http://localhost:8080/api/<1|2>/search/things/count
```

Complex example:
```
GET .../search/things/count?filter=eq(attributes/location,"living-room")
```
