---
title: HMAC Signing
keywords: hmac, hmac-sha256, authorization, azure, aws, azure iot hub, azure service bus, azure monitor, aws version 4 signing, aws sns, signing
tags: [connectivity]
permalink: connectivity-hmac-signing.html
---

You use HMAC signing to authenticate Ditto at external services like AWS and Azure without transmitting credentials directly.

{% include callout.html content="**TL;DR**: Set `credentials.type` to `\"hmac\"` and specify an `algorithm` (`aws4-hmac-sha256`, `az-monitor-2016-04-01`, or `az-sasl`) with the required parameters. Supported on HTTP Push and AMQP 1.0 connections." type="primary" %}

## Overview

Ditto provides an extensible framework for HMAC-based signing authentication for HTTP Push and
AMQP 1.0 connections. Three algorithms are available out of the box:

| Algorithm | Connection types | Use case |
|-----------|-----------------|----------|
| `aws4-hmac-sha256` | HTTP Push | [AWS Version 4 signing](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html) (SNS, S3, etc.) |
| `az-monitor-2016-04-01` | HTTP Push | [Azure Monitor Data Collector](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api#authorization) |
| `az-sasl` | HTTP Push, AMQP 1.0 | [Azure IoT Hub](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-dev-guide-sas?tabs=node) and [Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-sas) Shared Access Signatures |

## How it works

Set the `credentials` field in your connection configuration:

{%raw%}
```json
{
  "connectionType": "http-push",
  "uri": "https://...:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "<algorithm>",
    "parameters": {
      "...": "..."
    }
  }
}
```
{%endraw%}

## Configuration

### aws4-hmac-sha256

For AWS services using [Version 4 request signing](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html):

| Parameter | Description | Default |
|-----------|-------------|---------|
| `region` | AWS region | -- |
| `service` | AWS service name | -- |
| `accessKey` | IAM access key | -- |
| `secretKey` | IAM secret key | -- |
| `doubleEncode` | Double-encode path segments (`false` for S3, `true` for others) | `true` |
| `canonicalHeaders` | Header names to include in the signature | `["host"]` |
| `xAmzContentSha256` | S3 payload hash header: `EXCLUDED`, `INCLUDED`, or `UNSIGNED` | `EXCLUDED` |

### az-monitor-2016-04-01

For [Azure Monitor Data Collector](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api#authorization):

| Parameter | Description |
|-----------|-------------|
| `workspaceId` | Azure Monitor workspace ID |
| `sharedKey` | Primary or secondary workspace key |

### az-sasl

For Azure IoT Hub and Azure Service Bus [Shared Access Signatures](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-dev-guide-sas?tabs=node):

| Parameter | Description |
|-----------|-------------|
| `sharedKeyName` | Name of the shared access policy |
| `sharedKey` | Primary or secondary key (Base64-encode for Service Bus) |
| `endpoint` | Resource URI (for example, `myHub.azure-devices.net` or `https://myNs.servicebus.windows.net/myQueue`) |
| `ttl` (optional) | Signature lifetime (for example, `10m`). For AMQP connections, the broker closes the connection after TTL and Ditto reconnects. Default: 7 days. |

## Examples

### AWS SNS

Publish twin events to an [AWS SNS](https://aws.amazon.com/sns/) topic:

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "AWS SNS",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://sns.<aws-region>.amazonaws.com:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "aws4-hmac-sha256",
    "parameters": {
      "region": "<aws-region>",
      "service": "sns",
      "accessKey": "<aws-access-key>",
      "secretKey": "<aws-secret-key>"
    }
  },
  "sources": [],
  "targets": [{
    "address": "GET:/",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["integration:ditto"],
    "payloadMapping": ["javascript"]
  }],
  "specificConfig": { "parallelism": "1" },
  "mappingDefinitions": {
    "javascript": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "function mapToDittoProtocolMsg() { return undefined; }",
        "outgoingScript": "function mapFromDittoProtocolMsg(namespace,name,group,channel,criterion,action,path,dittoHeaders,value,status,extra) {\n  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra));\n  let query = 'Action=Publish&Message=' + encodeURIComponent(textPayload) + '&Subject=ThingModified&TopicArn=<sns-topic-arn>';\n  return Ditto.buildExternalMsg({\"http.query\":query},'',null,'text/plain');\n}"
      }
    }
  }
}
```
{%endraw%}

The outgoing mapper builds SNS query parameters and sets them via the `http.query` special header:

```javascript
function mapFromDittoProtocolMsg(namespace, name, group, channel, criterion,
                                 action, path, dittoHeaders, value, status, extra) {
  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name,
      group, channel, criterion, action, path, dittoHeaders, value, status, extra));
  let query = 'Action=Publish&Message=' + encodeURIComponent(textPayload) +
      '&Subject=ThingModified&TopicArn=<sns-topic-arn>';
  return Ditto.buildExternalMsg({ "http.query": query }, '', null, 'text/plain');
}
```

### AWS S3

Publish twin events as objects in an [AWS S3](https://aws.amazon.com/s3/) bucket. Note the S3-specific
parameters `doubleEncode: false` and `xAmzContentSha256: "INCLUDED"`:

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "AWS S3",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<s3-bucket>.s3.<aws-region>.amazonaws.com:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "aws4-hmac-sha256",
    "parameters": {
      "region": "<aws-region>",
      "service": "s3",
      "accessKey": "<aws-access-key>",
      "secretKey": "<aws-secret-key>",
      "doubleEncode": false,
      "canonicalHeaders": ["host", "x-amz-date"],
      "xAmzContentSha256": "INCLUDED"
    }
  },
  "sources": [],
  "targets": [{
    "address": "PUT:/",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["integration:ditto"],
    "payloadMapping": ["javascript"]
  }],
  "specificConfig": { "parallelism": "1" },
  "mappingDefinitions": {
    "javascript": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "function mapToDittoProtocolMsg() { return undefined; }",
        "outgoingScript": "function mapFromDittoProtocolMsgWrapper(msg) {\n  let topic = msg['topic'].split('/').join(':');\n  let headers = { 'http.path': topic+':'+msg['revision'] };\n  return Ditto.buildExternalMsg(headers, JSON.stringify(msg), null, 'application/json');\n}"
      }
    }
  }
}
```
{%endraw%}

The payload mapper creates a distinct S3 object per event by computing the path via the
[special header](connectivity-protocol-bindings-http.html#target-header-mapping) `http.path`.
It overrides `mapFromDittoProtocolMsgWrapper` instead of `mapFromDittoProtocolMsg` to access the revision number.
The object name consists of the event topic with `/` replaced by `:` followed by its revision (for example,
`<namespace>:<name>:things:twin:events:modified:42`):

```js
function mapFromDittoProtocolMsgWrapper(msg) {
  let topic = msg['topic'].split('/').join(':');
  let headers = {
      'http.path': topic + ':' + msg['revision']
  };
  let textPayload = JSON.stringify(msg);
  let bytePayload = null;
  let contentType = 'application/json';

  return Ditto.buildExternalMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
  );
}
```

### Azure Monitor Data Collector

Push twin events to [Azure Monitor](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api):

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure Monitor",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<workspace-id>.ods.opinsights.azure.com:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "az-monitor-2016-04-01",
    "parameters": {
      "workspaceId": "<workspace-id>",
      "sharedKey": "<shared-key>"
    }
  },
  "sources": [],
  "targets": [{
    "address": "POST:/api/logs?api-version=2016-04-01",
    "topics": ["_/_/things/twin/events"],
    "authorizationContext": ["integration:ditto"],
    "headerMapping": {
      "Content-Type": "application/json",
      "Log-Type": "TwinEvent"
    }
  }],
  "specificConfig": { "parallelism": "1" }
}
```
{%endraw%}

### Azure IoT Hub (HTTP)

Forward live messages as direct method calls to
[Azure IoT Hub](https://docs.microsoft.com/en-us/azure/iot-hub/about-iot-hub):

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure IoT Hub HTTP",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<hostname>:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<policy-name>",
      "sharedKey": "<shared-key>",
      "endpoint": "<hostname>"
    }
  },
  "sources": [],
  "targets": [{
    "address": "POST:/twins/{{ thing:id }}/methods?api-version=2018-06-30",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "issuedAcknowledgementLabel": "live-response",
    "payloadMapping": ["javascript"]
  }],
  "specificConfig": { "parallelism": "1" },
  "mappingDefinitions": {
    "javascript": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "function mapToDittoProtocolMsg() { return undefined; }",
        "outgoingScript": "function mapFromDittoProtocolMsg(namespace,name,group,channel,criterion,action,path,dittoHeaders,value,status,extra) {\n  let payload = { \"methodName\": action, \"responseTimeoutInSeconds\": parseInt(dittoHeaders.timeout), \"payload\": value };\n  return Ditto.buildExternalMsg(dittoHeaders, JSON.stringify(payload), null, 'application/json');\n}"
      }
    }
  }
}
```
{%endraw%}

