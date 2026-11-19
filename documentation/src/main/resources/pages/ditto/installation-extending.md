---
title: Extending Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, extension, custom, configuration, logging
permalink: installation-extending.html
---

You extend Ditto by implementing custom `DittoExtensionPoint` interfaces and loading them into the service via Pekko's classloader.

{% include callout.html content="**TL;DR**: Create a Java class implementing a `DittoExtensionPoint` interface, configure it in a `<service>-extension.conf` file, and add the JAR to `/opt/ditto/extensions` in the Docker container." type="primary" %}

## Overview

Ditto provides extension points throughout its codebase, marked by interfaces that extend `DittoExtensionPoint`. You can replace the default behavior at these points by providing your own implementation.

## How it works

### Creating an extension

Your implementation needs a public constructor that accepts an `ActorSystem` and `Config` parameter, which Pekko's classloader uses for reflection-based instantiation:

```java
public CustomExtension(final ActorSystem actorSystem, final Config config) {}
```

### Configuring an extension

Tell Pekko's classloader which implementation to use by adding the extension's `CONFIG_KEY` to:
* A `<service-name>-extension.conf` file for service-specific extensions
* The `reference.conf` for a global scope

Each extension configuration has two parts:
* `extension-class`: The fully qualified class name of your implementation
* `extension-config`: Custom configuration for the extension (optional)

```hocon
ditto.extensions.signal-enrichment-provider {
  extension-class = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
  extension-config = {
    cache {
      enabled = true
      maximum-size = 20000
      expire-after-create = 2m
    }
  }
}
```

If your extension needs no custom configuration, use the shorthand form:

```hocon
ditto.extensions.signal-enrichment-provider = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
```

## Configuration

### Adjusting service configuration

For simple configuration changes, use [system properties](operating-configuration.html). For extensive changes, create a [HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) file named `<ditto-service-name>-extension.conf` and place it in the Docker container's working directory:

| Service      | Extension config path                    |
|--------------|------------------------------------------|
| Policies     | `/opt/ditto/policies-extension.conf`     |
| Things       | `/opt/ditto/things-extension.conf`       |
| Search       | `/opt/ditto/search-extension.conf`       |
| Connectivity | `/opt/ditto/connectivity-extension.conf` |
| Gateway      | `/opt/ditto/gateway-extension.conf`      |

These files can contain any configuration from the [service config files](operating-configuration.html).

