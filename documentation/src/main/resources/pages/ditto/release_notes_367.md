---
title: Release notes 3.6.7
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.6.7 of Eclipse Ditto, released on 11.01.2025"
permalink: release_notes_367.html
---

This is a bugfix release, no new features since [3.6.5](release_notes_365.html) were added.

## Changelog

Compared to the latest release [3.6.5](release_notes_365.html), the following changes and bugfixes were added.  
Release 3.6.6 was skipped due to a mistake in releasing.

### Bugfixes

The only fix of 3.6.7 is the fix of a log statement for failed WoT validation - in PR 
[#2087](https://github.com/eclipse-ditto/ditto/pull/2087) (included in 3.6.5), the `thingId` was added to the log message.  
This accidentally removed the exception from the log message which contained the detailed explanation what part was
not valid according to the model.

3.6.7 fixes just this log message.
