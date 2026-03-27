---
title: HTTP API Search
keywords: http, api, search, query, rql
tags: [http, search, rql]
permalink: httpapi-search.html
---

You search for Things using [RQL expressions](basic-rql.html) against the HTTP search endpoint, with support for filtering, sorting, paging, and field selection.

{% include callout.html content="**TL;DR**: Send `GET` or `POST` requests to `/api/2/search/things` with `filter`, `option`, `fields`, and `namespaces` parameters to find and retrieve Things matching your criteria." type="primary" %}

{% include note.html content="Find the HTTP API reference at the
    [Search resources](http-api-doc.html?urls.primaryName=api2#/Search)." %}

## Overview

The search endpoint is:

```
http://localhost:8080/api/2/search/things
```

If you omit the `filter` parameter, the result contains all `Things` the authenticated user is [allowed to read](basic-auth.html). For details on the underlying search concepts, see [Basic Search](basic-search.html).

## How it works

You pass search criteria as query parameters (for `GET`) or as a `x-www-form-urlencoded` body (for `POST`):

| Parameter | Purpose |
|-----------|---------|
| `filter` | [RQL filter expression](basic-rql.html#rql-filter) to select Things |
| `option` | [RQL sorting and paging](basic-search.html#sorting-and-paging-options) options |
| `fields` | [Field selector](httpapi-concepts.html#partial-requests) for response projection |
| `namespaces` | Comma-separated list of namespaces to search within |

## Examples

### Search with GET

Find Things in the living room, sorted by ID, limited to 5 results:

```
GET .../search/things?filter=eq(attributes/location,"living-room")&option=sort(+thingId),limit(0,5)
```

Restrict search to specific namespaces:

```
GET .../search/things?filter=eq(attributes/location,"living-room")&namespaces=org.eclipse.ditto,foo.bar
```

Return only the `thingId` and `manufacturer` attribute:

```
GET .../search/things?filter=eq(attributes/location,"living-room")&fields=thingId,attributes/manufacturer
```

### Search with POST

`POST` requests accept the same parameters as `x-www-form-urlencoded` body content:

```
POST .../search/things
body: filter=eq(attributes/location,"living-room")&option=sort(+thingId),limit(0,5)
```

Use `POST` when your query string would be too long for a URL.

### Count Things

Get the number of Things matching a filter:

```
GET .../search/things/count?filter=eq(attributes/location,"living-room")
```

Or with `POST`:

```
POST .../search/things/count
body: filter=eq(attributes/location,"living-room")
```

## Further reading

* [Basic Search](basic-search.html) -- search concepts and indexing
* [RQL expressions](basic-rql.html) -- filter and sort syntax
* [HTTP API concepts: partial requests](httpapi-concepts.html#partial-requests) -- field selectors
