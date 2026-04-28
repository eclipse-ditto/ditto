---
title: Cloud Events Binding
keywords: binding, protocol, http, cloudevents
tags: [binding, protocol, http]
permalink: httpapi-protocol-bindings-cloudevents.html
---

You use the Cloud Events endpoint to ingest data into Ditto using the standardized [CloudEvents HTTP Protocol Binding (v1.0)](https://github.com/cloudevents/spec/blob/v1.0/http-protocol-binding.md), enabling integration with CloudEvents-aware platforms like Knative.

{% include callout.html content="**TL;DR**: Send CloudEvents-formatted HTTP requests to `/api/2/cloudevents` with Ditto Protocol JSON as the payload and `ditto:` as the data schema prefix." type="primary" %}

## Overview

The Cloud Events endpoint provides an alternative to the other connectivity APIs for streaming data into your Ditto instance. It implements the [HTTP Protocol Binding for CloudEvents - Version 1.0](https://github.com/cloudevents/spec/blob/v1.0/http-protocol-binding.md).

## How it works

### Endpoint

```
http://localhost:8080/api/2/cloudevents
```

### Authentication

Authenticate using:
* **HTTP Basic Authentication** with a username and password managed by your reverse proxy (e.g., nginx)
* **JSON Web Token (JWT)** issued by an OpenID Connect provider

See [Authentication](basic-auth.html) for details.

### Message format

Your Cloud Event must satisfy these requirements:

| Field | Requirement |
|-------|-------------|
| Encoding | Binary content mode or structured content mode |
| Data content type | `application/json` |
| Data schema | Must start with `ditto:` (e.g., `ditto:some-schema`) |
| Payload | [Ditto Protocol JSON](protocol-specification.html) |

## Examples

### Direct HTTP invocation

```
POST /api/2/cloudevents HTTP/1.1
ce-specversion: 1.0
ce-type: my.ditto.event
ce-time: 2020-11-24T14:35:00Z
ce-id: f7b197fe-2e59-11eb-a8f4-d45d6455d2cc
ce-source: /my/source
ce-dataschema: ditto:some-schema
Content-Type: application/json; charset=utf-8

{
    ... Ditto Protocol JSON ...
}
```

### Knative eventing

You can configure the Cloud Events endpoint as a [Knative eventing](https://knative.dev/docs/eventing/) destination. This example shows a Knative Sequence that normalizes payloads with a converter before sending them to Ditto:

```yaml
apiVersion: flows.knative.dev/v1
kind: Sequence
metadata:
 name: digital-twin
spec:
  channelTemplate:
    apiVersion: messaging.knative.dev/v1alpha1
    kind: KafkaChannel
    spec:
      numPartitions: 1
      replicationFactor: 1
  steps:
  - ref:
      apiVersion: serving.knative.dev/v1
      kind: Service
      name: vorto-converter
      namespace: digital-twin
  reply:
    uri: http://ditto:ditto@ditto-nginx.digital-twin.svc.cluster.local:8080/api/2/cloudevents
```

## Further reading

* [HTTP Protocol Binding for CloudEvents - Version 1.0](https://github.com/cloudevents/spec/blob/v1.0/http-protocol-binding.md)
* [Ditto Protocol specification](protocol-specification.html) -- the payload format
* [Knative eventing](https://knative.dev/docs/eventing/) -- event-driven platform
