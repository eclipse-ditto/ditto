---
title: Release notes 3.5.10
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.10 of Eclipse Ditto, released on 18.06.2024"
permalink: release_notes_3510.html
---

This is a bugfix release, no new features since [3.5.9](release_notes_359.html) were added.

## Changelog

Compared to the latest release [3.5.9](release_notes_359.html), the following changes and bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.5.10).

#### Updating used `pekko-persistence-mongo` and mongo driver to latest versions

The used Pekko persistence plugin [pekko-persistence-mongo](https://github.com/scullxbones/pekko-persistence-mongo) provides
in its newest version `1.2.0` more control of the MongoDB `writeConcern` to use.  
Previously, e.g. the configured [Acknowledged](https://github.com/scullxbones/pekko-persistence-mongo?tab=readme-ov-file#writeconcern) 
write concern caused a different behavior than in MongoDB 5 when using Ditto with MongoDB 6.  

Previously, the configured `Acknowledged` writeConcern only required the MongoDB primary to acknowledge the write operation.  
With MongoDB 6, this behavior seem to have changed to use the configured "default write concern" of the database, which by
default is `"majority"`.

Ditto defaults to this `Acknowledged` writeConcern, so when updating from MongoDB 5 to 6 an increase in insert performance
is probable, although the writes are then performed with higher consistency guarantees.

Ditto 3.5.10 provides updates the `pekko-persistence-mongo` to version `1.2.0` and provides with this update the ability 
to also configure 3 additional writeConcerns:
* `W1` - requires that the MongoDB primary acknowledges the write
* `W2` - requires that the MongoDB primary and an additional secondary acknowledges the write
* `W3` - requires that the MongoDB primary and two additional secondaries acknowledges the write

Where `W1` should provide the behavior as `Acknowledged` before: only requiring the acknowledgement from the MongoDB primary.

As part of this update, also the MongoDB driver was updated to version `5.1.1`.
