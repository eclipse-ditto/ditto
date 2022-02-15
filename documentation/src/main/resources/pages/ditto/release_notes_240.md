---
title: Release notes 2.4.0
tags: [release_notes]
published: false
keywords: release notes, announcements, changelog
summary: "Version 2.4.0 of Eclipse Ditto, released on ??.??.2022"
permalink: release_notes_240.html
---

Ditto **2.4.0** is API and [binary compatible](https://github.com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Eclipse Ditto 2.4.0 includes the following topics/enhancements:

The following notable fixes are included:

The following non-functional work is also included:

<br/>

For a complete list of all merged PRs, inspect the following milestones:
* [merged pull requests for milestone 2.4.0](https://github.com/eclipse/ditto/pulls?q=is:pr+milestone:2.4.0)

<br/>
<br/>

Compared to the latest release [2.3.0](release_notes_230.html), the following most notable changes, new features and
bugfixes were added.

### Changes

#### [Upgrade to Java 17](https://github.com/eclipse/ditto/issues/1283)

We upgraded the compiler target level for our service modules from 11 to 17 and also use a Java 17 runtime environment
for our service containers. Please note that the Ditto model still remains compatible to Java 8.
This change only affects you when you're building and deploying Ditto on your own.

### New features


### Bugfixes

Several bugs in Ditto 2.2.x were fixed for 2.4.0.
This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.4.0), including the fixed bugs.


## Migration notes

Migrations required updating from Ditto 2.3.x:
* With this release we not only switched from Java 11 to Java 17 but also from OpenJ9 to Hotspot.
  This means that the environment variable OPENJ9_JAVA_OPTIONS needs to be renamed to JAVA_TOOL_OPTIONS.
  Make sure that all options that are defined are valid for the Hotspot JVM.

## Ditto clients

For a complete list of all merged client PRs, inspect the following milestones:
* [merged pull requests for milestone 2.4.0](https://github.com/eclipse/ditto-clients/pulls?q=is:pr+milestone:2.4.0)

### Ditto Java SDK

No mentionable changes/enhancements/bugfixes.

### Ditto JavaScript SDK

See separate [Changelog](https://github.com/eclipse/ditto-clients/blob/master/javascript/CHANGELOG.md) of JS client.


## Roadmap

Looking forward, the current plans for Ditto 2.5.0 are:
