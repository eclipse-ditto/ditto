---
title: Operating Ditto
tags: [installation]
keywords: operating, docker, docker-compose, devops, logging, logstash, elk, monitoring, prometheus, grafana
permalink: installation-operating.html
---

[pubsubmediator]: https://doc.akka.io/docs/akka/current/distributed-pub-sub.html

Once you have successfully started Ditto, proceed with setting it up for continuous operation.

This page shows the basics for operating Ditto.


## Logging

Gathering logs for a running Ditto installation can be achieved by:

* grepping log output from STDOUT/STDERR via Docker's [logging drivers](https://docs.docker.com/engine/admin/logging/overview/)
   * Benefits: simple, works with all Docker logging drivers (e.g. "awslogs", "splunk", etc.)

This option may also use an ELK stack with the right Docker logging driver.


## Monitoring

In addition to logging, the Ditto images include monitoring features. Specific metrics are
automatically gathered and published on a HTTP port. There it can be scraped by a [Prometheus](https://prometheus.io)
backend, from where the metrics can be accessed to display in dashboards (e.g. with [Grafana](https://grafana.com)).

### Configuring

In the default configuration, each Ditto service opens a HTTP endpoint, where it provides the Prometheus metrics on port
`9095`. This can be changed via the environment variable `PROMETHEUS_PORT`.

Ditto will automatically publish gathered metrics at the endpoint `http://<container-host-or-ip>:9095/`.

Further, Prometheus can be configured to poll on all Ditto service endpoints in order to persist the historical metrics.
Grafana can add a Prometheus server as its data source and can display 
the metrics based on the keys mentioned in section ["Gathered metrics"](#gathered-metrics).

### Gathered metrics

In order to inspect which metrics are exported to Prometheus, just visit the Prometheus HTTP endpoint of a Ditto service:
`http://<container-host-or-ip>:9095/`.

The following example shows an excerpt of metrics gathered for the
[gateway-service](architecture-services-gateway.html).

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

To put it in a nutshell, Ditto reports:

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

The "DevOps commands" API allows Ditto operators to make changes to a running installation without restarts.

The following DevOps commands are supported:

* Dynamically retrieve and change log levels
* Dynamically retrieve service configuration
* Piggyback commands


### Dynamically adjust log levels

Changing the log levels dynamically is very useful when debugging an accidental problem,
since the cause of the problem could be lost on service restart.

#### Retrieve all log levels

Example for retrieving all currently configured log levels:<br/>
`GET /devops/logging`

Response:

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

Response:

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

#### Change a specific log level for one service

Example request payload to change the log level of logger `org.eclipse.ditto` in all
instances of gateway-service to `DEBUG`:

`PUT /devops/logging/gateway`

```json
{
    "logger": "org.eclipse.ditto",
    "level": "debug"
}
```

### Dynamically retrieve configurations

Runtime configurations of services are available for the Ditto operator at
`/devops/config/` with optional restrictions by service name, instance ID and configuration path.
The entire runtime configuration of a service may be dozens of kilobytes big. If it exceeds the cluster message size
of 250 kB, then it can only be read piece by piece via the `path` query parameter.

#### Retrieve all service configurations

Retrieve the configuration at the path `ditto.info` thus:

`GET /devops/config?path=ditto.info`

It is recommended to not omit the query parameter `path`. Otherwise the full configurations of all services are
aggregated in the response, which can become megabytes big.

The path `ditto.info` points to information on service name, service instance index, JVM arguments and environment
variables. Response example:

```json
{
  "?": {
    "?": {
      "type": "common.responses:retrieveConfig",
      "status": 200,
      "config": {
        "env": {
          "PATH": "/usr/games:/usr/local/games"
        },
        "service": {
          "instance-index": "1",
          "service-name": "gateway"
        },
        "vm-args": [
          "-Dfile.encoding=UTF-8"
        ]
      }
    },
    "?1": {
      "type": "common.responses:retrieveConfig",
      "status": 200,
      "config": {
        "env": {
          "CONNECTIVITY_FLUSH_PENDING_RESPONSES_TIMEOUT": "3d"
        },
        "service": {
          "instance-index": "1",
          "service-name": "connectivity"
        },
        "vm-args": [
          "-Dditto.connectivity.connection.snapshot.threshold=2"
        ]
      }
    }
  }
}
```

#### Retrieve the configuration of a service instance

Retrieving the configuration of a specific service instance is much faster
because the response is not aggregated from an unknown number of respondents
over the duration given in the query parameter `timeout`.

To retrieve `ditto` configuration from Gateway instance `1`:

`GET /devops/config/gateway/1?path=ditto`

Response example:

```json
{
  "?": {
    "?": {
      "type": "common.responses:retrieveConfig",
      "status": 200,
      "config": {
        "cluster": {
          "number-of-shards": 20
        },
        "gateway": {
          "authentication": {
            "devops": {
              "password": "foobar",
              "securestatus": false
            }
          }
        }
      }
    }
  }
}
```

### Piggyback commands

You can use a DevOps command to send a command to another actor in the cluster.
Those special commands are called piggyback commands.
A piggyback command must conform to the following schema:

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

#### Managing background cleanup

Ditto deletes unnecessary events and snapshots in the background according to database load.
[Concierge](architecture-services-concierge.html) has a cluster-singleton coordinating the background cleanup process.
The cluster singleton responds to piggyback-commands to query its state and configuration, modify the configuration,
and restart the background cleanup process.

Each command is sent to the actor selection `/user/conciergeRoot/eventSnapshotCleanupCoordinatorProxy` on _one_
Concierge instance, typically `INSTANCE_INDEX=1` in a docker-based installation:

`POST /devops/piggygack/concierge/<INSTANCE_INDEX>?timeout=10000`


##### Query background cleanup coordinator state

`POST /devops/piggygack/concierge/<INSTANCE_INDEX>?timeout=10000`

```json
{
  "targetActorSelection": "/user/conciergeRoot/eventSnapshotCleanupCoordinatorProxy",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "status.commands:retrieveHealth"
  }
}
```

The response has the following details:

- `events`: State transitions of the actor. The top entry is the current state of the actor.
- `credit-decisions`: Decisions on how many cleanup actions were permitted, when, and why.
- `actions`: Log of cleanup actions, their round-trip times, and whether they were successful.


```json
{
  "?": {
    "?": {
      "type": "status.responses:retrieveHealth",
      "status": 200,
      "statusInfo": {
        "status": "UP",
        "details": [
          {
            "INFO": {
              "events": [
                { "2019-06-24T13:42:29.878Z": "Stream terminated. Result=<Done> Error=<null>" },
                { "2019-06-24T13:42:19.474Z": "WOKE_UP" }
              ],
              "credit-decisions": [
                { "2019-06-24T13:42:29.609Z": "100: maxTimeNanos=0 is below threshold=20000000" },
                { "2019-06-24T13:42:25.232Z": "0: maxTimeNanos=47358000 is above threshold=20000000" }
              ],
              "actions": [
                { "2019-06-24T13:42:28.801Z": "200 start=2019-06-24T13:42:28.755Z <thing:ditto:thing1>" }
              ]
            }
          }
        ]
      }
    }
  }
}
```

##### Query background cleanup coordinator configuration

`POST /devops/piggygack/concierge/<INSTANCE_INDEX>?timeout=10000`

```json
{
  "targetActorSelection": "/user/conciergeRoot/eventSnapshotCleanupCoordinatorProxy",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "common.commands:retrieveConfig"
  }
}
```

Response example:

```json
{
  "?": {
    "?": {
      "type": "common.responses:retrieveConfig",
      "status": 200,
      "config": {
        "cleanup-timeout": "30s",
        "credit-decision": {
          "credit-per-batch": 100,
          "interval": "10s",
          "metric-report-timeout": "10s",
          "timer-threshold": "20ms"
        },
        "keep": {
          "actions": 120,
          "credit-decisions": 30,
          "events": 15
        },
        "parallelism": 1,
        "persistence-ids": {
          "burst": 25,
          "stream-idle-timeout": "10m",
          "stream-request-timeout": "10s"
        },
        "quiet-period": "5m"
      }
    }
  }
}
```

##### Modify background cleanup coordinator configuration

Send a piggyback command of type `common.commands:modifyConfig` to change the configuration of the background cleanup
coordinator. All subsequent cleanup processes will use the new configuration. Any ongoing cleanup is not affected.
Configurations absent in the payload of the piggyback command remain unchanged.

`POST /devops/piggygack/concierge/<INSTANCE_INDEX>?timeout=10000`

```json
{
  "targetActorSelection": "/user/conciergeRoot/eventSnapshotCleanupCoordinatorProxy",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "common.commands:modifyConfig",
    "config": {
      "quiet-period": "240d"
    }
  }
}
```

The response contains the effective configuration of the background cleanup coordinator. If the configuration in the
piggyback command contains any error, then an error is logged and the actor's configuration is unchanged.

```json
{
  "?": {
    "?": {
      "type": "common.responses:modifyConfig",
      "status": 200,
      "config": {
        "cleanup-timeout": "30s",
        "credit-decision": {
          "credit-per-batch": 100,
          "interval": "10s",
          "metric-report-timeout": "10s",
          "timer-threshold": "20ms"
        },
        "keep": {
          "actions": 120,
          "credit-decisions": 30,
          "events": 15
        },
        "parallelism": 1,
        "persistence-ids": {
          "burst": 25,
          "stream-idle-timeout": "10m",
          "stream-request-timeout": "10s"
        },
        "quiet-period": "240d"
      }
    }
  }
}
```


##### Modify background cleanup coordinator configuration

Send a piggyback command of type `common.commands:shutdown` to stop the background cleanup process.
The next process is scheduled after the `quiet-period` duration in the coordinator's configuration.

`POST /devops/piggygack/concierge/<INSTANCE_INDEX>?timeout=10000`

```json
{
  "targetActorSelection": "/user/conciergeRoot/eventSnapshotCleanupCoordinatorProxy",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "common.commands:shutdown"
  }
}
```

Response example:

```json
{
  "?": {
    "?": {
      "type": "common.responses:shutdown",
      "status": 200,
      "message": "Restarting stream in <PT5760H30M5S>."
    }
  }
}
```

##### Cleanup events and snapshots of an entity

Send a cleanup command by piggyback to the entity's service and shard region to trigger removal of stale events and
snapshots manually. Here is an example for things. Change the service name and shard region name accordingly for
policies and connections. Typically in a docker based environment, use `INSTANCE_INDEX=1`.


`POST /devops/piggygack/things/<INSTANCE_INDEX>?timeout=10000`

```json
{
  "targetActorSelection": "/system/sharding/thing",
  "headers": {
    "aggregate": false
  },
  "piggybackCommand": {
    "type": "cleanup.commands:cleanupPersistence",
    "entityId": "ditto:thing1"
  }
}
```

Response example:

```json
{
  "?": {
    "?": {
      "type": "cleanup.responses:cleanupPersistence",
      "status": 200,
      "entityId": "thing:ditto:thing1"
    }
  }
}
```

#### Erasing data within a namespace

Ditto supports erasure of _all_ data within a namespace during live operations.
To do so safely, perform the following steps in sequence.

1. [Block all messages to the namespace](#block-all-messages-to-a-namespace)
   so that actors will not spawn in the namespace.
2. [Shutdown all actors in the namespace](#shutdown-all-actors-in-a-namespace)
   so that no actor will generate data in the namespace.
3. [Erase data from the persistence](#erase-all-data-in-a-namespace-from-the-persistence).
4. [Unblock messages to the namespace](#unblock-messages-to-a-namespace)
   so that the old namespace could be reused at a later point in time.

##### Block all messages to a namespace

Send a piggyback command to [Akka's pub-sub-mediator][pubsubmediator] with type `namespaces.commands:blockNamespace`
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

Once a namespace is blocked on all members of the Ditto cluster, you will get a response
similar to the one below. The namespace will remain blocked for the lifetime of the Ditto cluster,
or until you proceed with [step 4](#unblock-messages-to-a-namespace), which unblocks it.

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

Send a piggyback command to [Akka's pub-sub-mediator][pubsubmediator] with type `common.commands:shutdown`
to request all actors in a namespace to shut down. The value of `piggybackCommand/reason/type` must be
`purge-namespace`; otherwise, the namespace's actors will not stop themselves.

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

Send a piggyback command to [Akka's pub-sub-mediator][pubsubmediator] with type `namespaces.commands:purgeNamespace`
to erase all data from the persistence.
It is better to purge a namespace after
[blocking](#block-all-messages-to-a-namespace) it and
[shutting down](#shutdown-all-actors-in-a-namespace)
all its actors so that no data is written in the namespace while erasing is ongoing.

The erasure may take a long time if the namespace has a lot of data associated with it or if the persistent storage is
slow. Set the timeout to a safe margin above the estimated erasure time in milliseconds.

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

Send a piggyback command to [Akka's pub-sub-mediator][pubsubmediator] with type `namespaces.commands:unblockNamespace`
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
