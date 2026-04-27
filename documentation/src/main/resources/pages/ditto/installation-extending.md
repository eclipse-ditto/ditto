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

| Service | Extension config path |
|---------|----------------------|
| Policies | `/opt/ditto/policies-extension.conf` |
| Things | `/opt/ditto/things-extension.conf` |
| Search | `/opt/ditto/search-extension.conf` |
| Connectivity | `/opt/ditto/connectivity-extension.conf` |
| Gateway | `/opt/ditto/gateway-extension.conf` |

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

Ditto Docker images automatically add all JARs from these directories to the classpath:

* `/opt/ditto`
* `/opt/ditto/extensions`

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

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Architecture Overview](architecture-overview.html)
