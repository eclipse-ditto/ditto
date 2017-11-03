---
title: Getting started
keywords: docker, docker-compose, maven, build, start
tags: [getting_started]
permalink: getting-started.html
---

## (Optional) Build Eclipse Ditto

In order to build Ditto, you'll need
* JDK 8 >= 1.8.0_92 (due to a bug in older versions of the JDK you'll get a compile error)
* Apache Maven 3.x installed
* a running Docker daemon (at least version 17.06 CE)

```bash
# if you have the docker daemon running with remote access enabled (e.g. in a Vagrant box or on localhost):
mvn clean install -Pdocker-build-image -Ddocker.daemon.hostname=<ip/host of your docker daemon>
# if you have the docker daemon running on your machine and you are running on Unix, you can also connect against the docker socket:
mvn clean install -Pdocker-build-image -Ddocker.daemon.url=unix:///var/run/docker.sock
```

## Start Eclipse Ditto

In order to start Ditto, you'll need
* a running Docker daemon (at least version 17.06 CE)
* Docker Compose installed (at least version 1.14)

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
* a MongoDB as backing datastore of Ditto (not part of Ditto but started via Docker)
* Ditto microservices:
   * Policies
   * Things
   * Thing-Search
   * Gateway
* an nginx acting as a reverse proxy performing a simple "basic authentication" listening on port `8080`
   * including some static HTTP + API documentation on [http://localhost:8080](http://localhost:8080)


## Stop Eclipse Ditto

This commands stops the Ditto stack without removing the data in the MongoDB database.

```bash
docker-compose stop
```

After executing this command, Ditto can be started again:

```bash
docker-compose start
```

## Destroy Eclipse Ditto stack

For removing the stack and clearing the MongoDB completely, execute: 

```bash
docker-compose down
```
