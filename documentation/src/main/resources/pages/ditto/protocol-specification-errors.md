---
title: Protocol specification for errors
keywords: protocol, specification, error, failure
tags: [protocol]
permalink: protocol-specification-errors.html
---

When a command fails, Ditto returns an error response containing details about what went wrong.

{% include callout.html content="**TL;DR**: Error responses follow the standard Ditto Protocol envelope format and include an HTTP-semantics status code, an error code string, a human-readable message, and an optional description with resolution hints." type="primary" %}

## Overview

Ditto Protocol [error responses](basic-signals-errorresponse.html) transport information about [errors](basic-errors.html) caused by client mistakes or server problems.

## Error response format

{% include docson.html schema="jsonschema/protocol-error_response.json" %}

Each error response contains:

- **status**: An HTTP status code (e.g., `400`, `404`, `500`)
- **error**: A string error code (e.g., `things:thing.notfound`)
- **message**: A short description of the problem
- **description**: An optional hint on how to resolve the error

## How it works

When Ditto cannot process a command, it returns an error response instead of a success response. You correlate the error with the original command using the `correlation-id` header.

The error response topic differs from the command topic. For example, a failed `modify` command may return an error with a topic ending in `errors` rather than `commands/modify`.

## Error codes

The `error` string codes that Ditto provides (e.g., `things:thing.tooLarge`) are **not** part of the stable API. They may change without prior notice. Use the HTTP `status` code to identify and handle errors programmatically.

## Examples

* [Things error response examples](protocol-examples-errorresponses.html)
* [Policies error response examples](protocol-examples-policies-errorresponses.html)

## Further reading

- [Errors](basic-errors.html) -- the error model and common error codes
- [Protocol specification](protocol-specification.html) -- the full message format reference
