---
title: Release notes 3.5.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.5.0 of Eclipse Ditto, released on 26.01.2024"
permalink: release_notes_350.html
---

The team behind Eclipse Ditto is happy to announce Ditto version 3.5.0.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.5.0 focuses on the following areas:

* **Search in the history** of a **single thing** using an RQL filter
* **Configure per namespace** the **fields to index** in Ditto's **search index**
* Configure **defined search count queries** to be **exposed as Prometheus metrics** by Ditto periodically
* Providing **new placeholder functionality** to the **time placeholder**, being able to **add and subtract to/from 
  the current time** and to truncate the time to a given unit
* Enhance **WoT (Web of Things) JSON skeleton creation** to be able to **fail with an exception** on **invalid** WoT models
* Provide **negative numbers** when **querying for the historical events** of an entity (thing, policy, connection) in order to
  **e.g. get "latest 10" events**
* UI enhancements:
  * Show **policy imports** in Ditto explorer UI
  * Enhance UI **Operations** functionality to be able to **perform devops/piggyback commands**
  * Allow **editors in UI** to toggle **full screen mode**
  * **Display attributes in UI** inside a **JSON editor** in order to correctly display structured JSON payloads
  * Enhance "**Incoming Thing Updates**" section by **displaying "Action" and "Path" in the table** and adding a **dropdown to 
    select the amount of details** to show per event
  * Add **client side filter option** for filtering **Incoming Thing Updates** and **Connection logs**

The following non-functional work is also included:

* Configured docker-compose to by default retain only the last 50m of log messages per Ditto service
* Migrated SLF4J to version 2.x and logback to version 1.4.x
* Benchmark tool improvements and fixes
* Improve cluster stability when running in Kubernetes, e.g. on updates or k8s node-shutdowns

The following notable fixes are included:

* Fix enriching Thing creation events with the inlined `_policy`
* Fixed that Ditto's own calculated "health" was not exposed to the `/alive` endpoint scraped by Kubernetes to check for
  aliveness of single services
* Fixed that no cache was used when updating the search index when an "imported" policy was modified

### New features

#### Search in the history of a single thing using an RQL filter

