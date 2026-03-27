---
title: Operating - Monitoring & Tracing
tags: [installation]
keywords: operating, monitoring, logging, tracing, prometheus, grafana, metrics, opentelemetry, elk, logstash
permalink: operating-monitoring.html
---

You monitor Ditto through structured logging, Prometheus metrics, and OpenTelemetry-based distributed tracing.

{% include callout.html content="**TL;DR**: Ditto exposes Prometheus metrics on port 9095 by default. Enable tracing with `DITTO_TRACING_ENABLED=true` and point the OTLP exporter to your collector. Configure logging output via environment variables for STDOUT, ELK, or file-based appenders." type="primary" %}

## Overview

Ditto provides three observability pillars:

* **Logging**: STDOUT, ELK (Logstash), or file-based log output
* **Monitoring**: Prometheus-compatible metrics endpoint
* **Tracing**: W3C Trace Context propagation with OpenTelemetry export

## Logging

### Log output options

You can gather logs from a running Ditto installation in three ways:

**STDOUT/STDERR (default):**
* Works with all Docker logging drivers (awslogs, splunk, etc.)
* Disable with `DITTO_LOGGING_DISABLE_SYSOUT_LOG=true`

**ELK stack (Logstash):**
* Set `DITTO_LOGGING_LOGSTASH_SERVER` to your Logstash endpoint

**Log files:**
* Enable with `DITTO_LOGGING_FILE_APPENDER=true`
* Log files use LogstashEncoder format for easy ELK import

### File appender configuration

When you enable file-based logging, configure these environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DITTO_LOGGING_FILE_APPENDER_THRESHOLD` | `info` | Minimum log level |
| `DITTO_LOGGING_FILE_NAME_PATTERN` | `/var/log/ditto/<service>.log.%d{yyyy-MM-dd}.gz` | File name pattern (rollover period inferred from pattern) |
| `DITTO_LOGGING_MAX_LOG_FILE_HISTORY` | `10` | Maximum number of archived log files |
| `DITTO_LOGGING_TOTAL_LOG_FILE_SIZE` | `1GB` | Total disk space for log files (requires `MAX_LOG_FILE_HISTORY` to be set) |
| `DITTO_LOGGING_CLEAN_HISTORY_ON_START` | `false` | Delete old logs on service start |

See the [logback documentation](https://logback.qos.ch/manual/appenders.html#TimeBasedRollingPolicy) for details on these settings.

When running Ditto in Kubernetes, apply the `ditto-log-files.yaml` manifest to mount log files to the host system.

### Dynamic log level changes

You can change log levels at runtime without restarting services through the [DevOps commands API](operating-devops.html).

## Monitoring

### How it works

Each Ditto service exposes a Prometheus-compatible HTTP endpoint where metrics are published automatically. Prometheus scrapes these endpoints, and you visualize the data with tools like [Grafana](https://grafana.com).

### Configuration

Each service publishes metrics on port `9095` by default. Change this with the `PROMETHEUS_PORT` environment variable.

The metrics endpoint is available at:
```text
http://<container-host-or-ip>:9095/
```

### Gathered metrics

Visit the Prometheus endpoint of any service to see the full list of exported metrics. Ditto reports:

* **JVM metrics** (all services): garbage collection counts and times, memory consumption (heap and non-heap), thread counts, loaded classes
* **HTTP metrics** ([gateway service](architecture-services-gateway.html)): roundtrip times, request counts, response status codes
* **MongoDB metrics** ([things](architecture-services-things.html), [policies](architecture-services-policies.html), [things-search](architecture-services-things-search.html)): inserts, updates, reads per second, roundtrip times
* **Connection metrics** ([connectivity service](architecture-services-connectivity.html)): processed messages, mapping times

Explore the [example Grafana dashboards](https://github.com/eclipse-ditto/ditto/tree/master/deployment/operations/grafana-dashboards) and contribute new ones back to the community.

### Custom metrics

Since Ditto 3.5.0, you can define custom metrics that count things matching a namespace/filter combination. Configure these in the [search service](architecture-services-things-search.html).

```hocon
ditto {
  search {
    operator-metrics {
      enabled = true
      scrape-interval = 15m
      custom-metrics {
        all_produced_and_not_installed_devices {
          scrape-interval = 5m
          namespaces = [
            "org.eclipse.ditto.smokedetectors"
          ]
          filter = "and(exists(attributes/production-date),not(exists(attributes/installation-date)))"
          tags {
            company = "acme-corp"
          }
        }
      }
    }
  }
}
```

Ditto performs a [count things operation](basic-search.html#search-count-queries) at the configured interval and exposes the result as a Prometheus gauge:

```text
all_produced_and_not_installed_devices{company="acme-corp"} 42.0
```

### Custom aggregation metrics

Since Ditto 3.6.0, you can define aggregation-based metrics with dynamic tags populated from thing data using the `group-by` placeholder.

{% include warning.html content="Avoid grouping by fields with high cardinality, as this creates many metric series and may overload your Prometheus server." %}

```hocon
ditto {
  search {
    operator-metrics {
      custom-aggregation-metrics {
        online_things {
          enabled = true
          scrape-interval = 20m
          namespaces = ["org.eclipse.ditto"]
          group-by {
            "location" = "attributes/Info/location"
            "isGateway" = "attributes/Info/gateway"
          }
          tags {
            "hardcoded-tag" = "hardcoded_value"
            "location" = "{%raw%}{{ group-by:location | fn:default('missing location') }}{%endraw%}"
            "isGateway" = "{%raw%}{{ group-by:isGateway }}{%endraw%}"
          }
          filter = "gt(features/ConnectionStatus/properties/status/readyUntil,time:now)"
        }
      }
    }
  }
}
```

This produces metrics like:

```text
online_things{location="Berlin",isGateway="false",hardcoded-tag="hardcoded_value"} 6.0
online_things{location="Immenstaad",isGateway="true",hardcoded-tag="hardcoded_value"} 8.0
```

[Function expressions](basic-placeholders.html#function-expressions) are supported for transforming placeholder values.

## Tracing

### How it works

Ditto supports [W3C Trace Context](https://www.w3.org/TR/trace-context/) headers at the edges of the system (Gateway and Connectivity services). Spans are generated during request processing and exported in [OpenTelemetry](https://opentelemetry.io/) format.

### Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `DITTO_TRACING_ENABLED` | `false` | Enable tracing |
| `DITTO_TRACING_SAMPLER` | `never` | Sampler type: `always`, `never`, `random`, `adaptive` |
| `DITTO_TRACING_RANDOM_SAMPLER_PROBABILITY` | -- | Probability for `random` sampler |
| `DITTO_TRACING_ADAPTIVE_SAMPLER_THROUGHPUT` | -- | Target throughput for `adaptive` sampler |
| `DITTO_TRACING_OTEL_TRACE_REPORTER_ENABLED` | `false` | Enable OTLP trace reporting |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |

## Further reading

* [Operating - Configuration](operating-configuration.html)
* [Operating - DevOps Commands](operating-devops.html)
* [Example Grafana dashboards](https://github.com/eclipse-ditto/ditto/tree/master/deployment/operations/grafana-dashboards)
