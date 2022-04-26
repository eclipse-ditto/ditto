---
title: Migration notes: wildcard search
tags: []
published: true
keywords: migration
summary: "Migration of the search index after introduction of wildcard index based search"
permalink: migration_notes_wildcard_search.html
---

## Changes

Ditto **TODO:version** introduces a new search index schema based on [wildcard indices](https://www.mongodb.com/docs/manual/core/index-wildcard/) of MongoDB. In order to facilitate
a smooth upgrade, the service name, cluster role, database name and collections of Search service are changed as
follows.
- The service name is changed from `things-search` to `search`.
- The cluster role is changed from `things-search` to `things-wildcard-search`.
- The default database is changed from `searchDB` to `search`.
- The collections used for the search index are changed from `searchThings` and `searchThingsSync` to `search` and
  `searchSync`.

## Automatic reindexing

After deployment of Ditto **TODO:version**, Search service will start reindexing things in the background. The result
of queries will be incomplete until reindexing finishes. The progress of background sync can be monitored via the
`/status/health` HTTP endpoint under the label `backgroundSync`.

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
            "2022-04-25T02:13:07.695990296Z": "WOKE_UP"
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
            "2022-04-25T02:13:07.695990296Z": "WOKE_UP"
          },
          {
            "2022-04-25T02:05:07.679251051Z": "Stream terminated. Result=<Done> Error=<null>"
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
`"Stream terminated. Result=<Done> Error=<null>"`,
{%endraw%}
reindexing has completed successfully.

## Clean up

After reindexing, the old search index can be dropped.
- If you did not override the default database `searchDB`, the database `searchDB` can be dropped.
- If you configured a different database, the collections `searchThings` and `searchThingsSync` can be dropped.


