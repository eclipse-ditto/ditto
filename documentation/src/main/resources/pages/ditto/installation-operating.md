---
title: Operating Ditto
tags: [installation]
keywords: operating, docker, docker-compose, devops, logging, logstash, elk, monitoring, graphite, grafana
permalink: installation-operating.html
---

After Ditto was once successfully started, the next step is to set it up for continuously operating it.

This page shows the basics for operating Ditto.


## Logging

Gathering logs for a running Ditto installation can be achieved via two different ways:

1. grep log output from STDOUT/STDERR via Docker's [logging drivers](https://docs.docker.com/engine/admin/logging/overview/)
   * Benefits: simple, works with all Docker logging drivers (e.g. "awslogs", "splunk", ...)

2. configure a [Logstash](https://www.elastic.co/products/logstash) endpoint for Ditto's Docker containers where
   the logs should be published to
   * Benefits: stack traces are preserved, metadata from logback.xml is enhanced (e.g. "instance-index")

Option 2 uses the [ELK stack](https://www.elastic.co/elk-stack) for logging, option 1 may also use an ELK stack with the right 
Docker logging driver.

### Configuring Logstash endpoint

The Logstash endpoint to use can simply be configured by setting the following environment variable for the Docker containers:

```
LOGSTASH_SERVER=localhost:4560
```

Once that variable is configured, Ditto will automatically publish all logs via the `LogstashTcpSocketAppender` to that
endpoint from where Logstash can forward it to an Elasticsearch from where Kibana can access the logs.


## Monitoring

Additionally to the obligatory logging monitoring is included in the Ditto images. That means that certain metrics are 
automatically gathered and published via [StatsD](https://github.com/etsy/statsd) to a 
[Graphite](https://graphite.readthedocs.io) backend from where the metrics can be accessed to display in dashboards 
(e.g. with [Grafana](https://grafana.com)).

### Gathered metrics

* JVM metrics: `stats.timers.$Application.$Host.system-metric.*`
    * amount of garbage collections + GC times
    * memory consumption (heap + non-heap)
    * amount of threads + loaded classes
* HTTP metrics: `stats.timers.Gateway.$Host.trace.roundtrip_http_*.elapsed-time.mean`
    * roundtrip times from request to response
    * amount of HTTP calls
    * status code of HTTP responses
* MongoDB metrics: `stats.gauges.$Application.$Host.akka-persistence-mongo.journal.casbah.*`
    * inserts, updates, reads per second
    * roundtrip times

### Configuring the StatsD endpoint

The StatsD endpoint to use can simply be configured by setting the following environment variables for the Docker containers:

```
STATSD_PORT_8125_UDP_ADDR=localhost
STATSD_PORT_8125_UDP_PORT=8125
```

Once that variable is configured, Ditto will automatically publish gathered metrics via UDP to the StatsD server.

Grafana can then be configured to use Graphite (which StatsD sends the data to) as its data source and can display 
the metrics based on the keys mentioned in section ["Gathered metrics"](#gathered-metrics).


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

