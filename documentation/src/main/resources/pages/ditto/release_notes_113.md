---
title: Release notes 1.1.3
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.1.3 of Eclipse Ditto, released on 20.07.2020"
permalink: release_notes_113.html
---

This is a bugfix release, no new features since [1.1.2](release_notes_112.html) were added.

## Changelog

Compared to the latest release [1.1.2](release_notes_112.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A1.1.3), including the fixed bugs.


#### [Do not decided based on the response if it was required](https://github.com/eclipse-ditto/ditto/pull/734)

Fixed [Responses should not decide whether they're required](https://github.com/eclipse-ditto/ditto/issues/677).

#### [Include fields query parameter when retrieving feature properties](https://github.com/eclipse-ditto/ditto/pull/727)

There was an issue where the fields query parameter wasn't taken into account when retrieving feature properties.

#### [Delegate default options from wrapping message mapper to wrapped message mapper](https://github.com/eclipse-ditto/ditto/pull/723)

The WrappingMessageMapper didn't delegate the default options to the message mapper it was wrapping.

#### [Improved error message for unknown/invalid host names in a connection configuration](https://github.com/eclipse-ditto/ditto/pull/676)

An unknown/invalid host in a connection configuration caused an exception with an error message that did not indicate the actual cause.

#### [Reworked reconnect behaviour of java client](https://github.com/eclipse-ditto/ditto-clients/pull/64)

There were reported issues with the reconnecting behaviour of the java client. We improved the reconnecting behaviour, so it should be more reliable.

#### [Added org.reactivestreams to osgi imports](https://github.com/eclipse-ditto/ditto-clients/pull/73)

The package `org.reactivestreams` was missing in the OSGI imports of our java client.
