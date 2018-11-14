---
title: Operating Ditto
tags: [installation]
keywords: operating, docker, docker-compose, devops, logging, logstash, elk, monitoring, prometheus, grafana
permalink: installation-operating.html
---

pubsubmediator: https://doc.akka.io/docs/akka/current/distributed-pub-sub.html

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

#### Managing connections

Piggybacks are used to configure Dittos connectivity service. More information on this can be found in
the [Manage Connections](connectivity-manage-connections.html) section.

#### Erasing data within a namespace

Ditto supports erasure of _all_ data within a namespace during live operations.
To do so safely, perform the following steps in sequence.

1. [Block all messages to the namespace](#block-all-messages-to-a-namespace)
   so that actors will not spawn in the namespace.
2. [Shutdown all actors in the namespace](#shutdown-all-actors-in-a-namespace)
   so that no actor will generate data in the namespace.
3. [Erase data from the persistence](#erase-all-data-in-a-namespace-from-the-persistence).
4. [Unblock messages to the namespace](#unblock-messages-to-a-namespace)
   so that it can be reused.

##### Block all messages to a namespace

Send a piggyback command to [Akka's Pub-Sub Mediator](pubsubmediator) with type `namespaces.commands:blockNamespace`
to block all messages sent to actors belonging to a namespace.

`PUT /devops/piggygack?timeout=10000`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "namespaces.commands:blockNamespace",
    "namespace": "namespaceToBlock"
  }
}
```

A response will come once the namespace is blocked on all members of the Ditto cluster.
Once blocked, a namespace stays blocked for the lifetime of the Ditto cluster,
until [unblocked](#unblock-messages-to-a-namespace).

```json
{
  "?": {
    "?": {
      "type": "namespaces.responses:blockNamespace",
      "status": 200,
      "namespace": "namespaceToBlock",
      "resourceType": "namespaces"
    }
  }
}
```

##### Shutdown all actors in a namespace

Send a piggyback command to [Akka's Pub-Sub Mediator](pubsubmediator) with type `common.commands:shutdown`
to request all actors in a namespace to shutdown. The value of `piggybackCommand/reason/type` must be
`purge-namespace`; otherwise the namespace's actors will not stop themselves.

`PUT /devops/piggygack?timeout=0`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "piggybackCommand": {
    "type": "common.commands:shutdown",
    "reason": {
      "type": "purge-namespace",
      "details": "namespaceToShutdown"
    }
  }
}
```

The shutdown command has no response because the number of actors shutting down can be very large.
The response will always be `408` timeout.
Feel free to send the shutdown command several times to make sure.

##### Erase all data in a namespace from the persistence

Send a piggyback command to [Akka's Pub-Sub Mediator](pubsubmediator) with type `namespaces.commands:purgeNamespace`
to erase all data from the persistence.
It is better to purge a namespace after
[blocking](#block-all-messages-to-a-namespace) it and
[shutting down](#shutdown-all-actors-in-a-namespace)
all its actors so that no data is written in the namespace during its erasure.

The erasure may take a long time if the namespace has a lot of data or if the persistent storage is slow.
Set the timeout to a safe margin above the estimated erasure time in milliseconds.

`PUT /devops/piggygack?timeout=10000`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": {
    "aggregate": true
  },
  "piggybackCommand": {
    "type": "namespaces.commands:purgeNamespace",
    "namespace": "namespaceToPurge"
  }
}
```

The response contains results of the data purge, one for each resource type.
Note that to see responses from multiple resource types, the header `aggregate` must not be `false`.

```json
{
  "?": {
    "?": {
      "type": "namespaces.responses:purgeNamespace",
      "status": 200,
      "namespace": "namespaceToPurge",
      "resourceType": "thing",
      "successful": true
    },
    "?1": {
      "type": "namespaces.responses:purgeNamespace",
      "status": 200,
      "namespace": "namespaceToPurge",
      "resourceType": "policy",
      "successful": true
    },
    "?2": {
      "type": "namespaces.responses:purgeNamespace",
      "status": 200,
      "namespace": "namespaceToPurge",
      "resourceType": "thing-search",
      "successful": true
    }
  }
}
```

##### Unblock messages to a namespace

Send a piggyback command to [Akka's Pub-Sub Mediator](pubsubmediator) with type `namespaces.commands:unblockNamespace`
to stop blocking messages to a namespace.

`PUT /devops/piggygack?timeout=10000`

```json
{
  "targetActorSelection": "/system/distributedPubSubMediator",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "namespaces.commands:unblockNamespace",
    "namespace": "namespaceToUnblock"
  }
}
```

A response will come once the namespace's blockade is released on all members of the Ditto cluster.

```json
{
  "?": {
    "?": {
      "type": "namespaces.responses:unblockNamespace",
      "status": 200,
      "namespace": "namespaceToUnblock",
      "resourceType": "namespaces"
    }
  }
}
```
