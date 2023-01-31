---
title: Release notes 1.0.0
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.0.0 of Eclipse Ditto, released on 12.12.2019"
permalink: release_notes_100.html
---

This is Ditto's first major release which is tied to project graduation in Eclipse IoT.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip) 
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights 
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly 
investigated."

## Changelog

Eclipse Ditto 1.0.0 focuses on the following areas:

* Addition of "definition" field in thing at model level containing the model ID a thing may follow
* Improved connection response handling/mapping

{% include warning.html content="
If you want to upgrade an existing Ditto installation to 1.0.0, the following database migration has to be done 
before upgrading: **Follow the steps documented in [the migration notes](#migration-notes)**." %}


Compared to the latest milestone release [1.0.0-M2](release_notes_100-M2.html), the following changes, new features and
bugfixes were added.


### Changes

#### [Remove suffixed collections](https://github.com/eclipse-ditto/ditto/issues/537)

We removed suffixed collection support from Things and Policies persistence.
These collections do not scale well with increased amount of namespaces and lead to massive problems with mongodb as 
sharding can't be used.


### New features

#### [Comprehensive support for command responses](https://github.com/eclipse-ditto/ditto/issues/540)

Adds the possibility to define a "reply target" for [connection sources](basic-connections.html#sources) where 
* the response address may be configured
* response header mappings may be configured

Both accepting placeholders, so e.g. with that feature it is possible to send replies whenever an incoming command 
specified a `reply-to` address.

Used in combination with [Eclipse Hono](https://eclipse.org/hono/)  it is possible to send responses to devices which 
e.g. need to retrieve data from Ditto.

#### [Add "definition" to Thing in order to reference used model](https://github.com/eclipse-ditto/ditto/issues/247)

In order to specify which model a Thing follows, the JSON of the Thing entity was enhanced with a single string for 
`"definintion"`. This can e.g. be used in order to place an [Eclipse Vorto](https://eclipse.org/vorto/) 
"Information Model" reference to a Thing.

### Bugfixes

#### [Fixed NullPointer in StreamingSessionActor](https://github.com/eclipse-ditto/ditto/pull/546)

When closing a WebSocket session, a `NullPointerException` occurred which is fixed now.

## Migration notes

OpenID Connect URLs are now prefixed with `https://` per default. Any configured URLs containing `https://` will break the configuration.
Instead of `https://auth.eclipse.de/auth/realms/ditto` it has to be `auth.eclipse.de/auth/realms/ditto` instead.
The Configuration option is `ditto.gateway.authentication.oauth.openid-connect-issuers.myprovider`.

Because we removed support for suffixed collections with this release, an offline migration with the provided script 
is needed.

{% include file.html title="MongoDB migration script" file="migration_mongodb_1.0.0.js" %}

The script will copy all Thing and Policy events and snapshots from suffixed collections to one journal for each entity,
e.g. from things_journal@org.eclipse.ditto and things_journal@org.eclipse.hono to things_journal.

1. Completely stop Ditto.
2. Execute the migration script via mongo shell.
3. Update Ditto to 1.0.0.
4. Start Ditto.
