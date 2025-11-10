---
title: Release notes 3.8.6
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.8.6 of Eclipse Ditto, released on 11.11.2025"
permalink: release_notes_386.html
---

This is a bugfix release, no new features since [3.8.5](release_notes_385.html) were added.

## Changelog

Compared to the latest release [3.8.5](release_notes_385.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.8.6).

#### Fix wrong CBOR upper bound calculation causing BufferOverflowExceptions during WoT validation

In Ditto [3.8.4](release_notes_384.html) we introduced an optimization on WoT validation (based on JsonSchema) making
use Ditto's CBOR serialization based on Jackson. This should save memory allocations on the heap as a `ByteBuffer` was
used instead of copying byte arrays.  
However, for specific JsonObjects and JsonArrays, the upper bound calculation for the required `ByteBuffer` size was
done wrongly which could lead to `BufferOverflowExceptions` during WoT validation.  
PR [#2262](https://github.com/eclipse-ditto/ditto/pull/2262) fixes this calculation and also adds a failover, treating
the `BufferOverflowException` in a graceful way, logging the error to STDERR.
