---
title: Release notes 1.0.0
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 1.0.0 of Eclipse Ditto, released on 2019/12/12"
permalink: release_notes_100.html
---

This is Ditto's first major release which is tied to project graduation in Eclipse IoT.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip) 
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights 
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly 
investigated."

## What's in this release?

Eclipse Ditto 1.0.0 focuses on the following areas:
                               
{% include warning.html content="
If you want to upgrade an existing Ditto 1.0.0-M2 installation, the following database migration has to be done 
before upgrading: **Follow the steps documented in [the migration notes](#migration-notes)**." %}


### Changelog

Compared to the latest milestone release [1.0.0-M2](release_notes_100-M2.html), the following changes, new features and
bugfixes were added.


#### Changes

##### [Remove suffixed collections](https://github.com/eclipse/ditto/issues/537)

We removed suffixed collection support from Things and Policies persistence.
These collections do not scale well with increased amount of namespaces and lead to massive problems with mongodb as 
sharding can't be used.


#### New features


#### Bugfixes


### Migration notes

Because we removed support for suffixed collections with this release, an offline migration with the provided script 
is needed.

{% include file.html title="MongoDB migration script" file="migration_mongodb_1.0.0.js" %}

The script will copy all Thing and Policy events and snapshots from suffixed collections to one journal for each entity,
e.g. from things_journal@org.eclipse.ditto and things_journal@org.eclipse.hono to things_journal.

1. Completely stop Ditto.
2. Execute the migration script via mongo shell.
3. Update Ditto to 1.0.0-M3.
4. Start Ditto.

## Roadmap

The Ditto project plans on releasing (non-milestone releases) twice per year, once every 6 months.