With [#1583](https://github.com/eclipse-ditto/ditto/issues/1583) being implemented, Ditto is now able to search the 
history for a single thing for a given RQL query.

With this, it is _for example_ possible to detect when a thing was modified to a certain value or when a certain part of
the thing was updated:

```
# find out when the policy of a thing was changed:
GET ("Accept": "text/event-stream") 
  /api/2/things/namespace:my-thing1?from-historical-revision=1&filter=exists(policyId)

# stream all events when the thing's measured temperature was above a certain threshold:
GET ("Accept": "text/event-stream") 
  /api/2/things/namespace:my-thing1?from-historical-revision=1&filter=gt(features/temperatureSensor/properties/temp,23.42)
```

#### Configure per namespace the fields to index in Ditto's search index

With [#1521](https://github.com/eclipse-ditto/ditto/issues/1521), resolved by a contribution by a Ditto community member 
done in [#1870](https://github.com/eclipse-ditto/ditto/pull/1870), it is now possible to configure which JSON parts of
the things in certain namespaces should be indexed by the Ditto [search](basic-search.html).

By default, Ditto adds all the JSON fields of things to the search index, so that [search queries](basic-search.html#rql)
and sorting can be done efficiently on any fields of the things.  
This approach however can become a challenge when thing contain a large payload (e.g. greater 10kB or even 50kB). In such
cases, the load to the search index caused by updates can become very CPU intensive (on MongoDB side).

For large payload things it however also is often not required to have all fields indexed by the search and only have e.g.
`attributes` or certain [features](basic-feature.html) added to the index.

How this can be configured can be found in the [added documentation](installation-operating.html#limiting-indexed-fields).

#### Configure defined search count queries to be exposed as Prometheus metrics by Ditto periodically

With [#1806](https://github.com/eclipse-ditto/ditto/issues/1806) in place, Ditto's [search service](architecture-services-things-search.html)
can be configured to periodically perform [search count queries](basic-search.html#search-count-queries) and expose the
results as metric via its Prometheus endpoint.

Examples for such "operator defined metrics could be":
* count the total amount of managed Things in Ditto
* count the total amount of certain Thing "types"
* count how many Things were modified within the last day

The configuration documentation [can be found here](installation-operating.html#operator-defined-custom-metrics).

#### Providing new placeholder functionality to the time placeholder, being able to add and subtract to/from the current time

... and to truncate the time to a given unit.  
In [#1854](https://github.com/eclipse-ditto/ditto/issues/1854), new features were added to the existing `time:now` 
[placeholder](basic-placeholders.html#time-placeholder), namely:
* adding or subtracting time intervals to the current time
* truncating either the current time or the calculated time to a given unit (e.g. to current year, month, day, hour, minute, second)

#### Enhance WoT (Web of Things) JSON skeleton creation to be able to fail with an exception on invalid WoT models

With [#1656](https://github.com/eclipse-ditto/ditto/issues/1656) resolved, it is now possible to "fail" thing creations
[referencing a WoT ThingModel](basic-wot-integration.html#thing-skeleton-generation-upon-thing-creation) when a TM was
references which:
* could not be found/resolved/retrieved
* was not a valid WoT TM
* ...

The previous behavior was to ignore such errors and do not generate a JSON skeleton.  
This standard behavior now is to fail, but this can be configured via environment variable `THINGS_WOT_TM_BASED_CREATION_THING_THROW_EXCEPTION_ON_WOT_ERRORS`.

#### Provide negative numbers when querying for the historical events of an entity

In [#1866](https://github.com/eclipse-ditto/ditto/pull/1866) we added the option to provide negative numbers or `0` when
[querying for the historical events](basic-history.html#streaming-historical-events-of-entity).  
With that, it is e.g. possible to only stream the last 50 events by specifying:
* `from-historical-revision=-50`
* `to-historical-revision=0`


#### Enhancements in Ditto explorer UI

This release contains many feature additions, improvements and fixes in the Ditto UI.  
In detail, the following improvements and fixes were added:  
* [Show policy imports in Ditto explorer UI](https://github.com/eclipse-ditto/ditto/issues/1700)
* [Enhance UI Operations functionality to be able to perform devops/piggyback commands](https://github.com/eclipse-ditto/ditto/pull/1791)
* [Allow editors in UI to toggle full screen mode](https://github.com/eclipse-ditto/ditto/pull/1805)
* [Display attributes in UI inside a JSON editor in order to correctly display structured JSON payloads](https://github.com/eclipse-ditto/ditto/pull/1812)
* [Enhance "Incoming Thing Updates" section by displaying "Action" and "Path" in the table and adding a dropdown to select the amount of details to show per event](https://github.com/eclipse-ditto/ditto/pull/1813)
* [Add client side filter option for filtering Incoming Thing Updates and Connection logs](https://github.com/eclipse-ditto/ditto/issues/1818)
* [Enhance things search slot by displaying the amount of matching things](https://github.com/eclipse-ditto/ditto/pull/1864)


### Changes

#### Configured docker-compose to by default retain only the last 50m of log messages per Ditto service

With [#1831](https://github.com/eclipse-ditto/ditto/pull/1831), when now running Ditto e.g. locally via `docker-compose` 
[deployment](https://github.com/eclipse-ditto/ditto/blob/master/deployment/docker/docker-compose.yml),
the logs the services produced are now limited by default to 50mB - before there was no limitation in place and that 
could have caused "full disks".

#### Migrated SLF4J to version 2.x and logback to version 1.4.x

In [#1832](https://github.com/eclipse-ditto/ditto/pull/1832) Ditto updated to SLF4J 2.x and latest logback version 1.4.x.

#### Benchmark tool improvements and fixes

In [#1849](https://github.com/eclipse-ditto/ditto/pull/1849) further improvements and clarifications for the benchmark
tool were provided.


### Bugfixes

#### Fix enriching Thing creation events with the inlined `_policy`

In [#1863](https://github.com/eclipse-ditto/ditto/pull/1863) it was fixed that for thing creation events it was not 
possible to get the created policy via `_policy` [enriched extra field](basic-enrichment.html).

#### Fixed that Ditto's own calculated "health" was not exposed to the `/alive` endpoint scraped by Kubernetes to check for aliveness of single services

In [#1865](https://github.com/eclipse-ditto/ditto/issues/1865) it was reported that the Ditto internal "health" check
is not exposed to the `/alive` route, which is used by Kubernetes for detecting "aliveness" of Ditto and e.g. restarting
unhealthy pods.  
This was fixed in [#1867](https://github.com/eclipse-ditto/ditto/pull/1867).

#### Fixed that no cache was used when updating the search index when an "imported" policy was modified

We found out in [#1869](https://github.com/eclipse-ditto/ditto/issues/1869) that when using 
[Policy imports](basic-policy.html#policy-imports) and changing an imported policy, the [Ditto search service](architecture-services-things-search.html)
did correctly invalidate all policies importing from that changed imported policy, however no cache was used in order
to look up the imported policy for finding out its revision number.  
That could have led to a lot of policy retrievals in the cluster, leading to timeouts and errors.


### Helm Chart

#### Improve cluster stability when running in Kubernetes, e.g. on updates or k8s node-shutdowns

When running in Kubernetes, rolling updating to a new Ditto version can cause problems when the Ditto cluster is under
load, reported in [#1839](https://github.com/eclipse-ditto/ditto/issues/1839).  
This should improve via the Helm chart addition done in [#1871](https://github.com/eclipse-ditto/ditto/pull/1871), which
annotates the "oldest" members of the cluster with higher `pod-deletion-cost` so that they are downed last during a 
rolling update.


#### Fix helm chart open shift indentation

In [#1875](https://github.com/eclipse-ditto/ditto/pull/1875) the indentation for OpenShift based k8s deployments of the
Helm chart was fixed.


## Migration notes

There are no migration steps known when updating from Ditto 3.4.x to Ditto 3.5.0.
