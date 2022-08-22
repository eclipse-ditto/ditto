---
title: Release notes 3.0.0
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version 3.0.0 of Eclipse Ditto, released on xx.xx.2022"
permalink: release_notes_300.html
---

This is Eclipse Ditto version 3.0.0.

This release is completely [IP (intellectual property) checked by the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip)
meaning that project code as well as all used dependencies were "[...] reviewed to ensure that the copyrights
expressed are correct, licensing is valid and compatible, and that other issues have been uncovered and properly
investigated."

## Changelog

Eclipse Ditto 3.0.0 focuses on the following areas:

* Ability to **search in JSON arrays** and thus also for feature definitions
* Several **improvements around "metadata"** in Ditto managed things
* Creation of **new HTTP API for CRUD** management of Ditto managed **connections**
* Addition of **Ditto explorer UI** for managing things, policies and connections
* Support for EC signed JsonWebKeys (JWKs)
* W3C WoT (Web of Things) adjustments and improvements for latest 1.1 "Candidate Recommendation" from W3C
* Make "default namespace" for creating new entities configurable
* Provide custom namespace when creating things via HTTP POST

The following non-functional enhancements are also included:

* New Ditto **"thing" search index** massively improving write performance; reducing the search consistency lag
  and improving search query performance
* **Removal of former "ditto-concierge" service**, moving its functionality to other Ditto services; reducing overall
  resource consumption and improving latency+throughput for API calls
* Rewrite of Ditto managed **MQTT connections to use reactive-streams based client**, supporting consumption applying
  backpressure
* Further improvements on rolling updates and other failover scenarios
* Consolidate and simplify DevOps command responses (TODO TJ migration note!)
* Add resubscription to Ditto pub/sub to ensure long-term consistency
* Add subscriber pooling to Ditto PubSub supporting scaling out on messages/second to a single subscriber


### Changes

#### [New Ditto "thing" search index](https://github.com/eclipse/ditto/issues/1374)

TODO

#### [Removal of former "ditto-concierge" service](https://github.com/eclipse/ditto/issues/1339)

TODO

#### [Rewrite of Ditto managed MQTT connection to use reactive client](https://github.com/eclipse/ditto/pull/1411)

TODO

#### Further improvements on rolling updates and other failover scenarios

TODO

