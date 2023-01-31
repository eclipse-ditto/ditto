---
title: Extending Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, extension, custom, configuration, logging
permalink: installation-extending.html
---

## Create Extensions for Ditto
Ditto offers the possibility to execute custom behaviour by utilizing Akka extensions. The places which can be 
extended by such custom behaviour are marked by extending the `DittoExtensionPoint` interface. Add a new 
implementation of an interface extending `DittoExtensionPoint` for changing its behaviour.

The implementation needs a public constructor accepting an ActorSystem and Config, for the Akka Classloader to load 
the extension via reflection.
```java
public CustomExtension(final ActorSystem actorSystem, final Config config) {}
```

## Configure Extensions
In order for the Akka Classloader to load the correct implementation of a `DittoExtensionPoint`, the 
implementation has to be configured. This can be done by adding the `CONFIG_KEY` of the extension either to the 
`<service-name>-extension.conf` if the extension should only be loaded in specific services, or to the `reference.conf`
for a global scope.

The configuration for an extension consists of two parts:
* `extension-class`: specify the implementation that should be used by the canonical name.
* `extension-config`: specify custom configurations for the extension.

```
ditto.extensions.signal-enrichment-provider {
  extension-class = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
  extension-config = {
    ask-timeout = 10s

    cache {
      enabled = true
      maximum-size = 20000
      expire-after-create = 2m
    }
  }
}
```

If no custom configuration is needed, the `extension-config` can be omitted, thus directly specifying the 
implementation.

```
ditto.extensions.signal-enrichment-provider = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
```

## Extend Ditto Docker images

### Adjusting configuration of Ditto
Adjusting configuration is also possible using [system properties](installation-operating.html#ditto-configuration).  
If however lots of configuration changes should be done, a more feasible approach is to provide a 
[HOCON](https://github.com/lightbend/config/blob/main/HOCON.md) formatted configuration file named 
`<ditto-service-name>-extension.conf` in the working directory of Ditto's Docker container:
 
* for extending Ditto's Policies service: `/opt/ditto/policies-extension.conf`
* for extending Ditto's Things service: `/opt/ditto/things-extension.conf`
* for extending Ditto's Search service: `/opt/ditto/search-extension.conf`
* for extending Ditto's Connectivity service: `/opt/ditto/connectivity-extension.conf`
* for extending Ditto's Gateway service: `/opt/ditto/gateway-extension.conf`

Those configuration files can contain any [Ditto configuration](installation-operating.html#ditto-configuration) done in
the service config files.

For example, the [gateway.conf](https://github.com/eclipse-ditto/ditto/blob/master/gateway/service/src/main/resources/gateway.conf)
contains the following configuration snippet:
```hocon
ditto {
  gateway {
    health-check {
      cluster-roles = {
        enabled = true
        enabled = ${?HEALTH_CHECK_ROLES_ENABLED} # may be overridden with this environment variable

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

If this needs to be adjusted, e.g. because the "connectivity" role should not be checked in the health-check 
(which could be the case if `ditto-connectivity` should not be started at all in a Ditto installation), this would be 
possible by creating a `gateway-extension.conf` and adding the following content:
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

And then by putting this `gateway-extension.conf`, e.g. as a Docker volume mount, to the path `/opt/ditto/gateway-extension.conf`.

### Providing additional functionality by adding `.jars` to the classpath
The new extensions and their corresponding configuration have to be in the Java classpath of the Ditto service which 
loads them. To achieve this, the Ditto Docker images automatically add all jars, that are in the home directory of 
the docker container into the classpath:
* `/opt/ditto`
* `/opt/ditto/extensions`

The easiest way to achieve this, is thus building an 
extension jar (including the extension classes and extension config files) and adding it to the home `extensions` 
directory of the Docker container.

## Example
* Create a new implementation of the `CustomApiRoutesProvider`, overriding the `unauthorized(*)` and 
  `authorized(*)` functions, returning custom HTTP API routes.
* Build the project to a new `gateway-extension.jar`
* Add the `gateway-extension.jar` to the `/opt/ditto/extensions` directory of the Docker images, by i.e. copying the jar 
  into the container.
    ```
    docker cp gateway-extension.jar container_id:/opt/ditto/extensions/
    ```
* Configure the new `CustomApiRoutesProvider` via a file `gateway-extension.conf`
    ```
    ditto.extensions.custom-api-routes-provider = org.company.project.gateway.service.endpoints.utils.MyCustomApiRoutesProvider
    ```
* Add the `gateway-extension.conf` to the `/opt/ditto` directory of the Docker images
    ```
    docker cp gateway-extension.conf container_id:/opt/ditto/
    ```
  
Alternatively, you can of course also mount the extension `.jar` and `.conf` file into the Docker containers, e.g.
via docker-compose:
```yaml
  connectivity:
    image: docker.io/eclipse/ditto-gateway:${DITTO_VERSION:-latest}
    ...
    environment:
      - TZ=Europe/Berlin
      - JAVA_TOOL_OPTIONS=-Dlogback.configurationFile=/opt/ditto/logback.xml
    volumes:
      - ./gateway-extension.conf:/opt/ditto/gateway-extension.conf
      - ./logback.xml:/opt/ditto/logback.xml
      - ./gateway-extension.jar:/opt/ditto/extensions/gateway-extension.jar
```