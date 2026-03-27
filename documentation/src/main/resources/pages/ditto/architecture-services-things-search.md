---
title: Things-Search service
keywords: architecture, service, things-search, search
tags: [architecture, search]
permalink: architecture-services-things-search.html
---

The Things-Search service maintains an optimized search index of all Things and executes search queries against it.

{% include callout.html content="**TL;DR**: The Things-Search service listens for Thing and Policy change events, updates a search-optimized MongoDB collection, and processes RQL search queries to find matching Things." type="primary" %}

## Overview

The Things-Search service has two main responsibilities:

1. **Index maintenance**: Track changes to Things, Features, and Policies by consuming [events](basic-signals-event.html) from the [Things](architecture-services-things.html) and [Policies](architecture-services-policies.html) services, and keep a search-optimized index up to date.
2. **Query execution**: Accept search queries and return matching Thing IDs from the index.

## How it works

### Model

The Things-Search service does not define its own entity model. It uses the models from the [Things](architecture-services-things.html) and [Policies](architecture-services-policies.html) services.

It does include a parser that transforms [RQL](basic-rql.html) search queries into a Java domain model:

* [RQL parser AST](https://github.com/eclipse-ditto/ditto/tree/master/rql/model/src/main/java/org/eclipse/ditto/rql/model/predicates/ast)

### Signals

Other services communicate with the Things-Search service via:

* [Commands](https://github.com/eclipse-ditto/ditto/tree/master/thingsearch/model/src/main/java/org/eclipse/ditto/thingsearch/model/signals/commands): Search commands and their responses

### Persistence

The Things-Search service maintains its own MongoDB collections optimized for full-text and attribute-based search:

| Collection | Purpose |
|------------|---------|
| `search` | The search index containing Thing data in a search-optimized format |
| `searchSync` | A single-document capped collection that records the instant until which Thing events are indexed |

## Further reading

* [Search concept](basic-search.html)
* [Architecture Overview](architecture-overview.html)
* [Things Service](architecture-services-things.html)
* [Policies Service](architecture-services-policies.html)
