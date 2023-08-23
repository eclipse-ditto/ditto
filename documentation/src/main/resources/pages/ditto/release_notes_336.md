---
title: Release notes 3.3.6
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.6 of Eclipse Ditto, released on 23.08.2023"
permalink: release_notes_336.html
---

This is a bugfix release, no new features since [3.3.5](release_notes_335.html) were added.

## Changelog

Compared to the latest release [3.3.5](release_notes_335.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.6).

#### [Fixed providing Ditto Adaptable information in the "_context" of an SSE event](https://github.com/eclipse-ditto/ditto/pull/1716)

Previously, the optional `_context` of an SSE subscription only contained the headers.

The fix also adds (same as for the [`NormalizedMessageMapper`](connectivity-mapping.html#normalized-mapper)) the `topic`
and `path` of the underlying Ditto protocol message.

#### [Fixed using "/cloudevents" endpoint for other entities than things](https://github.com/eclipse-ditto/ditto/pull/1724)

When sending `Policy` commands via the `POST /cloudevents` endpoint, the HTTP response was never completed successfully, 
as the cloudevents implementation made the assumption to always handle thing commands only.  
This has been fixed, now also Policy commands may be sent via the Cloud Events endpoint.


### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm), which was enhanced and changed 
a lot for version 3.3.0, contained some configuration bugs which are also addressed with this bugfix release.

#### [Support for basic nginx-ingress authentication](https://github.com/eclipse-ditto/ditto/pull/1702)

When using "Ingress" in the Ditto Helm chart, now also users via nginx "Basic auth" can be authenticated.
