---
title: Operating Ditto
tags: [installation]
keywords: operating, docker, docker-compose, devops, logging, logstash, elk, monitoring, prometheus, grafana
permalink: installation-operating.html
---

After Ditto was once successfully started, the next step is to set it up for continuously operating it.

This page shows the basics for operating Ditto.


## Logging

Gathering logs for a running Ditto installation can be achieved by:

* grepping log output from STDOUT/STDERR via Docker's [logging drivers](https://docs.docker.com/engine/admin/logging/overview/)
   * Benefits: simple, works with all Docker logging drivers (e.g. "awslogs", "splunk", ...)

This option may also use an ELK stack with the right Docker logging driver.


## Monitoring

Additionally to the obligatory logging monitoring is included in the Ditto images. That means that certain metrics are 
automatically gathered and published on a HTTP port where it can be scraped from a [Prometheus](https://prometheus.io) 
backend from where the metrics can be accessed to display in dashboards (e.g. with [Grafana](https://grafana.com)).

### Configuring

In the default configuration each Ditto service opens a HTTP endpoint where it provides the Prometheus metrics on port
`9095`. This can be changed via the environment variable `PROMETHEUS_PORT`.

Ditto will automatically publish gathered metrics at the endpoint `http://<container-host-or-ip>:9095/`.

Prometheus can then be configured to poll on all Ditto service endpoints in order to persist the historical metrics.
Grafana can add a Prometheus server as its data source and can display 
the metrics based on the keys mentioned in section ["Gathered metrics"](#gathered-metrics).

### Gathered metrics

In order to inspect which metrics are exported to Prometheus, just visit the Prometheus HTTP endpoint of a Ditto service:
`http://<container-host-or-ip>:9095/`, e.g. for the [gateway-service](architecture-services-gateway.html) an excerpt:

```
#Kamon Metrics
# TYPE jvm_threads gauge
jvm_threads{component="system-metrics",measure="total"} 72.0
# TYPE jvm_memory_buffer_pool_count gauge
jvm_memory_buffer_pool_count{component="system-metrics",pool="direct"} 14.0
# TYPE jvm_class_loading gauge
jvm_class_loading{component="system-metrics",mode="loaded"} 10491.0
# TYPE jvm_memory_buffer_pool_usage gauge
jvm_memory_buffer_pool_usage{component="system-metrics",pool="direct",measure="used"} 396336.0
# TYPE roundtrip_http_seconds histogram
roundtrip_http_seconds_bucket{le="0.05",ditto_request_path="/api/1/things/x",ditto_request_method="PUT",ditto_statusCode="201",segment="overall"} 1.0
roundtrip_http_seconds_sum{ditto_request_path="/api/1/things/x",ditto_statusCode="201",ditto_request_method="PUT",segment="overall"} 0.038273024
roundtrip_http_seconds_bucket{le="0.001",ditto_request_path="/api/1/things/x",ditto_request_method="PUT",ditto_statusCode="204",segment="overall"} 0.0
roundtrip_http_seconds_bucket{le="0.1",ditto_request_path="/api/1/things/x",ditto_request_method="PUT",ditto_statusCode="204",segment="overall"} 7.0
roundtrip_http_seconds_sum{ditto_request_path="/api/1/things/x",ditto_statusCode="204",ditto_request_method="PUT",segment="overall"} 0.828899328
# TYPE jvm_gc_promotion histogram
jvm_gc_promotion_sum{space="old"} 7315456.0
# TYPE jvm_gc_seconds histogram
jvm_gc_seconds_count{component="system-metrics",collector="scavenge"} 9.0
jvm_gc_seconds_sum{component="system-metrics",collector="scavenge"} 0.063
# TYPE jvm_memory_bytes histogram
jvm_memory_bytes_count{component="system-metrics",measure="used",segment="miscellaneous-non-heap-storage"} 54.0
jvm_memory_bytes_sum{component="system-metrics",measure="used",segment="miscellaneous-non-heap-storage"} 786350080.0
```

So what Ditto reports in a nutshell:

* JVM metrics for all services
    * amount of garbage collections + GC times
    * memory consumption (heap + non-heap)
    * amount of threads + loaded classes
* HTTP metrics for [gateway-service](architecture-services-gateway.html)
    * roundtrip times from request to response
    * amount of HTTP calls
    * status code of HTTP responses
* MongoDB metrics for [things-service](architecture-services-things.html), 
[policies-service](architecture-services-policies.html), [things-search-service](architecture-services-things-search.html)
    * inserts, updates, reads per second
    * roundtrip times
* cache metrics for [concierge-service](architecture-services-concierge.html)
* connection metrics for [connectivity-service](architecture-services-connectivity.html)
    * processed messages
    * mapping times

## DevOps commands

Starting with Ditto Milestone 0.1.0-M2, an HTTP API for so called "DevOps commands" was added.<br/>
This API is intended for making operating Ditto easier by reducing the need to restart Ditto services for configuration
changes.

Following DevOps commands are supported:


* Dynamically retrieve and change log levels
* Piggyback commands


### Dynamically adjust log levels

Dynamically changing the log levels is a very useful tool when debugging a problem occurring only in production without
the need to restart the server and thereby fixing the bug.

#### Retrieve all log levels

Example response for retrieving all currently configured log levels:<br/>
`GET /devops/logging`

```json
{
    "gateway": {
        "1": {
            "type": "devops.responses:retrieveLoggerConfig",
            "status": 200,
            "serviceName": "gateway",
            "instance": 1,
            "loggerConfigs": [{
                "level": "info",
                "logger": "ROOT"
            }, {
                "level": "info",
                "logger": "org.eclipse.ditto"
            }, {
                "level": "warn",
                "logger": "org.mongodb.driver"
            }]
        }
    },
    "things-search": {
        ...
    },
    "policies": {
        ...
    },
    "things": {
        ...
    },
    "connectivity": {
        ...
    }
}
```

#### Change a specific log level for all services

Example request payload to change the log level of logger `org.eclipse.ditto` in all services to `DEBUG`:<br/>
`PUT /devops/logging`

```json
{
    "logger": "org.eclipse.ditto",
    "level": "debug"
}
```

#### Retrieve log levels of a service

Example response for retrieving all currently configured log levels of gateways services:<br/>
`GET /devops/logging/gateway`

```json
{
    "1": {
        "type": "devops.responses:retrieveLoggerConfig",
        "status": 200,
        "serviceName": "gateway",
        "instance": 1,
        "loggerConfigs": [{
            "level": "info",
            "logger": "ROOT"
        }, {
            "level": "info",
            "logger": "org.eclipse.ditto"
        }, {
            "level": "warn",
            "logger": "org.mongodb.driver"
        }]
    }
}
```

#### Change a specific log level for one services

Example request payload to change the log level of logger `org.eclipse.ditto` in all gateway services to `DEBUG`:<br/>
`PUT /devops/logging/gateway`

```json
{
    "logger": "org.eclipse.ditto",
    "level": "debug"
}
```

### Piggyback commands

You can use a DevOps command to send a command to another actor in the cluster. Those special commands are named
piggyback commands. A piggyback command must conform to the following schema:

{% include docson.html schema="jsonschema/piggyback-command.json" %}

Example:

```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:createConnection",
        ...
    }
}
```

Currently piggybacks are only used to configure Dittos connectivity service. More information on this can be found in
the [Manage Connections](connectivity-manage-connections.html) section.

