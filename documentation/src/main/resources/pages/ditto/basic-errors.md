---
title: Errors
keywords: error, failure, exception, model
tags: [model]
permalink: basic-errors.html
---

Errors are structured responses that describe failures caused by client mistakes or server problems.

{% include callout.html content="**TL;DR**: Every error includes an HTTP `status` code, an `error` code string, a human-readable `message`, and an optional `description` with resolution hints. Use the `status` code for programmatic handling -- the `error` code string may change without notice." type="primary" %}

## Overview

When a Ditto operation fails, Ditto returns an error response containing details about what went wrong and how you might fix it.

## Error model

{% include docson.html schema="jsonschema/error.json" %}

### Status

The `status` field uses HTTP status code semantics (see [RFC 7231](https://tools.ietf.org/html/rfc7231#section-6)) to indicate whether a command succeeded or failed.

The `status` code is part of Ditto's stable API. Use it to identify and handle errors programmatically.

### Error code

The `error` field contains a string identifier that uniquely identifies the error type (e.g., `things:attribute.notfound`).

These error codes are **not** part of the stable API and may change without notice. Do not rely on them for programmatic error handling.

Ditto uses these prefixes for its error codes:

| Prefix | Domain |
|---|---|
| `things:` | [Things](basic-thing.html) |
| `policies:` | [Policies](basic-policy.html) |
| `things-search:` | [Things search](basic-search.html) |
| `acknowledgement:` | [Acknowledgements](basic-acknowledgements.html) |
| `messages:` | [Messages](basic-messages.html) |
| `placeholder:` | [Placeholders](basic-placeholders.html) |
| `jwt:` | <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a>-based [authentication](basic-auth.html) |
| `gateway:` | [Gateway](architecture-services-gateway.html) service |
| `connectivity:` | [Connectivity](architecture-services-connectivity.html) service |

### Errors for inaccessible things and policies

Since Ditto 4.0.0, Ditto doesn't reveal whether a thing or a policy exists to a requester without any permission on it.
For every rejected request, such a requester gets the same error as for a nonexistent entity:
status `404` with error code `things:thing.notfound` or `policies:policy.notfound`, even for a write request.

A requester with at least one permission on the entity gets a more specific error instead, for example
status `404` with error code `things:attributes.notfound`, or status `403` with error code `things:thing.notmodifiable`.

Operators can restore the previous behavior, which returns the specific errors to every requester, by setting the
environment variable `DITTO_DEVOPS_FEATURE_HIDE_INACCESSIBLE_ENTITY_EXISTENCE_ENABLED` of the things and policies
services to `false`.

### Message

The `message` field contains a short, human-readable description of the problem.

### Description

The optional `description` field provides more detail on how to resolve the error.

### Href

The optional `href` field links to Ditto documentation or external resources that help resolve the error.

## Examples

A "not found" error:

```json
{
  "status": 404,
  "error": "things:attribute.notfound",
  "message": "The attribute with key 'unknown-key' on the thing with ID 'org.eclipse.ditto:my-thing' could not be found or the requester had insufficient permissions to access it.",
  "description": "Check if the ID of the thing and the key of your requested attribute was correct and you have sufficient permissions."
}
```

An "invalid ID" error:

```json
{
  "status": 400,
  "error": "messages:id.invalid",
  "message": "Thing ID 'foobar2000' is not valid!",
  "description": "It must conform to the namespaced entity ID notation (see Ditto documentation)",
  "href": "https://www.eclipse.dev/ditto/basic-namespaces-and-names.html#namespaced-id"
}
```

## Further reading

- [Protocol errors](protocol-specification-errors.html) -- error format in Ditto Protocol messages
- [Protocol specification](protocol-specification.html) -- the full message format reference
