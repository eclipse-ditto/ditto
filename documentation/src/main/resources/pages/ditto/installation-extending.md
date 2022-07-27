---
title: Extending Ditto
tags: [getting_started, installation]
keywords: running, start, run, docker, docker-compose, extension, custom
permalink: installation-extending.html
---

## Create Extensions for  Ditto
Ditto offers the possibility to execute custom behaviour by utilizing Akka Extensions. The places which can be 
extended by such custom behaviour are marked by extending the ```DittoExtensionPoint``` interface. Add a new 
implementation of an interface extending ```DittoExtensionPoint``` for changing its behaviour.

The implementation needs a public constructor accepting an ActorSystem and Config, for the Akka Classloader to load 
the extension via reflection.
``` java
public CustomExtension(final ActorSystem actorSystem, final Config config) {}
```

## Configure Extensions
In order for the Akka Classloader to load the correct implementation of a ```DittoExtensionPoint```, the 
implementation has to be configured. This can be done by adding the ```CONFIG_KEY``` of the extension either to the 
```service-extension.conf``` if the extension should only be loaded in specific services, or to the ```reference.conf``` 
for a global scope.

The configuration for an extension consists of two parts:
- ```extension-class```: specify the implementation that should be used by the canonical name.
- ```extension-config```: specify custom configurations for the extension.

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

If no custom configuration is needed, the ```extension-config``` can be omitted, thus directly specifying the 
implementation.

```
ditto.extensions.signal-enrichment-provider = org.eclipse.ditto.gateway.service.endpoints.utils.DefaultGatewaySignalEnrichmentProvider
```

## Extend Ditto Docker images
The new extensions and their corresponding configuration have to be in the Java classpath of the Ditto service which 
loads them. To achieve this, the Ditto docker images automatically add all jars, that are in the home directory of 
the docker container (```/opt/ditto/```) into the classpath.

The easiest way to achieve this, is thus building an 
extension jar (including the extension classes and extension config files) and adding it to the home directory of the 
docker container.

## Example
- Create a new implementation of the ```CustomApiRoutesProvider```, overriding the ```unauthorized(*)``` and 
```authorized(*)``` functions, returning custom HTTP API routes.
- Configure the new ```CustomApiRoutesProvider``` via a ```gateway-extension.conf```.
    ```
    ditto.extensions.custom-api-routes-provider = org.company.project.gateway.service.endpoints.utils.MyCustomApiRoutesProvider
    ```
- Build the project to a new ```gateway-extension.jar```
- Add the ```gateway-extension.jar``` to the ```/opt/ditto/``` directory of the docker images, by i.e. copying the jar 
into the container.
    ```
    docker cp gateway-extension.jar container_id:/opt/ditto/
    ```
