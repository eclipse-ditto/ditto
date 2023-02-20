<div align="center">
  <img src="https://raw.githubusercontent.com/eclipse/ditto/master/logo/ditto_fordarkbg.svg?sanitize=true#gh-dark-mode-only" alt="Ditto Logo dark" height="250">
  <img src="https://raw.githubusercontent.com/eclipse/ditto/master/logo/ditto.svg?sanitize=true#gh-light-mode-only" alt="Ditto Logo light" height="250">
</div>


# Eclipse Ditto™

[![Join the chat at https://gitter.im/eclipse/ditto](https://badges.gitter.im/eclipse/ditto.svg)](https://gitter.im/eclipse/ditto?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://github.com/eclipse-ditto/ditto/workflows/build/badge.svg)](https://github.com/eclipse-ditto/ditto/actions?query=workflow%3Abuild)
[![Maven Central](https://img.shields.io/maven-central/v/org.eclipse.ditto/ditto?label=maven)](https://search.maven.org/search?q=g:org.eclipse.ditto)
[![Docker pulls](https://img.shields.io/docker/pulls/eclipse/ditto-things.svg)](https://hub.docker.com/search?q=eclipse%2Fditto&type=image)
[![License](https://img.shields.io/badge/License-EPL%202.0-green.svg)](https://opensource.org/licenses/EPL-2.0)
[![Lines of code](https://img.shields.io/badge/dynamic/xml.svg?label=Lines%20of%20code&url=https%3A%2F%2Fwww.openhub.net%2Fprojects%2Feclipse-ditto.xml%3Fapi_key%3D11ac3aa12a364fd87b461559a7eedcc53e18fb5a4cf1e43e02cb7a615f1f3d4f&query=%2Fresponse%2Fresult%2Fproject%2Fanalysis%2Ftotal_code_lines&colorB=lightgrey)](https://www.openhub.net/p/eclipse-ditto)

[Eclipse Ditto](https://www.eclipse.org/ditto/)™ is a technology in the IoT implementing a software pattern called “digital twins”.  
A digital twin is a virtual, cloud based, representation of his real world counterpart (real world “Things”, e.g. devices like sensors, smart heating, connected cars, smart grids, EV charging stations, …).

An ever growing list of [adopters](https://iot.eclipse.org/adopters/?#iot.ditto) makes use of Ditto as part of their IoT platforms - if you're as well using it, it would be super nice to show your [adoption here](https://iot.eclipse.org/adopters/how-to-be-listed-as-an-adopter/).

## Documentation

Find the documentation on the project site: [https://www.eclipse.org/ditto/](https://www.eclipse.org/ditto/)

## Eclipse Ditto™ explorer UI

Find a live version of the latest explorer UI: [https://eclipse-ditto.github.io/ditto/](https://eclipse-ditto.github.io/ditto/?primaryEnvironmentName=ditto_sandbox)

You should be able to work with your locally running default using the `local_ditto` environment - and you can add additional environments to also work with e.g. with a deployed installation of Ditto.

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=eclipse-ditto/ditto&type=Date)](https://star-history.com/#eclipse-ditto/ditto&Date)

## Getting started

In order to start up Ditto via *Docker Compose*, you'll need:
* a running Docker daemon
* Docker Compose installed
* for a "single instance" setup on a local machine:
  * at least 2 CPU cores which can be used by Docker
  * at least 4 GB of RAM which can be used by Docker

You also have other possibilities to run Ditto, please have a look [here](https://github.com/eclipse-ditto/ditto/tree/master/deployment) to explore them.

### Start Ditto

In order to start the latest built Docker images from Docker Hub, simply execute:

```bash
cd deployment/docker/
docker-compose up -d
```

Check the logs after starting up:
```bash
docker-compose logs -f
```

Open following URL to get started: [http://localhost:8080](http://localhost:8080)<br/>
Or have a look at the ["Hello World"](https://www.eclipse.org/ditto/intro-hello-world.html)

Additional [deployment options](deployment/) are also available, if Docker Compose is not what you want to use.

## Development Guide

If you plan to develop extensions in Ditto or to contribute some code, the following steps are of interest for you.

> :warning: **If you just want to start/use Ditto**, please ingore the following sections! 

### Build and start Ditto locally

In order to build Ditto, you'll need:
* JDK >= 17 
* Apache Maven >= 3.8.x installed.
* a running Docker daemon

In order to first build Ditto and then start the built Docker images.

#### 1. Build Ditto with Maven
```bash
mvn clean install
```

Skip tests:
```bash
mvn clean install -DskipTests
```

#### 2. Build local Ditto Docker snapshot images
```bash
./build-images.sh
```
If your infrastructure requires a proxy, its host and port can be set using the `-p` option like for example:
```bash
./build-images.sh -p 172.17.0.1:3128
```
Please note that the given host and port automatically applies for HTTP and HTTPS.

#### 3. Start Ditto with local snapshot images
```bash
cd ../deployment/docker/
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
   * Things-Search
   * Gateway
   * Connectivity
* an nginx acting as a reverse proxy performing a simple "basic authentication" listening on port `8080`
   * including some static HTTP + API documentation on [http://localhost:8080](http://localhost:8080)
