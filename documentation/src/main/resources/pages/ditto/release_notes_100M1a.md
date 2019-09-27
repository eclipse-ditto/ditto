---
title: Release notes 1.0.0-M1a
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.0.0-M1a of Eclipse Ditto, released on 17.09.2019"
permalink: release_notes_100-M1a.html
---

This first milestone of the "1.0.0" release provides a preview of what to expect in the next release.


## Changes

### [Add HiveMQ MQTT client as an alternative for MQTT integration](https://github.com/eclipse/ditto/pull/487)

Due to the problems described in [#450](https://github.com/eclipse/ditto/issues/450) we decided to add the HiveMQ
 MQTT Client as an alternative for the connection of Ditto to MQTT brokers. Once it has proven to be working and
  stable in production, it will replace the previous client (Alpakka/Paho).
  
### [Scalable event publishing](https://github.com/eclipse/ditto/pull/483)

This patch improves horizontal scalability of Ditto's event publishing mechanism. Find a more detailed description of
 this change in the pull request.
 
### [Typed entity IDs](https://github.com/eclipse/ditto/pull/475)

This change introduces validated Java representations for entity IDs.

### [Introduce architectural decision records (ADR)](https://github.com/eclipse/ditto/pull/470)

We want to keep track of architectural decisions and decided to use the format of ADRs for this purpose.

### [Relax uri restrictions](https://github.com/eclipse/ditto/pull/451)

With this change we allow more characters in uris.

### [Background cleanup for stale journal entries and snapshots](https://github.com/eclipse/ditto/pull/446)

This change introduces a asynchronous background deletion approach which replaces the current approach.


## New features

### [Initial contribution of Java client SDK](https://github.com/eclipse/ditto-clients/pull/1)

Contribution was extracted from former commercial-only client - all references to Bosch were removed. Consists of
 full working, OSGi capable "ditto-client" artifact. More information can be found in our
 [SDK documentation](client-sdk-java.html).
 
### [Configurable authorization servers](https://github.com/eclipse/ditto/pull/477)

Eclipse Ditto now supports all OAuth 2.0 providers which implement OpenID Connect out-of-the-box. See this 
[blog post](https://www.eclipse.org/ditto/2019-08-28-openid-connect.html) for more details.
 
### [Fine grained access for connections](https://github.com/eclipse/ditto/pull/463)

With this change it is possible to restrict access to connections on any level via a policy.

### [Reconnecting feature for created connections](https://github.com/eclipse/ditto/pull/442)

When creating a connection, it will also contain the desired status of the connection.


## Bugfixes

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A1.0.0-M1a).

