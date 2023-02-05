---
title: Release notes 1.0.0-M2
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.0.0-M2 of Eclipse Ditto, released on 04.11.2019"
permalink: release_notes_100-M2.html
---

The second milestone is one last stop preparing for the upcoming "1.0.0" release.


## Changes

### [Don't allow double slashes in JSON pointers and REST](https://github.com/eclipse-ditto/ditto/pull/524)

Ditto allowed to have double slashed in JSON pointers (e.g.: `features//foo/properties`) and HTTP endpoints.
Allowing those did not always result the API behave like expected, so this is now handled more strictly.
From now on, a `JsonPointerInvalidException` will be thrown whenever double slashes are encountered, e.g. resulting
in a status code `400` (Bad request) at the HTTP API.

### Connection JSON format was adjusted

As a result of the newly added feature "[Map Hono device connection status to Thing feature](https://github.com/eclipse-ditto/ditto/issues/492)"
(see below), the JSON format of connections was adjusted.

The new format is documented here: 
[connections payload mapping configuration](connectivity-manage-connections.html#payload-mapping-configuration).

The good news however is that previously created connections will automatically be migrated to that format 
(e.g. when querying) the API, so the old format is still supported.


## New features

### [Enhance Ditto's connectivity by invoking HTTP endpoints](https://github.com/eclipse-ditto/ditto/issues/491)

One of the bigger feature enhancements since the last milestone release is connections to existing HTTP endpoints/servers.<br/>
By adding the "connection type" HTTP to Ditto's connectivity feature Ditto can now perform HTTP calls for the configured
"targets", e.g. for twin events.
 
That may be used in order to integrate with other public HTTP APIs. See also the 
[published blog post about that](2019-10-17-http-connectivity.html). 

### [Map Hono device connection status to Thing feature](https://github.com/eclipse-ditto/ditto/issues/492)

The integration with [Eclipse Hono](https://eclipse.org/hono/) was improved by adding the possibility tp extract 
`creation-time` and `ttd`  headers from consumed Hono telemetry and event messages and automatically updating the 
targeted thing with a [ConnectionStatus](connectivity-mapping.html#connectionstatus-mapper) feature.

This feature was added by enhancing and generalizing the overall [payload mapping](connectivity-mapping.html) feature, 
as a result now multiple payload mappings may be defined and selectively applied to sources/targets in 
[connections](connectivity-manage-connections.html).

### [Support for OAuth based authentication in Ditto Java client](https://github.com/eclipse-ditto/ditto-clients/issues/17)

In [1.0.0-M1a](release_notes_100-M1a.html) support for arbitrary OpenID Connect providers was 
[added](2019-08-28-openid-connect.html) to Ditto.<br/>
Now the Ditto Java client can authenticate itself by either providing "client-id" and "client-secret" or by 
supplying JWT tokens via a custom callback.

### [Throttle max. processed inbound websocket per time interval](https://github.com/eclipse-ditto/ditto/pull/517)

It is not always desirable that a single websocket connection may "flood" a Ditto backend with a massive amount of 
commands.<br/>
This makes it possible to configure the amount of websocket commands limit per duration interval.<br/>
The defaults configuration is: 100 / 1second


## Bugfixes

### [Deleted things were still available in search](https://github.com/eclipse-ditto/ditto/issues/526)

When things were deleted, they were still available and findable in the search index (as the search index was cleared
via a `TTL` index). This is now fixed.

### [Failed connections AMQP 1.0 target didn't back-off](https://github.com/eclipse-ditto/ditto/pull/516)

When an AMQP 1.0 endpoint did not allow to open a link to an address, Ditto tried to reconnect in a high frequency.
As such errors are most likely configuration errors, that almost never is a good solution. The aggressive reconnect
produced a lot of load both to Ditto and the AMQP 1.0 endpoint.<br/>
This fix introduces an exponential back-off mechanism.

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.0.0-M2).