The outgoing JavaScript mapper transforms the Ditto Protocol message into the
[direct method format](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-direct-methods#invoke-a-direct-method-from-a-back-end-app),
using the live message subject as `methodName`, the timeout as `responseTimeoutInSeconds`, and the value as `payload`:

```javascript
function mapFromDittoProtocolMsg(
        namespace,
        name,
        group,
        channel,
        criterion,
        action,
        path,
        dittoHeaders,
        value,
        status,
        extra
) {

  let headers = dittoHeaders;
  let payload = {
    "methodName": action,
    "responseTimeoutInSeconds": parseInt(dittoHeaders.timeout),
    "payload": value
  };
  let textPayload = JSON.stringify(payload);
  let bytePayload = null;
  let contentType = 'application/json';

  return Ditto.buildExternalMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
  );
}
```

Azure IoT Hub direct methods require this JSON format:

```json
{
    "methodName": "reboot",
    "responseTimeoutInSeconds": 200,
    "payload": {
        "input1": "someInput",
        "input2": "anotherInput"
    }
}
```

Send live messages to a Thing containing the expected `payload` as value, the expected `methodName` as live message
subject, and the expected `responseTimeoutInSeconds` as timeout. For example, invoke the above direct method with a
`POST` to `<ditto>/api/2/things/<thing-id>/inbox/messages/reboot?timeout=200s` with body:

```json
{
    "input1": "someInput",
    "input2": "anotherInput"
}
```

### Azure IoT Hub (AMQP)

Forward live messages as Cloud-to-Device messages via AMQP 1.0:

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure IoT Hub AMQP",
  "connectionType": "amqp-10",
  "connectionStatus": "open",
  "uri": "amqps://<hostname>:5671",
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<policy-name>",
      "sharedKey": "<shared-key>",
      "endpoint": "<hostname>"
    }
  },
  "sources": [],
  "targets": [{
    "address": "/messages/devicebound",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "headerMapping": {
      "iothub-ack": "full",
      "to": "/devices/{{thing:id}}/messages/devicebound"
    }
  }]
}
```
{%endraw%}

### Azure Service Bus (HTTP)

Forward live messages to an [Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview) queue:

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure Service Bus HTTP",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<hostname>:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<policy-name>",
      "sharedKey": "<base64-encoded-key>",
      "endpoint": "https://<hostname>/<queue-name>"
    }
  },
  "sources": [],
  "targets": [{
    "address": "POST:/<queue-name>/messages",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "issuedAcknowledgementLabel": "live-response"
  }],
  "specificConfig": { "parallelism": "1" }
}
```
{%endraw%}

For Azure Service Bus, the `sharedKey` must be additionally Base64-encoded (for example, `theKey`
becomes `dGhlS2V5`).

## Custom algorithms

You can add custom signing algorithms by implementing the appropriate interface and registering it
in [connectivity.conf](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf):

| Connection type | Config path | Interface |
|----------------|-------------|-----------|
| HTTP Push | `ditto.connectivity.connection.http-push.hmac-algorithms` | [HttpRequestSigningFactory](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/httppush/HttpRequestSigningFactory.java) |
| AMQP 1.0 | `ditto.connectivity.connection.amqp10.hmac-algorithms` | [AmqpConnectionSigningFactory](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/amqp/AmqpConnectionSigningFactory.java) |

The default configuration registers the pre-defined algorithms as follows:

```hocon
ditto.connectivity.connection {
  http-push.hmac-algorithms = {

    aws4-hmac-sha256 =
      "org.eclipse.ditto.connectivity.service.messaging.httppush.AwsRequestSigningFactory"

    az-monitor-2016-04-01 =
      "org.eclipse.ditto.connectivity.service.messaging.httppush.AzMonitorRequestSigningFactory"

    az-sasl =
      "org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigningFactory"

    // my-own-request-signing-algorithm =
    //   "my.package.MyOwnImplementationOfHttpRequestSigningFactory"
  }
  amqp10.hmac-algorithms = {

    az-sasl =
      "org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigningFactory"

    // my-own-connection-signing-algorithm =
    //   "my.package.MyOwnImplementationOfAmqpConnectionSigningFactory"
  }
}
```

## Further reading

* [HTTP 1.1 binding](connectivity-protocol-bindings-http.html) -- HTTP push connection details
* [AMQP 1.0 binding](connectivity-protocol-bindings-amqp10.html) -- AMQP 1.0 connection details
* [Connections overview](basic-connections.html) -- connection model and configuration
