---
title: Running Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, k3s, helm, openshift, kubernetes
permalink: installation-running.html
---

## Start Ditto

Resource requirements:
* in order to start Ditto locally (via Docker Compose), you'll need **at least**:
  * 2 CPU cores to be used by Docker
  * 4 GB of RAM to be used by Docker
* if you decide to run Ditto in a local Kubernetes environment, you'll need some additional resources for Kubernetes

In order to start Ditto, you'll need:
* a [MongoDB](https://github.com/mongodb/mongo) service or container with version greater or equal __4.2__ as backing datastore of Ditto.
  (if you want to use a managed MongoDB service have a look in the [section](#managed-mongodb-service) below)
   * Supported MongoDB versions:
     * 4.2
     * 4.4
     * 5.0
   * Alternatively, [Amazon DocumentDB (with MongoDB compatibility)](https://aws.amazon.com/documentdb/) may also be used,
     however with some limitations, see the [section about DocumentDB below](#managed-amazon-documentdb-with-mongodb-compatibility)
* the built Docker images of Ditto
    * either by building them as described in [Building Ditto](installation-building.html),
    * or by using the pre-built [Ditto images on Docker Hub](https://hub.docker.com/u/eclipse/).
* some other tools like docker-compose, helm, k3s, minikube or openshift to run Ditto.
 
You can choose from several options to run/deploy Ditto.
A good starting point here is [Ditto Deployment](https://github.com/eclipse-ditto/ditto/blob/master/deployment/README.md).
After completing the deployment of your choice Ditto should be up & running.

Now you have running:
* a MongoDB as backing datastore of Ditto (not part of Ditto but started to get Ditto running),
* Ditto microservices:
   * Policies,
   * Things,
   * Connectivity,  
   * Thing-Search,
   * Gateway
* an instance of nginx acting as a reverse proxy performing a simple "basic authentication" listening on a local port 
   * including some static HTTP + API documentation

The running port on which Ditto can be accessed is described in the Readme of the respective deployment section.

If you want to change the MongoDB config or Ditto config please have a look here: 
[Operating Ditto](installation-operating.html)

### Managed MongoDB service
When using a managed MongoDB service the following recommendations should be taken into account:
- at least a 3 Node dedicated cluster for high-traffic applications and large datasets
- Cluster scaling depending on load

Other Recommendations:
- SSL/TLS 1.2 and above
- Data Encryption at rest
- Daily Backups

### Managed Amazon DocumentDB (with MongoDB compatibility)

DocumentDB provides a MongoDB 4.0 compatible replacement for MongoDB.  
Eclipse Ditto supports running against MongoDB 4.0 compatible mode, however with restrictions.

#### Configuration

If DocumentDB compatibility mode shall be enabled, the following environment variable has to be configured for all Ditto
services:
```
MONGO_DB_DOCUMENTDB_COMPATIBILITY_MODE=true
```

Effects:
* That configuration will change a behavior in Ditto's `MongoReadJournal` concerning the sorting based on priority of 
  connections to recover after a "cold start" of Ditto (e.g. after a "recreate" deployment)
  * as DocumentDB does not support configuring 
    [`collation` and `numericOrdering=true`](https://www.mongodb.com/docs/v4.2/reference/collation/), a workaround was 
    added which disables this configuration
  * the workaround for DocumentDB only correctly sorts connection priorities less than 1000 - connections with higher 
    priorities will be started in an order not reflected by their priority value
  * this limitation is only relevant if many (e.g. several hundreds) connections are managed by Ditto
* That configuration will also prevent that the [wildcard index](https://www.mongodb.com/docs/manual/core/index-wildcard/)
  in Ditto's search collection is created - in order to prevent errors upon index creation as this index type is not
  supported by DocumentDB
  * for more information on the effects of that, please have a look at the [following section](#documentdb-restriction-regarding-search)

#### DocumentDB restriction regarding search

As DocumentDB does not support the in Mongo 4.2 added
[wildcard index](https://www.mongodb.com/docs/manual/core/index-wildcard/) which Ditto's
[search service](architecture-services-things-search.html) makes use of, the search functionality - when Ditto
runs against DocumentDB - can have a poor performance.

Without the index, every performed query will basically have to search through all documents in the search collection.  
The amount of documents to search in however can significantly be reduced by executing the search with users 
([authenticated subjects](basic-auth.html#authenticated-subjects)) which are only allowed to `READ` a smaller set of 
things.  
That is due to the fact, that every "authenticated subject" which is allowed to `READ` any field in a thing, is added 
in an indexed field in the `search` collection. And the query planner always uses this index in absence of the 
"wildcard index".

For example:
* assuming you in total have 300.000 things persisted
* if you use a single user which is allowed to `READ` all of those 300k things
  * if you do a search, all the 300k documents have to be scanned
  * the search query performance will be really poor, probably > 15 seconds, maybe even worse
* if you instead have 1.000 users from which every user is only allowed to `READ` 300 things
  * only 300 documents have to be scanned
  * the search query performance will be quite good, probably < 1 second, maybe even better

If that restriction fits you use case, or if search queries are not needed at all, Amazon DocumentDB can be a good
replacement for MongoDB as backing persistence for Ditto.
