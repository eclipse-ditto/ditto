---
title: Release notes 1.2.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 1.2.0 of Eclipse Ditto, released on 31.08.2020"
permalink: release_notes_120.html
---

The second minor (feature adding) release of Eclipse Ditto 1 is here: **1.2.0**.

It is API and [binary compatible](https://github.com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to Eclipse Ditto 1.0.0 and 1.1.0.

## Changelog

Compared to the latest minor release [1.1.0](release_notes_110.html), the following changes, new features and
bugfixes were added.

The main change of Ditto 1.2.0 is the now full support for QoS 1 ("at least once") message processing.

### Changes

#### [Update Docker base image to OpenJ9 0.21.0](https://github.com/eclipse/ditto/pull/743)

Updated to running the Ditto Docker containers with the latest OpenJ9 0.21.0 (with OpenJDK 11).

#### Added config key for setting the max pool size for connections

The pool is used for mapping inbound and outbound messages in the connectivity service. It is configured
per connection in the attribute `processorPoolSize`.

To provide a meaningful max per-connection pool size, you can now configure a service-wide maximum
in the connectivity service using the key `ditto.connectivity.mapping.max-pool-size` (or its corresponding
environment variable `CONNECTIVITY_MESSAGE_MAPPING_MAX_POOL_SIZE`).


### New features

#### [`fn:filter` function for connectivity header mapping](https://github.com/eclipse/ditto/pull/674)

In connection [header mappings](connectivity-header-mapping.html) as part of the placeholders, the new 
[`fn:filter`](basic-placeholders.html#function-library) function may be used in order to remove the result of the 
previous expression in the function pipeline unless the condition specified by the parameters is satisfied.

#### [Whoami HTTP resource](https://github.com/eclipse/ditto/pull/687)

The new HTTP `GET` resource `/whoami` may be called in order to find out which authorization subjects were resolved in 
the HTTP call's authentication. This can be e.g. useful to find out the used JWT subject which should be added to 
[policies](basic-policy.html#subjects).

#### [Support using client certificate based authentication in HTTP push connections](https://github.com/eclipse/ditto/pull/695)

Connections of type [HTTP push](connectivity-protocol-bindings-http.html) can now, additionally to username/password 
based authentication, make use of 
[client certificate based authentication](connectivity-protocol-bindings-http.html#client-certificate-authentication).

#### [Automatic end-2-end acknowledgements handling for managed connections](https://github.com/eclipse/ditto/issues/661)

Acknowledgements can now be configured to be requested for messages consumed by connection 
[sources (acknowledgement requests)](basic-connections.html#source-acknowledgement-requests) and can automatically be 
issued by targets to automatically [issue acknowledgements](basic-connections.html#target-issued-acknowledgement-label) 
for all published twin events, live commands and live messages that request them.

#### [End-2-end acknowledgements support for "live" messages/commands](https://github.com/eclipse/ditto/issues/757)

Acknowledgements for [live messages/commands](basic-acknowledgements.html#assure-qos-until-processing-of-a-live-commandmessage-by-a-subscriber---live-response)
are now supported as well. Both requesting and issuing them, e.g. in order to acknowledge that a message was 
successfully received without directly responding to it.

#### [Addition of `_created` date to things](https://github.com/eclipse/ditto/issues/749)

Whenever a [thing](basic-thing.html) is now created, a JSON field `"_created"` is now added containing the creation 
date. This field can be selected via [fields selection](httpapi-concepts.html#with-field-selector), as the already 
existing `"_modified"` field can also be. The created date can also be used as part of 
[search RQL queries](basic-rql.html).

#### [Support for adding `_metadata` to things](https://github.com/eclipse/ditto/issues/680)

On modifying API calls to a [thing](basic-thing.html), additional metadata can now be passed with the header field 
`"put-header"`. Documentation for this feature is still missing, but will be added soon after the 1.2.0 release.

### Bugfixes

Several bugs in Ditto 1.1.x were fixed for 1.2.0.<br/>
This is a complete list of the 
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A1.2.0), including the fixed bugs.

#### [Connectivity service does not consume message after reconnect to AMQP (0.9.1)](https://github.com/eclipse/ditto/issues/770)

Connections via AMQP 0.9.1 did not correctly resume message consumption after the broker was e.g. restarted.


## Migration notes

### Renamed config keys containing `blacklist` to `blocklist`

* in `gateway.conf`: `ditto.gateway.http.redirect-to-https-blocklist-pattern`
* in `ditto-cluster.conf`: `ditto.cluster.cluster-status-roles-blocklist`
* in `ditto-protocol.conf`: `ditto.protocol.blocklist`

If you configured any of the `blocklist` entries with Ditto < 1.2.0, you'll have to adjust your configuration 
accordingly.
