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
