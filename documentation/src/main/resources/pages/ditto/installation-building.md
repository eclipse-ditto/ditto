---
title: Building Ditto
tags: [getting_started, installation]
keywords: installation, docker, maven
permalink: installation-building.html
---

## Building with Apache Maven

In order to build Ditto with Maven, you'll need:
* JDK 11 >= 11.0.5,
* Apache Maven 3.x installed,
* a running Docker daemon (at least version 18.06 CE).

```bash
mvn clean install
sh build-images.sh
```

## Building with Docker

In order to build Ditto with Docker, you'll need a running Docker daemon (at least version 18.06 CE).

If you do not have the appropriate Maven and JDK version available, you can also use a Maven Docker image as build 
environment.

```bash
docker run -it --rm --name mvn-ditto \
    -v "$PWD":/usr/src/mymaven -w /usr/src/mymaven \
    -u root \
    maven:3.6-jdk-11 \
    mvn clean install

sh build-images.sh
```

