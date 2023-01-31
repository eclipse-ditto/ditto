---
title: Things-Search service
keywords: architecture, service, things-search, search
tags: [architecture, search]
permalink: architecture-services-things-search.html
---

The "things-search" service takes care of:

* updating an optimized search index of `Things` based on the [events](basic-signals-event.html) emitted by the 
  [things](architecture-services-things.html) and [policies](architecture-services-policies.html) services when entities
  are changed there
* executing search queries against the search index in order to find out which `Things` (which `thingId`s) match a 
  given search

## Model

The things-search service has no model (entity) by its own, but uses the model of [things](architecture-services-things.html) 
and [policies](architecture-services-policies.html) services.

It however contains a model which can transform an <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> 
search query into a Java domain model which is defined here:

* [rql parser ast](https://github.com/eclipse-ditto/ditto/tree/master/rql/model/src/main/java/org/eclipse/ditto/rql/model/predicates/ast)

## Signals

Other services can communicate with the things-search service via:


* [commands](https://github.com/eclipse-ditto/ditto/tree/master/thingsearch/model/src/main/java/org/eclipse/ditto/thingsearch/model/signals/commands):
  containing commands and command responses which are processed by this service

## Persistence

The Things-Search service maintains its own persistence in which it stores `Things` in an optimized way in order to 
provide a full search on arbitrary `Thing` data. 

Things-Search creates the following MongoDB collections:

* `search`: The search index.
* `searchSync`: A single-document capped collection containing the instant until which `Thing` events are
indexed for sure.

