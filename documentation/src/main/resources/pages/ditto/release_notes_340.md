---
title: Release notes 3.4.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.4.0 of Eclipse Ditto, released on 17.10.2023"
permalink: release_notes_340.html
---

The fourth minor release of Ditto 3.x, Eclipse Ditto version 3.4.0 is here.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."


## Changelog

Eclipse Ditto 3.4.0 focuses on the following areas:

* Supporting **HTTP `POST`** for performing **searches** with a very **long query**
* Addition of a **new placeholder** to use **in connections** to use **payload of the thing JSON** e.g. in headers or addresses
* New **placeholder functions** for **joining** multiple elements into a single string and doing **URL-encoding and -decoding**
* Configure **MQTT message expiry interval for published messages** via a header
* **Reduce patch/merge thing commands** to **modify** only the **actually changed values** with a new option
* UI enhancements:
  * Adding sending messages to Things
  * Made UI (at least navigation bar) responsive for small screen sizes
  * Increase size of JSON editors in "edit" mode

The following non-functional work is also included:

* **Swapping the [Akka toolkit](https://akka.io)** (because of its switch of license to [BSL License](https://www.lightbend.com/akka/license-faq) after Akka v2.6.x)
  **with its fork [Apache Pekko](https://pekko.apache.org/)** which remains Apache 2.0 licensed.
* Support for using **AWS DocumentDB** as a replacement for MongoDB
* Improve logging by adding the **W3C Trace Context** `traceparent` header as MDC field to logs
* Adjust handling of special MQTT headers in MQTT 5
* Optimize docker files
* Migration of Ditto UI to TypeScript
* There now is an official **[Eclipse Ditto Benchmark](2023-10-09-ditto-benchmark.html)** which shows how Ditto is able
  to scale horizontally and provides some tuning tips
* Addition of a **benchmark tooling** to run own Ditto benchmarks

The following notable fixes are included:

* Fixed that failed retrieval of a policy (e.g. after policy change) leads to search index being "emptied out"
* Fixed that putting metadata when updating a single scalar value did not work
* UI fix, fixing that patching a thing will null values did not reflect that change in the UI

### New features

#### Supporting HTTP `POST` for performing searches with a very long query

In [#1706](https://github.com/eclipse-ditto/ditto/pull/1706) support for the additional HTTP verb `POST` on the 
search HTTP API `/api/2/search/things` and `/api/2/search/things/count` was added.  
This is beneficial if the passed in [RQL search query](basic-search.html#rql) would get too long to send via query 
parameter of the `GET` verb.  
Documentation was added [here](httpapi-search.html#post) and in the [OpenAPI documentation](http-api-doc.html#/Things-Search/post_api_2_search_things).

#### Addition of a new placeholder to use in connections to use payload of the thing JSON e.g. in headers or addresses

For Ditto managed [connections](basic-connections.html) a new ["thing-json" placeholder](basic-placeholders.html#thing-json-placeholder)
was added, resolving issue [#1727](https://github.com/eclipse-ditto/ditto/issues/1727).  
With the `thing:json` placeholder it is possible to access arbitrary thing payload as a placeholder, for example in order
to use it as part of an outbound HTTP call for a managed [HTTP-push connection](connectivity-protocol-bindings-http.html) (WebHook).

Example: call a foreign weather service with the location being a part of the thing's attributes:
```
"address": "GET:/weather?longitude={%raw%}{{thing-json:attributes/location/lon}}&latitude={{thing-json:attributes/location/lat}}{%endraw%}"
```

#### New placeholder functions for joining multiple elements into a single string and doing URL-encoding and -decoding

With [#1754](https://github.com/eclipse-ditto/ditto/pull/1754) there is a new [placeholder function](basic-placeholders.html#function-library), 
`fn:join('delimiter')`, which can be used in order to join a pipeline element, containing multiple values, into a single
string.

As part of [#1727](https://github.com/eclipse-ditto/ditto/issues/1727) several placeholders were also added in order to
be able to apply URL-encoding and -decoding plus also Base64-encoding and -decoding: 
* `fn:url-encode()`
* `fn:url-decode()`
* `fn:base64-encode()`
* `fn:base64-decode()`

#### Configure MQTT message expiry interval for published messages via a header

Resolving issue [#1729](https://github.com/eclipse-ditto/ditto/issues/1729), a functionality was added to add a special
header `mqtt.message-expiry-interval` as part of a [MQTT5 target header mapping](connectivity-protocol-bindings-mqtt5.html#target-header-mapping),
dynamically influencing the MQTT message expiry interval, e.g. as part of a payload mapper for certain to-be-published 
messages, or as a header mapping for all published messages.

#### Reduce patch/merge thing commands to modify only the actually changed values with a new option

In [#1772](https://github.com/eclipse-ditto/ditto/pull/1772) the existing [if-equal header](httpapi-concepts.html#conditional-headers)
has been enhanced with a new option: `skip-minimizing-merge`.  
Performing a [merge/patch command](protocol-specification-things-merge.html) and specifying this option as header will
cause that the merge command's payload will be minimized to only the values which will actually be changed in the thing.

This reduces e.g. required storage in the MongoDB a lot, if redundant data is often sent and
also reduces the emitted event payload to [subscribers](basic-changenotifications.html) to only the actually changed 
parts of the thing, reducing network load and making it more clear what actually changed with a "merge event".

#### Enhancements in Ditto explorer UI

The UI was mainly enhanced with new features in a single PR, [#1773](https://github.com/eclipse-ditto/ditto/pull/1773).  
In detail, the following improvements and fixes were added:  
* add a tab "Message to Thing" to send thing messages
* add a loading spinner to the "Send" (message) button and deactivate it while sending
* update a complete Thing using "PATCH" and with the new 3.4.0 header "if-equal: skip-minimizing-merge"
* only send eTag if it could be retrieved when updating complete thing
* added missing `ilike` predicate to the search slot
* made UI more responsive for small screens
* prevent browser for doing autocomplete in the "search" input field
* increase size of the "Things" JSON editor - keep sizes and position of other JSON editors as they were


### Changes

#### Swapping the Akka toolkit with its fork, Apache Pekko

The biggest change of Ditto 3.4.0 is surely the switch from the [Akka toolkit](https://akka.io) to its OpenSource-friendly 
fork, [Apache Pekko](https://pekko.apache.org), tracked via [#1477](https://github.com/eclipse-ditto/ditto/issues/1477).  
To read about the Akka license switch to the BSL (Business Source License), please
[visit the Lightbend FAQ on that topic](https://www.lightbend.com/akka/license-faq).

Eclipse Ditto will use Apache Pekko, starting with Ditto 3.4.0.

The required [migration steps are documented as part of the release notes](#migrating-to-ditto-34x) and upgrading requires
a full cluster restart, no rolling update from prior versions to Ditto 3.4.0 is possible.

#### Support for using AWS DocumentDB as a replacement for MongoDB

Adding support for using [Amazon DocumentDB (with MongoDB compatibility)](https://aws.amazon.com/documentdb/) with
some [documented limitations](installation-running.html#managed-amazon-documentdb-with-mongodb-compatibility).

#### Improve logging by adding the W3C traceparent header as MDC field to logs

In [#1739](https://github.com/eclipse-ditto/ditto/issues/1739) Ditto adds support to log a [W3C Trace Context](https://www.w3.org/TR/trace-context/) 
`traceparent` header passed into Ditto (e.g. as HTTP header or as part of a [Connection's header mapping](connectivity-header-mapping.html)) 
to the MDC.  
If [tracing is enabled](installation-operating.html#tracing), Ditto will even produce traceparents for (up to) each API
invocation.

#### Adjust handling of special MQTT headers in MQTT 5

Resolving [#1758](https://github.com/eclipse-ditto/ditto/issues/1758), a feature toggle
([configuration](https://github.com/eclipse-ditto/ditto/blob/7ee1a778ffc6f254f59a9097d9c44372f069e897/internal/utils/config/src/main/resources/ditto-devops.conf#L24-L26))
was added to configure whether to preserve "special" MQTT properties (like `mqtt.topic`, `mqtt.qos`, ...) as headers or not.  
The default is to preserve them (as this was the default until now).

#### Optimize docker files

The Docker files were improved in [#1744](https://github.com/eclipse-ditto/ditto/issues/1744) in order to reduce image
size and follow best-practices.

#### Addition of a benchmark tooling to run own Ditto benchmarks

As part of the [Eclipse Ditto Benchmark blogpost](2023-10-09-ditto-benchmark.html) a benchmark-tool was developed and
is now part of the Eclipse Ditto [Git repository](https://github.com/eclipse-ditto/ditto/tree/master/benchmark-tool).  
If one needs/wants to run own benchmarks, this tool can be a good starting point.

#### Migration of Ditto UI to TypeScript

With [#1688](https://github.com/eclipse-ditto/ditto/pull/1688), the Ditto UI has been migrated from JavaScript codebase
to TypeScript, introducing `npm` to build the UI.


### Bugfixes

#### Fixed that failed retrieval of a policy (e.g. after policy change) leads to search index being "emptied out"

A bug [#1703](https://github.com/eclipse-ditto/ditto/issues/1703) was fixed, where the search index for things which 
could not fetch an updated policy via a cache-loader, was basically dropped.  
This could e.g. happen if a single policy is used by a lot of things and this was updated.

#### Fixed that putting metadata when updating a single scalar value did not work

The reported bug [#1631](https://github.com/eclipse-ditto/ditto/issues/1631), where the header `put-metadata` did not 
have an effect when updating a single scalar value, was fixed.

#### UI fix, fixing that patching a thing will null values did not reflect that change in the UI

The reported bug [#1712](https://github.com/eclipse-ditto/ditto/issues/1712) was fixed, the UI now correctly updates
when e.g. something was removed from a thing by a merge/patch update, using a `null` value.

### Helm Chart

#### Allow BASIC authentication for devops/status users while using Helm deployment and Ingress Controller

In [#1760](https://github.com/eclipse-ditto/ditto/pull/1760) the Ditto Helm chart was enhanced to use "Basic Authentication"
when using the Ditto Helm chart together with an Ingress Controller for authenticating at the DevOps APIs.


## Migration notes


### Migrating to Ditto 3.4.x

{% include warning.html content="Updating to Ditto 3.4.0 from versions < 3.4.0 is only possible with a full cluster recreate.
  Rolling update is not supported as there are changes in the management urls and ports." %}

To migrate a Ditto < 3.4.0 to Ditto 3.4.0, the renaming of an index name in Ditto's MongoDB persistence has to be done.  
The following section describes, how.

Apart from that, the transition to 3.4.0 should be smooth.

#### Renaming an index name

As part of the migration from Akka to Pekko and from [akka-persistence-mongo](https://github.com/scullxbones/akka-persistence-mongo)
persistence plugin to [pekko-persistence-mongo](https://github.com/scullxbones/pekko-persistence-mongo) a previously
named index `akka_persistence_metadata_pid` was renamed to `pekko_persistence_metadata_pid`. 

When starting Ditto 3.4.0 and not having adjusted the index name, the MongoDB will respond with errors as `pekko-persistence-mongo`
will try to create the same index with a different name.

To migrate, there are two options:
1. dropping the old indexes before upgrading to Ditto 3.4.0
2. adjusting the configuration with system properties to still keep using the old index name

##### Option 1: dropping the old indexes

* Connect to your MongoDB with the permissions to alter/drop indexes
* Drop the indexes using the following commands (via MongoDB shell):
  ```
  db.policies_metadata.dropIndex("akka_persistence_metadata_pid")
  db.things_metadata.dropIndex("akka_persistence_metadata_pid")
  db.connection_metadata.dropIndex("akka_persistence_metadata_pid")
  db.connection_remember_metadata.dropIndex("akka_persistence_metadata_pid")
  ```
* Depending on whether you use a single MongoDB "database" or several, you need to switch to the correct database before
  each drop


##### Option 2: adjusting the configuration with system properties

Configure the following system properties for the described Ditto services.

Policies:
```markdown
* -Dpekko-contrib-mongodb-persistence-policies-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-policies-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
```

Things:
```markdown
* -Dpekko-contrib-mongodb-persistence-things-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-things-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
```

Connectivity:
```markdown
* -Dpekko-contrib-mongodb-persistence-connection-journal.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-connection-journal-read.overrides.metadata-index=akka_persistence_metadata_pid
* -Dpekko-contrib-mongodb-persistence-connection-remember-journal.overrides.metadata-index=akka_persistence_metadata_pid
```


## Roadmap

Looking forward, the (current) ideas for Ditto 3.5.0 are:

* [#1650](https://github.com/eclipse-ditto/ditto/issues/1650) Enforcing linked WoT ThingModels in Things/Features by validating JsonSchema of model elements 
  * Ensuring that a Ditto Thing is ensured to always follow its WoT ThingModel and also message payloads are always
    provided in the specified format
* [#1521](https://github.com/eclipse-ditto/ditto/issues/1521) Configure on a namespace basis the fields to index in the thing-search
* [#1700](https://github.com/eclipse-ditto/ditto/issues/1700) Show policy imports in Ditto explorer UI
* [#1637](https://github.com/eclipse-ditto/ditto/issues/1637) Let Policies declare to be applicable only for certain namespaces

Apart from those ideas we are open to contributions of the community, improving or fixing areas which are important for
their use of Ditto.
