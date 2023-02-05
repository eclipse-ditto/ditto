---
title: Release notes 0.8.0-M3
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.8.0-M3 of Eclipse Ditto, released on 14.11.2018"
permalink: release_notes_080-M3.html
---

Since the last milestone of Eclipse Ditto [0.8.0-M2](release_notes_080-M2.html), the following changes, new features and
bugfixes were added.


## Changes

### Speed up Search

With more and more Things, the search service was slowing down massively.

Two Pull Requests ([#275](https://github.com/eclipse-ditto/ditto/pull/275), [#278](https://github.com/eclipse-ditto/ditto/pull/278)) 
addressed this issue with the following changes:
* add an index on `_policyId` and `__policyRev` for the `thingEntities` collection.
* add the field `_thingId` to new documents in `policiesBasedSearchIndex`.
* add an index on `_thingId` for the `policiesBasedSearchIndex` collection.
* rewrite queries on `policiesBasedSearchIndex` to always look for the indexed `_thingId` first. 

{% include warning.html content="If you want to upgrade an existing Ditto installation, the following database 
        migration has to be done." %}

**Data in `policiesBasedSearchIndex` needs to be migrated (we strongly recommend to do that in "offline mode"
 with the Ditto cluster stopped) using the 
 {% include file.html title="MongoDB migration script from 0.8.0-M2 to 0.8.0-M3" file="migration_mongodb_0.8.0-M2_0.8.0-M3.js" %}.**

### [Netty 3 was removed from dependencies](https://github.com/eclipse-ditto/ditto/issues/161)

Due to licensing issues with Netty 3, it was removed in this release and replaced with 
[Akka's Artery](https://doc.akka.io/docs/akka/current/remoting-artery.html) remoting which uses by default a plain TCP 
socket for communication.

That means that a rolling update from a prior version of Ditto will fail - you'll have to completely restart your 
cluster with all services running the new version.


## New features

### [Apply enforcement for incoming messages in connectivity service](https://github.com/eclipse-ditto/ditto/issues/265)

When adding a [connection](connectivity-manage-connections.html), an optional enforcement (e.g. for 
[AMQP 1.0](connectivity-protocol-bindings-amqp10.html)) may be configured in order to only accept messages having, 
for example, a defined header value.

This is also very useful to be used for connecting to [Eclipse Hono](https://eclipse.org/hono/) which sends a header
`device_id` in every message which Ditto can check against the ID of the addressed twin. 

### [Allow to create a new thing that uses a copied policy](https://github.com/eclipse-ditto/ditto/issues/268)

When [creating a new Thing](protocol-specification-things-create-or-modify.html) it is now possible to copy the 
[Policy](basic-policy.html) already used in another Thing.

An example of this new feature can be found [here](protocol-examples-creatething.html#alternative-creatething-commands).


## Bugfixes

This milestone contains several bugfixes related to memory leaks, recovery of connections and cluster consistency.

### Various smaller bugfixes

This is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.8.0-M3+).

