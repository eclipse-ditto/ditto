---
title: Running Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, k3s, helm, openshift, kubernetes
permalink: installation-running.html
---

## Start Ditto

In order to start Ditto, you'll need:
* the built Docker images of Ditto
    * either by building them as described in [Building Ditto](installation-building.html),
    * or by using the pre-built [Ditto images on Docker Hub](https://hub.docker.com/u/eclipse/).
* some other tools like docker-compose, helm, k3s, minikube or openshift to run Ditto. 
 
You can choose from several options to deploy Ditto.
A good starting point here is [Ditto Deployment](deployment/README.md).
After completing the deployment of your choice Ditto should be up & running.

Now you have running:
* a MongoDB as backing datastore of Ditto (not part of Ditto but started to get Ditto running),
* Ditto microservices:
   * Concierge,
   * Connectivity,  
   * Policies,
   * Things,
   * Thing-Search,
   * Gateway,
* an instance of nginx acting as a reverse proxy performing a simple "basic authentication" listening on port `8080`
   * including some static HTTP + API documentation on [http://localhost:8080](http://localhost:8080).

  

## Runtime configuration

Ditto has many config parameters which can be set in the config files or via environment variables.
This section will cover some of Ditto's config parameters.

### MongoDB configuration

With the following environment variables thr connection to the MongoDB can be configured.

MONGO_DB_URI: Connection string to MongoDB 

MONGO_DB_SSL_ENABLED: Enabled SSL connection to MongoDB

MONGO_DB_CONNECTION_POOL_SIZE: Configure MongoDB connection pool size

MONGO_DB_READ_PREFERENCE: Configure MongoDB read preference

MONGO_DB_WRITE_CONCERN: Configure MongoDB write concern

AKKA_PERSISTENCE_MONGO_JOURNAL_WRITE_CONCERN: Configure Akka Persistence MongoDB journal write concern   

AKKA_PERSISTENCE_MONGO_SNAPS_WRITE_CONCERN: Configure Akka Persistence MongoDB snapshot write concern


### Ditto configuration

Link to config files. 