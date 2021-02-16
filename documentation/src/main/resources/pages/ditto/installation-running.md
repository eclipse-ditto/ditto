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
A good starting point here is [Ditto Deployment](https://github.com/eclipse/ditto/blob/master/deployment/README.md).
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

If you want to change the MongoDB config or Ditto config please have a look here: [Operating Ditto](installation-operating.html)