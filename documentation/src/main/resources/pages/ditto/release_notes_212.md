---
title: Release notes 2.1.2
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 2.1.2 of Eclipse Ditto, released on 12.11.2021"
permalink: release_notes_212.html
---

This is a bugfix release, no new features since [2.1.1](release_notes_211.html) were added.

## Changelog

Compared to the latest release [2.1.1](release_notes_211.html), the following bugfixes were added.

### Bugfixes

This is a complete list of the
[merged pull requests](https://github.com/eclipse/ditto/pulls?q=is%3Apr+milestone%3A2.1.2), including the fixed bugs.

#### [Improve performance of JWT validations by caching jwt validator](https://github.com/eclipse/ditto/pull/1217)

For some users of Ditto, the performance of JWT validations was dramatically bad. This bugfix caches the creation of 
the JWT parser based on the public key and should dramatically improve the performance of HTTP requests using JWT.

#### [Improve Kafka consumer performance](https://github.com/eclipse/ditto/pull/1218)

This bugfix should reduce the CPU load in the connectivity service and number of requests/second to the Kafka broker by 
increasing `fetch.max.wait.ms` to 5 seconds.  
It should also reduce the consumer lag due to a lack of threads when there are a lot of consumers running.

#### [Keep order of json elements in connection model in set structures](https://github.com/eclipse/ditto/pull/1219)

Previously, the JSON element order e.g. in arrays in a managed `connection` could be mixed up, e.g. from creation to
persistence. This has been fixed by maintaining the JSON element order in the connection model.

#### [Updated to Akka HTTP 10.2.7 due to critical reported CVE](https://github.com/eclipse/ditto/pull/1222)

The for Ditto's HTTP API used library contained a critical security issue 
[CVE-2021-42697](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-42697) which has been resolved in Akka HTTP 10.2.7 
