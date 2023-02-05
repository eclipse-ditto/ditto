---
title: Release notes 0.2.0-M1
tags: [release_notes, search]
keywords: release notes, announcements, changelog
summary: "Version 0.2.0-M1 of Eclipse Ditto, released on 07.02.2018"
permalink: release_notes_020-M1.html
---

Since the last milestone of Eclipse Ditto [0.1.0-M3](release_notes_010-M3.html), the following new features and
bugfixes were added.


## New features

### [Search in namespaces](https://github.com/eclipse-ditto/ditto/pull/104)

A query parameter `namespaces` was added to the [HTTP search API](httpapi-search.html).
It can be used in order to restrict search to Things within specific namespaces. For example, with the route

```
/search/things?namespaces=john,mark
```

only Things with IDs of the form `john:<id-suffix>` and `mark:<id-suffix>` are returned as results.

Namespace restriction happens at the start of a search query execution and may speed up a search queries considerably.

### [Feature Definition](https://github.com/eclipse-ditto/ditto/issues/60)

Ditto's model (to be precise the `Feature`) was enhanced by a `Definition`. This field is intended to store which 
contract a Feature follows (which state and capabilities can be expected from a Feature).

The Java model, HTTP API and Ditto Protocol were enhanced (in a non-API breaking way) to now contain that field.

For more information about the Feature Definition and how it can in future be used together with Eclipse Vorto, have 
a look at [its documentation](basic-feature.html#feature-definition). 


## Bugfixes

### [AMQP 1.0 failover is not working](https://github.com/eclipse-ditto/ditto/issues/97)

Using `"failover": true` when creating a new AMQP 1.0 connection caused that the connection could not be established. 


### Various smaller bugfixes

This is a complete list of the [merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.2.0-M1+).


## Documentation

Continuously improve and enhance the existing documentation.
