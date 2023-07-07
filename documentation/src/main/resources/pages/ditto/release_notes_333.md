---
title: Release notes 3.3.3
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.3.3 of Eclipse Ditto, released on 07.07.2023"
permalink: release_notes_333.html
---

This is a bugfix release, no new features since [3.3.2](release_notes_332.html) were added.

## Changelog

Compared to the latest release [3.3.2](release_notes_332.html), the following changes and bugfixes were added.

### Changes


### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A3.3.3).

#### [Fix that ImmutablePolicyImports.getPolicyImport with arbitrary CharSequence did not work](https://github.com/eclipse-ditto/ditto/pull/1674)

A model class' implementation signature did not work when using plain `CharSequence` instead of using an
instance of `PolicyId` (inheriting from `CharSequence`).

#### [Fix JsonSchema for policy "announcements"](https://github.com/eclipse-ditto/ditto/pull/1675)

The JsonSchema documentation for the "announcement" part of a Policy was wrongly documented / missing.

#### [Fix non-correct WARN log about potentially failing connections status](https://github.com/eclipse-ditto/ditto/pull/1678)

When connections did not contain any `target`, a `WARN` message was logged on every status-check of the
connection due to a wrong assumption in how the warning message is determined.

#### [Fix MergeThing command being used for creating new things did not respect "inlinePolicy"](https://github.com/eclipse-ditto/ditto/pull/1680)

After the addition of the [functionality to create a thing when it did not yet exist when using a "merge thing" command](https://github.com/eclipse-ditto/ditto/issues/1614)
which was added to Ditto 3.3.0, using an inline policy `"_policy"` and creating an inline policy as part of creating the
thing did not yet work.

### Helm Chart

The [Ditto Helm Chart](https://github.com/eclipse-ditto/ditto/tree/master/deployment/helm), which was enhanced and changed 
a lot for version 3.3.0, contained some configuration bugs which are also addressed with this bugfix release.

#### [Allow connectivity inter cluster communication](https://github.com/eclipse-ditto/ditto/pull/1676)

By default, allow connecting to hostnames local in the Kubernetes cluster where Ditto is deployed.  
Previously, this was disabled by default.
