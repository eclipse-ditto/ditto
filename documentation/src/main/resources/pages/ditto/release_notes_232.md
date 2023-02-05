---
title: Release notes 2.3.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.3.2 of Eclipse Ditto, released on 08.02.2022"
permalink: release_notes_232.html
---

This is a bugfix release, no new features since [2.3.0](release_notes_230.html) were added.

## Changelog

Compared to the latest release [2.3.1](release_notes_231.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A2.3.2), including the fixed bugs.

#### [Fix search consistency for failed patch updates](https://github.com/eclipse-ditto/ditto/pull/1300)

The search index could get never consistent for some things, as failed "patch updates" to the search index were not 
retried.

#### [Fix search sync infinite loop](https://github.com/eclipse-ditto/ditto/pull/1301)

Forcing a full search index update could lead to an infinite loop when processing "Thing Deleted" events.

#### [Ignore DittoMessageMapper for Hono delivery failed notifications](https://github.com/eclipse-ditto/ditto/pull/1299)

Delivery failure notification sent via Eclipse Hono with content-type `application/vnd.eclipse-hono-delivery-failure-notification+json`
are now excluded to be handled by the default `DittoMessageMapper` used in connections.

#### [Fixed potential race condition in LiveSignalEnforcement](https://github.com/eclipse-ditto/ditto/pull/1305)

For live response messages a race condition could happen where a 503 ("Thing not available") exception was produced
instead of running in a 408 timeout.
