# [Eclipse Ditto](https://eclipse.org/ditto)

[![Join the chat at https://gitter.im/eclipse/ditto](https://badges.gitter.im/eclipse/ditto.svg)](https://gitter.im/eclipse/ditto?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/eclipse/ditto.svg?branch=master)](https://travis-ci.org/eclipse/ditto)

![Ditto Logo](./documentation/src/main/resources/images/ditto.svg)

Eclipse Ditto is the open-source project of Eclipse IoT that provides a ready-to-use functionality to manage the state of Digital Twins. It provides access to them and mediates between the physical world and this digital representation.

## Documentation

Find the documentation on the project site: [https://eclipse.org/ditto/](https://eclipse.org/ditto)

## Getting started

In order to start up Ditto, you'll need
* JDK 8 >= 1.8.0_92 (due to a bug in older versions of the JDK you'll get a compile error)
* Apache Maven 3.x installed
* a running Docker daemon (at least version 17.06 CE)
* Docker Compose installed (at least version 1.14)

### Start Ditto

In order to start the latest built Docker images from Docker Hub, simply execute:

```bash
cd docker/
docker-compose up -d
```

Check the logs after starting up:
```bash
docker-compose logs -f
```

### Build and start Ditto

In order to first build Ditto and then start the built Docker images

```bash
# if you have the docker daemon running with remote access enabled (e.g. in a Vagrant box or on localhost):
mvn clean install -Pdocker-build-image -Ddocker.daemon.hostname=<ip/host of your docker daemon>
# if you have the docker daemon running on your machine and you are running on Unix, you can also connect against the docker socket:
mvn clean install -Pdocker-build-image -Ddocker.daemon.url=unix:///var/run/docker.sock

cd docker/
# the "dev.env" file contains the SNAPSHOT number of Ditto, copy it to ".env" so that docker compose uses it:
cp dev.env .env
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
