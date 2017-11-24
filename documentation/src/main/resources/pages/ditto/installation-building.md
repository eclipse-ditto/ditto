---
title: Building Ditto
tags: [getting_started, installation]
keywords: installation, docker, maven
permalink: installation-building.html
---

## Building with Apache Maven

In order to build Ditto with Maven, you'll need:
* JDK 8 >= 1.8.0_92 (due to a bug in older versions of the JDK you'll get a compile error),
* Apache Maven 3.x installed,
* a running Docker daemon (at least version 17.06 CE).

```bash
# if you have the Docker daemon running with remote access enabled (e.g. in a Vagrant box or on localhost):
mvn clean install -Pdocker-build-image -Ddocker.daemon.hostname=<ip/host of your Docker daemon>

# if you have the Docker daemon running on your machine and you are running on Unix, you can also connect against the Docker socket:
mvn clean install -Pdocker-build-image -Ddocker.daemon.url=unix:///var/run/docker.sock
```

## Building with Docker

In order to build Ditto with Docker, you'll need a running Docker daemon (at least version 17.06 CE).

If you do not have the appropriate Maven and JDK version available, you can also use a Maven Docker image as build 
environment.
On a Linux or macOS host you can expose the Docker socket to Maven like this:

```bash
# Start up the Docker image with maven:
docker run -it --rm --name mvn-ditto \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven \
    -u root \
    maven:3.5.0-jdk-8 \
    /bin/bash

# From within the Docker image, build the Docker images:
mvn clean install -Pdocker-build-image \
    -Ddocker.daemon.url=unix:///var/run/docker.sock

# Docker images are now available on your Docker host
```

