---
title: Release notes 3.4.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.2 of Eclipse Ditto, released on 30.11.2023"
permalink: release_notes_342.html
---

This is a bugfix release, no new features since [3.4.1](release_notes_341.html) were added.

## Changelog

Compared to the latest release [3.4.1](release_notes_341.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.4.2).

#### Fixed that Hono Connection config missed trusted certificates configuration

Bug [#1801](https://github.com/eclipse-ditto/ditto/issues/1801), that a custom configured `ca` was ignored for connections
of type `hono`, was fixed in [#1802](https://github.com/eclipse-ditto/ditto/pull/1802).

#### Background Sync not syncing entities

Bug [#1825](https://github.com/eclipse-ditto/ditto/issues/1825), not syncing non-indexes Things in the search when there
are many deleted Things, was fixed via [#1824](https://github.com/eclipse-ditto/ditto/pull/1824).

#### MQTT 5 messages sent when Ditto was not connected are not processed

Reported bug [#1767](https://github.com/eclipse-ditto/ditto/issues/1767), causing Ditto not consuming messages sent with
QoS 1 ("at least once") was fixed via [#1794](https://github.com/eclipse-ditto/ditto/pull/1794).

#### When enriching signals with extraFields, duplicate paths lead to not all fields to be enriched

Bug [#1826](https://github.com/eclipse-ditto/ditto/issues/1826), causing that fields were missing from "enrichment" 
when using duplicated paths in `extraFields`, was fixed via [#1828](https://github.com/eclipse-ditto/ditto/pull/1828).

#### Fix that a "wrong" JsonPointer in a search RQL query lead to ERRORs

[#1816](https://github.com/eclipse-ditto/ditto/pull/1816) fixes that when a search contained a `filter` (RQL) expression
with 2 slashes following each other, an "internal server error" (500) was produced instead of a 400 (Bad Request.)


### Ditto Explorer UI

#### Fix that non-successful message responses were not displayed in ACE editor

Only successful responses were displayed in the "response" editor when sending messages via the Ditto UI.  
This has been fixed in [#1810](https://github.com/eclipse-ditto/ditto/pull/1810).


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm) contained some configuration 
bugs and enhancements which are also addressed with this bugfix release.

#### Removed X-Original-URI from NGINX ingress

[#1823](https://github.com/eclipse-ditto/ditto/pull/1823) removes the (non-used) `X-Original-URI` header previously
set by the nginx ingress.
