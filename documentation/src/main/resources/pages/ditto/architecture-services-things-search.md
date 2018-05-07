---
title: Things-Search service
keywords: architecture, service, things-search, search
tags: [architecture, search]
permalink: architecture-services-things-search.html
---

The "things-search" service takes care of:


* updating an optimized search index of `Things` based on the [events](basic-signals-event.html) emitted by the 
  [things](architecture-services-things.html) and [policies](architecture-services-policies.html) services when entites
  are changed there
* executing search queries against the search index in order to find out which `Things` match a given search

## Model

The things-search service has no model (entity) by its own, but uses the model of [things](architecture-services-things.html) and 
[policies](architecture-services-policies.html) services.

It however contains a model which can transform an <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> 
search query into a Java domain model which is defined here:


* [rql parser ast](https://github.com/eclipse/ditto/tree/master/model/thingsearch-parser/src/main/java/org/eclipse/ditto/model/thingsearchparser/predicates/ast)

## Signals

Other services can communicate with the things-search service via:


* [commands](https://github.com/eclipse/ditto/tree/master/signals/commands/thingsearch/src/main/java/org/eclipse/ditto/signals/commands/thingsearch):
  containing commands and command responses which are processed by this service

## Persistence

The things-search service maintains its own persistence in which it stores `Things` in an optimized way in order to 
provide a full search on arbitrary `Thing` data. 
