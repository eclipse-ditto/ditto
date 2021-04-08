---
title: Cloud Events HTTP protocol binding
keywords: binding, protocol, http, cloudevents
tags: [binding, protocol, http]
permalink: httpapi-protocol-bindings-cloudevents.html
---

Implements the [HTTP Protocol Binding for CloudEvents - Version 1.0](https://github.com/cloudevents/spec/blob/v1.0/http-protocol-binding.md).

Unless mentioned otherwise, the endpoint following the Cloud Events specification for the HTTP binding in version 1.0.

## Cloud Events features

The Cloud Events endpoint provides an alternative to the other connectivity APIs to stream data into your instance.

## Cloud Events endpoint

The Cloud Events endpoint is accessible at the following URL:

```
http://localhost:8080/api/2/cloudevents
```

### Authentication

A user who connects to the Cloud Events endpoint can be authenticated by using

* HTTP BASIC Authentication by providing a username and the password of a user managed within nginx or
* a JSON Web Token (JWT) issued by an OpenID connect provider.

See [Authenticate](basic-auth.html) for more details.

## Cloud Events protocol format

The source must be a Cloud Event, encoded in the format for the HTTP binding. It may be encoded in *binary content mode*
or in *structural content mode*.

The *data content type* of the event must be `application/json`.

The *data schema* must start with `ditto:`, for example `ditto:some-schema`.

The events *payload* must in the Ditto Protocol JSON format as defined in the
[Protocol specification](protocol-specification.html).

## Publishing events to the endpoint

Publishing events to the endpoint can be done by directly sending HTTP requests, conforming to the Cloud Events
HTTP binding specification. Or by using other technologies that have adopted Cloud Events.

### Knative eventing

The endpoint can directly be configured as a [Knative eventing](https://knative.dev/docs/eventing/) destination.

In the following example, a Knative eventing flow is configured to normalize the payload with a Vorto converter
and send the result to Ditto's cloud events endpoint:

~~~yaml
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
      # Convert incoming payload to the Ditto format
      apiVersion: serving.knative.dev/v1
      kind: Service
      name: vorto-converter
      namespace: digital-twin
  reply:
    # Deliver to Ditto Cloud Events endpoint
    uri: http://ditto:ditto@ditto-nginx.digital-twin.svc.cluster.local:8080/api/2/cloudevents
~~~

This sequence itself can again be the target of another operation.

### Direct invocation

Of course, it is also possible to directly access the Cloud Events endpoint through HTTP:

An example HTTP request could look like this:

~~~
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
~~~

For more information, see [HTTP Protocol Binding for CloudEvents - Version 1.0](https://github.com/cloudevents/spec/blob/v1.0/http-protocol-binding.md).
