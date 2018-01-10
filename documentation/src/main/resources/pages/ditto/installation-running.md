---
title: Running Ditto
tags: [getting_started, installation]
keywords: running, docker, docker-compose, start, run
permalink: installation-running.html
---

## Start Ditto

In order to start Ditto, you'll need:
* a running Docker daemon (at least version 17.06 CE),
* Docker Compose installed (at least version 1.14),
* the built Docker images of Ditto
    * either by building them as described in [Building Ditto](installation-building.html),
    * or by using the pre-built [Ditto images on Docker Hub](https://hub.docker.com/u/eclipse/).

```bash
# switch to the docker/ directory:
cd docker/
docker-compose up -d
```

Check the logs after starting up:
```bash
docker-compose logs -f
```

You have now running:
* a MongoDB as backing datastore of Ditto (not part of Ditto but started via Docker),
* Ditto microservices:
   * Policies,
   * Things,
   * Thing-Search,
   * Gateway,
* an instance of nginx acting as a reverse proxy performing a simple "basic authentication" listening on port `8080`
   * including some static HTTP + API documentation on [http://localhost:8080](http://localhost:8080).


## Stop Ditto

This command stops the Ditto stack without removing the data from MongoDB database.

```bash
docker-compose stop
```

After executing this command, Ditto can be started again:

```bash
docker-compose start
```

## Destroy Ditto stack

For removing the stack and clearing the MongoDB completely, execute: 

```bash
docker-compose down
```