For example, the [gateway.conf](https://github.com/eclipse-ditto/ditto/blob/master/gateway/service/src/main/resources/gateway.conf)
contains the following health-check configuration:

```hocon
ditto {
  gateway {
    health-check {
      cluster-roles = {
        enabled = true
        enabled = ${?HEALTH_CHECK_ROLES_ENABLED}

        expected = [
          "policies",
          "things",
          "search",
          "gateway",
          "connectivity"
        ]
      }
    }
  }
}
```

To remove the "connectivity" role from the health check (e.g. when not starting `ditto-connectivity`
at all), create a `gateway-extension.conf` with:

```hocon
ditto.gateway.health-check.cluster-roles = {
  expected = [
    "policies",
    "things",
    "search",
    "gateway"
  ]
}
```

Then mount the file into the container at `/opt/ditto/gateway-extension.conf`.

### Adding JARs to the classpath

Ditto Docker images automatically add all JARs from these directories to the classpath, in this order:

* `/opt/ditto` (the service itself)
* `/opt/ditto/backends/${DITTO_PERSISTENCE_BACKEND}` (exactly one persistence backend folder, see
  [Persistence backends](#persistence-backends))
* `/opt/ditto/extensions` (your extension JARs)

Build your extension as a JAR (including extension classes and config files) and place it in the `extensions` directory.

## Examples

### Custom API routes

1. Create a new implementation of `CustomApiRoutesProvider`, overriding the `unauthorized(*)` and `authorized(*)` functions to return custom HTTP API routes.
2. Build the project into a `gateway-extension.jar`.
3. Add the JAR to the container:
   ```bash
   docker cp gateway-extension.jar container_id:/opt/ditto/extensions/
   ```
4. Create a `gateway-extension.conf`:
   ```hocon
   ditto.extensions.custom-api-routes-provider = org.company.project.gateway.service.endpoints.utils.MyCustomApiRoutesProvider
   ```
5. Add the config to the container:
   ```bash
   docker cp gateway-extension.conf container_id:/opt/ditto/
   ```

Alternatively, mount both files via docker-compose:

```yaml
connectivity:
  image: docker.io/eclipse/ditto-gateway:${DITTO_VERSION:-latest}
  environment:
    - TZ=Europe/Berlin
    - JAVA_TOOL_OPTIONS=-Dlogback.configurationFile=/opt/ditto/logback.xml
  volumes:
    - ./gateway-extension.conf:/opt/ditto/gateway-extension.conf
    - ./logback.xml:/opt/ditto/logback.xml
    - ./gateway-extension.jar:/opt/ditto/extensions/gateway-extension.jar
```

### Persistence backends

The persistence backends are extensions too. Every Ditto image ships both of them, MongoDB and PostgreSQL, in its own
folder under `/opt/ditto/backends/`:

| Service                          | `/opt/ditto/backends/mongodb/`                                          | `/opt/ditto/backends/postgresql/`                                            |
|----------------------------------|-------------------------------------------------------------------------|------------------------------------------------------------------------------|
| policies, things, connectivity   | `ditto-mongo-persistence-extension`                                     | `ditto-postgres-client-extension`, `ditto-postgres-persistence-extension`    |
| things-search                    | `ditto-mongo-persistence-extension`, `ditto-mongo-search-extension`     | `ditto-postgres-client-extension`, `ditto-postgres-search-extension`         |
| gateway                          | none                                                                    | none                                                                         |

The environment variable `DITTO_PERSISTENCE_BACKEND` (default `mongodb`) names the folder that is put on the
classpath. In the Helm chart it is the value `global.persistenceBackend`. The backend wires itself through a
fixed-name profile in its JARs, including the journals and snapshot stores the service starts, so no
`<service>-extension.conf` change is needed to switch.

Policies, things, connectivity and things-search need **exactly one** backend on the classpath. With none, or with
several (for example the default `mongodb` folder plus PostgreSQL JARs mounted into `/opt/ditto/extensions/`), they
refuse to start before any
actor starts, and the message names `DITTO_PERSISTENCE_BACKEND` and the fix. A mixed layout, such as things-search on
MongoDB and the persistence services on PostgreSQL, sets the variable per service.

How to configure and run the PostgreSQL backend is described in
[Running Ditto on PostgreSQL](installation-postgresql.html); how to operate it (schema lifecycle, self-heal,
connection budget, TLS, PgBouncer, monitoring, vacuum, differences from MongoDB) in
[Operating PostgreSQL](operating-postgresql.html).

#### Upgrading a PostgreSQL deployment that staged JARs

Deployments that copied or mounted the PostgreSQL extension JARs into `/opt/ditto/extensions/` (for example with an
init container and a shared volume) must change three things when they move to an image with
`/opt/ditto/backends/`:

1. Remove the init container and the volume, or the mount into `/opt/ditto/extensions/`.
2. Set `DITTO_PERSISTENCE_BACKEND=postgresql` on each PostgreSQL service (Helm: `global.persistenceBackend: postgresql`).
3. Drop the activation overlay in `<service>-extension.conf`. Its `include` is now harmless but no longer needed, and
   its auto-start narrowing lines are superseded by the backend profile.

A deployment that keeps the staging and leaves the variable unset gets the default `mongodb` folder plus the staged
PostgreSQL JARs: the services refuse to boot because they find two backends, and the message names the fix.

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Architecture Overview](architecture-overview.html)
