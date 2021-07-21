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
* executing search queries against the search index in order to find out which `Things` match a given search

## Model

The things-search service has no model (entity) by its own, but uses the model of [things](architecture-services-things.html) 
and [policies](architecture-services-policies.html) services.

It however contains a model which can transform an <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.rql}}">RQL</a> 
search query into a Java domain model which is defined here:


* [rql parser ast](https://github.com/eclipse/ditto/tree/master/rql/model/src/main/java/org/eclipse/ditto/rql/model/predicates/ast)

## Signals

Other services can communicate with the things-search service via:


* [commands](https://github.com/eclipse/ditto/tree/master/thingsearch/model/src/main/java/org/eclipse/ditto/thingsearch/model/signals/commands):
  containing commands and command responses which are processed by this service

## Persistence

The Things-Search service maintains its own persistence in which it stores `Things` in an optimized way in order to 
provide a full search on arbitrary `Thing` data. 

Things-Search creates the following MongoDB collections:

* `searchThings`: The search index.
* `searchThingsSyncThings`: A single-document capped collection containing the instant until which `Thing` events are
indexed for sure; expected to be 30 minutes before the current time.
* `searchThingsSyncPolicies`: A single-document capped collection containing the instant until which `Policy` events
are indexed for sure; expected to be 30 minutes before the current time.

## Migration from Ditto 0.9.0-M1

The index schema has changed since Ditto version 0.9.0-M1. Data migration is obligatory to upgrade an existing
installation running Ditto version 0.9.0-M1 or earlier. Expected duration of data migration is 1/60th of the lifetime
of the Ditto installation.

1. *After* stopping the cluster of Ditto 0.9.0-M1, drop unnecessary collections:
```javascript
db.getCollection('thingEntities').drop();
db.getCollection('policyBasedSearchIndex').drop();
db.getCollection('thingsSearchSyncStatePolicies').drop();
```

2. *Before* starting the upgraded Ditto cluster, write into `searchThingsSyncThings` the timestamp when the Ditto cluster started for the first time:
```javascript
var startingTimestamp = new Date(TIMESTAMP-WHEN-DITTO-CLUSTER-STARTED-FOR-THE-FIRST-TIME); // e.g. new Date('2019-01-01T00:00:00.000Z')
db.getCollection('thingsSearchSyncStateThings').renameCollection('searchThingsSyncThings');
db.getCollection('searchThingsSyncThings').insert({'ts':startingTimestamp});
```

3. Start the upgraded Ditto cluster. All `Thing` events persisted after the timestamp in `searchThingsSyncThings` 
will be indexed in the background.
