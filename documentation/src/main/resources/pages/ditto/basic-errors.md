---
title: Errors
keywords: error, failure, exception, model
tags: [model]
permalink: basic-errors.html
---

Errors are datatypes containing information about occurred failures which were either
cause by the user or appeared in the server.  

## Error model specification

{% include docson.html schema="jsonschema/error.json" %}

### Status

The "status" uses HTTP status codes semantics (see [RFC 7231](https://tools.ietf.org/html/rfc7231#section-6))
to indicate whether a specific command has been successfully completed, or not.

These "status" codes can be seen as API/contract which will be always the same for a specific error.  
Use the "status" in order to identify an error, as the additional "error" and "description" might change
without prior notice.

### Error

A Ditto error contains an "error" code which is a string identifier that uniquely identifies the error.

These error codes Ditto provides in addition to the HTTP **status** code are not to be considered as API and must 
therefore not be relied on.  
They might change without prior notice.

Ditto itself uses the following prefixes for its error codes:

* `things:` - for errors related to [things](basic-thing.html)
* `policies:` - for errors related to [policies](basic-policy.html)
* `things-search:` - for errors related to the [things search](basic-search.html)
* `acknowledgement:` - for errors related to [acknowledgements](basic-acknowledgements.html)
* `messages:` - for errors related to [messages](basic-messages.html)
* `placeholder:` - for errors related to [placeholders](basic-placeholders.html)
* `jwt:` - for errors related to <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> based [authentication](basic-auth.html)
* `gateway:` - for errors produced by the (HTTP/WS) [gateway](architecture-services-gateway.html) service
* `connectivity:` - for errors produced by the [connectivity](architecture-services-connectivity.html) service

### Message

The error "message" contains a short message describing the encountered problem in plain english text.

### Description

The optional error "description" describes in more detail how the error could be resolved.

### Href

The optional href contains a link to Ditto documentation or external resources in order to help to resolve the error.


## Examples

```json
{
  "status": 404,
  "error": "things:attribute.notfound",
  "message": "The attribute with key 'unknown-key' on the thing with ID 'org.eclipse.ditto:my-thing' could not be found or the requester had insufficient permissions to access it.",
  "description": "Check if the ID of the thing and the key of your requested attribute was correct and you have sufficient permissions."
}
```

```json
{
  "status": 400,
  "error": "messages:id.invalid",
  "message": "Thing ID 'foobar2000' is not valid!",
  "description": "It must conform to the namespaced entity ID notation (see Ditto documentation)",
  "href": "https://www.eclipse.org/ditto/basic-namespaces-and-names.html#namespaced-id"
}
```

