# Eclipse Ditto

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
* Apache Maven 3.x installed
* a running Docker daemon (at least version 17.06 CE)
* Docker Compose installed (at least version 1.16)

### Build and start Ditto

```bash
# if you have the docker daemon running on your machine:
mvn clean install -Pdocker-build-image
# if you have the docker daemon running on another machine with remote access enabled:
mvn clean install -Pdocker-build-image -Ddocker.daemon.hostname=<ip/host of your docker daemon>

cd docker/
docker-compose -f docker-compose.yml -p ditto up -d
```

Check the logs after starting up:
```bash
docker-compose -f docker-compose.yml logs -f
```

You have now running:
* a MongoDB as backing datastore of Ditto (not part of Ditto but started via Docker)
* Ditto microservices:
   * Policies
   * Things
   * Thing-Search
   * Gateway
* an nginx acting as a reverse proxy performing a simple "basic authentication" listeing on port `8080`

### Try it out

Have a look at the [Getting Started](documentation/getting-started/README.md)
