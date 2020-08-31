---
title: Release notes 1.2.0
tags: [release_notes]
published: false
keywords: release notes, announcements, changelog
summary: "Version 1.2.0 of Eclipse Ditto, released on 31.08.2020"
permalink: release_notes_120.html
---

## Changelog

### Changes

### Bugfixes

## Migration notes

### rename config keys containing `blacklist` to `blocklist`:
* in `gateway.conf`: `ditto.gateway.http.redirect-to-https-blocklist-pattern`
* in `ditto-cluster.conf`: `ditto.cluster.cluster-status-roles-blocklist`
* in `ditto-protocol.conf`: `ditto.protocol.blocklist`

### added config key for setting the max pool size for connections
The pool is used for mapping inbound and outbound messages in the connectivity service. It is configured
per connection in the attribute `processorPoolSize`.

To provide a meaningful max per-connection pool size, you can now configure a service-wide maximum
in the connectivity service using the key `ditto.connectivity.mapping.max-pool-size` (or its corresponding
environment variable `CONNECTIVITY_MESSAGE_MAPPING_MAX_POOL_SIZE`).
