---
title: HTTP API overview
keywords: api, http, overview, REST
tags: [http]
permalink: httpapi-overview.html
---

Ditto's HTTP API is documented separately in the [HTTP API Doc](http-api-doc.html).

There you can explore the two different API versions (the difference is described in the
[Basic Overview](basic-overview.html)).

Ditto does not provide a fully compliant RESTful API in the
[academic sense](https://www.ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm) as it does not include
hyperlinks in the HTTP responses.
It however tries to follow the other best practices.

If you have any feedback on how to improve at that point, Ditto's developer team is [eager to learn](feedback.html).

## Content Type

Currently, the content-type `application/json` is supported for all REST resources except the _PATCH_ resource.
There the content-type has to be `application/merge-patch+json`.

