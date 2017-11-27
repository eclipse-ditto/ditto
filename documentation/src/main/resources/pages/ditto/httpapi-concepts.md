---
title: HTTP API concepts
keywords: http, api, concepts, partial
tags: [http]
permalink: httpapi-concepts.html
---

{% include warning.html content="This page is still a work in progress." %}

## Specified endpoints

TODO document the static endpoints of Ditto:
* /things
* /things/{thingId}/policyId
* /things/{thingId}/acl
* /things/{thingId}/attributes
* /things/{thingId}/features
* /things/{thingId}/features/{featureId}
* /things/{thingId}/features/{featureId}/properties
* ...

## Dynamic endpoints

The JSON structure of a `Thing` defines the API.

## Partial updates

TODO document partial updates (changing only specific hierarchies instead of a complete `Thing`)

## Partial responses

TODO document partial responses:
* resource based
* with field selectors maintaining the JSON hierarchy
