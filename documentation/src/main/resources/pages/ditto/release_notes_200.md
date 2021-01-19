---
title: Release notes 2.0.0 
tags: [release_notes]
published: true 
keywords: release notes, announcements, changelog 
summary: "Version 2.0.0 of Eclipse Ditto, released on xx.xx.2021"
permalink: release_notes_200.html
---

Ditto **2.0.0** is API and [binary compatible](https://github.
com/eclipse/ditto/blob/master/documentation/src/main/resources/architecture/DADR-0005-semantic-versioning.md)
to prior Eclipse Ditto 2.x versions.

## Changelog

Compared to the latest release [1.5.0](release_notes_150.html), the following changes, new features and bugfixes were
added.

{% include warning.html content="If you want to upgrade an existing Ditto installation to 2.0.0, the migration has to be
done before upgrading: **Follow the steps documented in [the migration notes](#migration-notes)**." %}

### Changes

#### [change](https://github.com/eclipse/ditto/pull/000)

...

### New features

#### [Merge updates](https://github.com/eclipse/ditto/issues/288)

This new feature allows updating parts of a thing without affecting existing parts. You may now for example update an
attribute, add a new property to a feature and delete a property of a different feature in a _single request_. See
[Merge updates](httpapi-concepts.html#merge-updates) for more details and an example.

### Bugfixes

Several bugs in Ditto 1.5.0 were fixed for 2.0.0.<br/>
This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.0.0), including the fixed bugs.
<br/>
Here as well for the Ditto Java Client: [merged pull requests](https://github.com/eclipse/ditto-clients/pulls?
q=is%3Apr+milestone%3A2.0.0)

#### [fix1](https://github.com/eclipse/ditto/pull/000)

....

#### [Ditto Java client: fix2](https://github.com/eclipse/ditto-clients/pull/000)

...

## Migration notes

### migration...

.....
