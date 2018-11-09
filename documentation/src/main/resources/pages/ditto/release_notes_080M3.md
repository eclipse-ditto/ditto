---
title: Release notes 0.8.0-M3
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.8.0-M3 of Eclipse Ditto, released on <>"
permalink: release_notes_080-M3.html
---

## Changes

### Speed up Search

With more and more Things, the Search service was slowing down massively.

Two Pull Requests ([#275](https://github.com/eclipse/ditto/pull/275), [#278](https://github.com/eclipse/ditto/pull/278)) 
addressed this issue with the following changes:
* add an index on `_policyId` and `__policyRev` for the `thingEntities` collection.
* add the field `_thingId` to new documents in `policiesBasedSearchIndex`.
* add an index on `_thingId` for the `policiesBasedSearchIndex` collection.
* rewrite queries on `policiesBasedSearchIndex` to always look for the indexed `_thingId` first. 

**Since the access to the Search database was changed, data in `policiesBasedSearchIndex` needs to be
 migrated using the {% include file.html title="MongoDB migration script from 0.8.0-M2 to 0.8.0-M3" file="migration_mongodb_0.8.0-M2_0.8.0-M3.js" %}.**
