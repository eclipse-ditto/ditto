# Eclipse Ditto

[![Join the chat at https://gitter.im/eclipse/ditto](https://badges.gitter.im/eclipse/ditto.svg)](https://gitter.im/eclipse/ditto?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/eclipse/ditto.svg?branch=master)](https://travis-ci.org/eclipse/ditto)

Eclipse Ditto is the open-source project of Eclipse IoT that provides a ready-to-use functionality to manage the state of Digital Twins. It provides access to them and mediates between the physical world and this digital representation.


## Scope

To achieve this Ditto addresses the following aspects.

### Device-as-a-Service
Provide a higher abstraction level in form of an API used to work with individual devices.

### State management for Digital Twins
Differ between reported (last known), desired (target) and current state (live) of devices, including support for synchronization and publishing of state changes.

### Organize your set of Digital Twins
Support finding and selecting sets of Digital Twins by providing search functionality on meta data and state data.


## Getting started

In order to start up Ditto, you'll need
* JDK 8 >= 1.8.0_92 (due to a bug in older versions of the JDK you'll get a compile error)
* Apache Maven 3.x installed
* a running Docker daemon (at least version 17.06 CE)
* Docker Compose installed (at least version 1.14)

### Build and start Ditto

```bash
# if you have the docker daemon running with remote access enabled (e.g. in a Vagrant box or on localhost):
mvn clean install -Pdocker-build-image -Ddocker.daemon.hostname=<ip/host of your docker daemon>
# if you have the docker daemon running on your machine and you are running on Unix, you can also connect against the docker socket:
mvn clean install -Pdocker-build-image -Ddocker.daemon.url=unix:///var/run/docker.sock

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

#### Build from within Docker image

If you do not have the right Maven and JDK version available, you can also use a Maven Docker image as build environment. On a Linux or macOS host, you can expose the docker socket to Maven like this:

```bash
# Start up the docker image with maven:
docker run -it --rm --name mvn-ditto \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven \
    -u root \
    maven:3.5.0-jdk-8 \
    /bin/bash
# From within the docker image, build the docker images:
mvn clean install -Pdocker-build-image \
    -Ddocker.daemon.url=unix:///var/run/docker.sock
# Docker images are now available on your Docker host
```

### Try it out

Have a look at the [Getting Started](documentation/getting-started/README.md)
