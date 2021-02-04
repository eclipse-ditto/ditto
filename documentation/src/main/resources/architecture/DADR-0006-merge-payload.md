# Merge updates on things

Date: 18.01.2021

## Status

accepted

## Context

We want to allow partial or merge updates of things with a single request.

## Decision

A merge request

* uses HTTP `PATCH` method.
* has payload in _JSON merge patch_ format defined in [RFC-7396](https://tools.ietf.org/html/rfc7396).
* has the request header `content-type` set to `application/merge-patch+json`.

## Consequences

The merge requests in JSON merge patch format have a structure similar (except `null` values) to the original data
structure (Thing JSON) which makes them easy and intuitive to use. The JSON merge patch approach was chosen over the
[JSON Patch](https://tools.ietf.org/html/rfc6902) format, which allows a more fine-grained control over the change  
operations but is also more complex/verbose and less user-friendly.
