---
title: Release notes 3.7.1
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.7.1 of Eclipse Ditto, released on 05.03.2025"
permalink: release_notes_371.html
---

This is a bugfix release, no new features since [3.7.0](release_notes_370.html) were added.

## Changelog

Compared to the latest release [3.7.0](release_notes_370.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.7.1).

#### Update SSHD version fixing security vulnerabilities

PR [#2133](https://github.com/eclipse-ditto/ditto/pull/2133) updates the used dependency sshd-core to the latest version, 
which fixes the known security vulnerabilities in dependencies of `sshd-core`.

#### Fix configuration of throttling of search index after policy update

The Helm chart configuration for the Ditto search index throttling contained a typo which was fixed in PR 
[#2134](https://github.com/eclipse-ditto/ditto/pull/2134).

#### Fix migrate thing definition validation issue for dry-runs

PR [#2136](https://github.com/eclipse-ditto/ditto/pull/2136) fixes an issue where the error message of a "dry-run" 
[migration of a thing's definition](httpapi-concepts.html#things-in-api-2---migrate-thing-definitions) (added in 
Ditto 3.7.0) was not correctly returned in case of validation errors.

#### Fix pre-defined extraFields caused missing extra data for thing creation events

The in Ditto 3.7.0 added configuration option to configure 
[pre-defined extraFields](installation-operating.html#pre-defined-extra-fields-configuration) caused for creation of
things that the `extra` fields were missing in the created event. This was fixed in PR [#2137](https://github.com/eclipse-ditto/ditto/pull/2137).

#### Fix that kamon configuration was not loaded

PR [#2140](https://github.com/eclipse-ditto/ditto/pull/2140) fixes that the Kamon configuration was not loaded correctly.

#### Fix that HTTP push connection issues with slow target endpoints

Issue [#2138](https://github.com/eclipse-ditto/ditto/issues/2138) brought up that Ditto managed [HTTP-push](connectivity-protocol-bindings-http.html)
connections could drop messages under load and/or slowly responding HTTP target endpoints.  
The exiting configuration for scaling the thread pool were wrong, which was fixed in PR [#2139](https://github.com/eclipse-ditto/ditto/pull/2139).

