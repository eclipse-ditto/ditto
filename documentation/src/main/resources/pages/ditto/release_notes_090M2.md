---
title: Release notes 0.9.0-M2
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.9.0-M2 of Eclipse Ditto, released on 29.04.2019"
permalink: release_notes_090-M2.html
---

This second milestone of the "0.9.0" release provides closes more gaps towards the upcoming release.


## Changes

### [Make it easy to add new Authentication mechanisms and allow chaining them](https://github.com/eclipse-ditto/ditto/issues/348)

If we have multiple applicable authentication mechanisms all of them are processed until either one of them 
authenticates successfully or all of them fail.

### [Unify implementations of things-search across API versions](https://github.com/eclipse-ditto/ditto/pull/392)

Ditto's "search" capabilities were rewritten so that bot API v1 and API v2 use the same MongoDB based search index.

{% include warning.html content="If you want to upgrade an existing Ditto installation, the following database 
        migration has to be done: **Follow the steps documented in the migration notes**." %}


## New features


### Various contributions for a setup of Eclipse Ditto on Microsoft Azure

As discussed in the ongoing issue [Eclipse Ditto on Microsoft Azure](https://github.com/eclipse-ditto/ditto/issues/358) Microsoft
added a few PRs in order to deploy Ditto on MS Azure cloud:

* [Enable support for MongoDB persistence in K8s](https://github.com/eclipse-ditto/ditto/pull/364)
* [Fix Nginx connectivity after Helm update](https://github.com/eclipse-ditto/ditto/pull/375)
* [Recover from closed JMS AMQP message producer](https://github.com/eclipse-ditto/ditto/pull/367)
* [Improve nginx configuration for gateway restarts](https://github.com/eclipse-ditto/ditto/pull/386)


## Bugfixes


### [MQTT publish fails if no sources are configured](https://github.com/eclipse-ditto/ditto/issues/387)

Fixed: If using a MQTT connection that only has targets but no sources, publishing of events will fail.

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.9.0-M2+).

