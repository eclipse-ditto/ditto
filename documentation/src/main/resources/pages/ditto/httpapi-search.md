---
title: HTTP API search
keywords: http, api, search, query, rql
tags: [http, search, rql]
permalink: httpapi-search.html
---

The [search aspect](basic-search.html) of Ditto can be accessed via an HTTP API.

{% include note.html content="Find the HTTP API reference at the 
    [Search resources](http-api-doc.html?urls.primaryName=api2#/Search)." %}

The concepts of the [RQL expression](basic-rql.html#rql-filter), [RQL sorting](basic-rql.html#rql-sorting) and 
[RQL paging](basic-search.html#rql-paging-deprecated) are mapped to HTTP as query parameters which are added to 
`GET` requests to the search endpoint:

```
http://localhost:8080/api/2/search/things
```

If the `filter` parameter is omitted, the result contains all `Things` the authenticated user is 
[allowed to read](basic-auth.html).

Optionally a `namespaces` parameter can be added to search only in the given namespaces.  


## Query parameters

In order to define for which `Things` to search, the `filter` query parameter has to be added.<br/>
In order to change the sorting and limit the result (also to do paging), the `option` parameter has to be added.
Default values of each option is documented [here](basic-search.html#sorting-and-paging-options).

Complex example:
```
GET .../search/things?filter=eq(attributes/location,"living-room")&option=sort(+thingId),limit(0,5)&namespaces=org
.eclipse.ditto,foo.bar
```

Another Complex example with the `namespaces` parameter:
```
GET .../search/things?filter=eq(attributes/location,"living-room")&namespaces=org.eclipse.ditto,foo.bar
```

The HTTP search API can also profit from the [partial request](httpapi-concepts.html#partial-requests) concept 
of the API:<br/>
Additionally to a `filter` and `options`, a `fields` parameter may be specified in order to select which data 
of the result set to retrieve.

Example which only returns `thingId` and the `manufacturer` attribute of the found Things:
```
GET .../search/things?filter=eq(attributes/location,"living-room")&fields=thingId,attributes/manufacturer
```

With the `namespaces` parameter, the result can be limited to the given namespaces.

Example which only returns Things with the given namespaces prefix:
```
GET .../search/things?namespaces=org.eclipse.ditto,foo.bar
```

## Search count
Search counts can be made against this endpoint:

```
http://localhost:8080/api/2/search/things/count
```

Complex example:
```
GET .../search/things/count?filter=eq(attributes/location,"living-room")
```
