---
title: Running Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, k3s, helm, openshift, kubernetes
permalink: installation-running.html
---

## Start Ditto

In order to start Ditto, you'll need:
* a [MongoDB](https://github.com/mongodb/mongo) service or container with version __4.2__ as backing datastore of Ditto.
  (if you want to use a managed MongoDB service have a look in the [section](#managed-mongodb-service) below)
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