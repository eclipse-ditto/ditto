---
title: Release notes 3.8.10
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.10 of Eclipse Ditto, released on 15.12.2025"
permalink: release_notes_3810.html
---

This is a bugfix release, no new features since [3.8.9](release_notes_389.html) were added.

## Changelog

Compared to the latest release [3.8.9](release_notes_389.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.10).

#### Fix false positive ERROR logs when using response-required:false and conditions which did not match

PR [#2293](https://github.com/eclipse-ditto/ditto/pull/2293) fixes another ERROR log which falsely was logged as ERROR.  
This happened for modifying commands of a thing when using `response-required:false` header and providing a condition
which did not match (e.g. `If-Match: *`).

#### Fix mapping of response payload of HTTP APIs invoked by http-push connections

PR [#2294](https://github.com/eclipse-ditto/ditto/pull/2294) resolves an issue in Ditto managed HTTP connections, mapping 
the response payload of the called API.  
Before the fix, all responses were mapped to JSON, e.g. a text payload was mapped to a JSON string `"the text payload"`.  
And a non-text and non-JSON payload (e.g. binary) was encoded to Base64 and mapped to a JSON string containing the Base64 encoded data.

This is now fixed, e.g. images are now correctly mapped through Ditto, calling an external HTTP API.