* [Allow turning the akka SBR on/off during runtime](https://github.com/eclipse/ditto/pull/1373)
* [Implement graceful shutdown for http publisher actor](https://github.com/eclipse/ditto/pull/1381)
* ...

#### [DevOps commands response consistency](https://github.com/eclipse/ditto/pull/1380)

TODO

#### [Add resubscription to Ditto pub/sub to ensure long-term consistency](https://github.com/eclipse/ditto/pull/1386)

TODO

#### [Make Ditto pubsub update operations simpler and more consistent](https://github.com/eclipse/ditto/pull/1427)

TODO

### New features

#### [Ability to search in JSON arrays and also in feature definitions](https://github.com/eclipse/ditto/pull/1396)

TODO 

Also enables:
* [Support to search by feature definition](https://github.com/eclipse/ditto/pull/1417)

#### Several improvements around "metadata"

TODO 

The following features, enhancements and fixes about [metadata](basic-metadata.html):

* [Retrieve thing metadata when not retrieving complete thing](https://github.com/eclipse/ditto/issues/772)
* [Metadata is not deleted when thing parts are deleted](https://github.com/eclipse/ditto/issues/829)
* [Make is possible to delete metadata from a thing](https://github.com/eclipse/ditto/issues/779)
* [Metadata cannot be set on sub-resources via HTTP](https://github.com/eclipse/ditto/issues/1146)
* [Add initial-metadata support to thing creation](https://github.com/eclipse/ditto/issues/884)
* [Search does not work for _metadata](https://github.com/eclipse/ditto/issues/1404)

#### [New HTTP API for CRUD of connections](https://github.com/eclipse/ditto/issues/1406)

TODO

#### New Ditto explorer UI

We received several contributions by [Thomas Fries](https://github.com/thfries), who contributed the Ditto explorer UI.  
A live version of the UI can be found here. You can use it in order to e.g. connect to your Ditto installation and
manage things, policies and even connections:  
[https://eclipse.github.io/ditto/](https://eclipse.github.io/ditto/)

Contributions:
* [Eclipse Ditto explorer UI](https://github.com/eclipse/ditto/pull/1397)
* [Ditto explorer UI: Improvements from review](https://github.com/eclipse/ditto/pull/1405)
* [Explorer UI - Add initial support for connections](https://github.com/eclipse/ditto/pull/1414)
* [added mechanism to build "ditto-ui" Docker image](https://github.com/eclipse/ditto/pull/1415)
* [Explorer UI - review improvements for connections](https://github.com/eclipse/ditto/pull/1418)
* [Explorer UI: add local_ditto_ide and ditto_sanbdox environments](https://github.com/eclipse/ditto/pull/1422)
* [Explorer UI - add support for policies](https://github.com/eclipse/ditto/pull/1430)
* [Explorer UI - Improve message to feature and some WoT support](https://github.com/eclipse/ditto/pull/1455)


### Bugfixes

Several bugs in Ditto 2.4.x were fixed for 3.0.0.  
This is a complete list of the
* [merged pull requests for milestone 3.0.0](https://github.com/eclipse/ditto/pulls?q=is:pr+milestone:3.0.0)

Here as well for the Ditto Java Client: [merged pull requests for milestone 3.0.0](https://github.com/eclipse/ditto-clients/pulls?q=is:pr+milestone:3.0.0)



## Migration notes

Migrations required updating from Ditto 2.4.x or earlier versions:

* The search index has to be rebuilt - the old search collections may afterwards be dropped/deleted

### Building up new search index

Ditto **3.0.0** introduces a new search index schema based on
[wildcard indices](https://www.mongodb.com/docs/manual/core/index-wildcard/) of MongoDB.  
The service name, cluster role, database name and collections of the search service were changed as follows:
- The service name is changed from `things-search` to `search`.
- The cluster role is changed from `things-search` to `search`.
- The default database is changed from `searchDB` to `search`.
- The collections used for the search index are changed from `searchThings` and `searchThingsSync` to `search` and
  `searchSync`.

#### Automatic reindexing

After initial deployment of Ditto **3.0.0**, the search service will start reindexing things in the background.  
The result of performed search queries will be incomplete until the reindexing finishes.  
The progress of the background sync can be monitored via the `/status/health` HTTP endpoint under the label 
`backgroundSync`:

Here is an example status for reindexing in progress.
```json
{
  "label": "backgroundSync",
  "status": "UP",
  "details": [
    {
      "INFO": {
        "enabled": true,
        "events": [
          {
            "2022-08-25T02:13:07.695990296Z": "WOKE_UP"
          }
        ],
        "progressPersisted": "ditto:device1234",
        "progressIndexed": ":_"
      }
    }
  ]
}
```

Here is an example status after completion of background sync.
{%raw%}
```json
{
  "label": "backgroundSync",
  "status": "UP",
  "details": [
    {
      "INFO": {
        "enabled": true,
        "events": [
          {
            "2022-08-25T02:13:07.695990296Z": "WOKE_UP"
          },
          {
            "2022-08-25T02:05:07.679251051Z": "Stream terminated. Result=<Done> Error=<null>"
          }
        ],
        "progressPersisted": ":_",
        "progressIndexed": ":_"
      }
    }
  ]
}
```
{%endraw%}

Background sync will restart shortly after the first round of reindexing. As long as the `events` field contains the
line  
{%raw%}
`"Stream terminated. Result=<Done> Error=<null>"`
{%endraw%}

, the reindexing has completed successfully.

#### Clean up

After reindexing, the old search index can be dropped.
- If you did not override the default database `searchDB`, the database `searchDB` can be dropped.
- If you configured a different database, the collections `searchThings` and `searchThingsSync` can be dropped.


### Entity creation configuration migration

TODO TJ


### TODO ...


## Roadmap

Looking forward, the current plans for Ditto 3.1.0 are:

* ...
