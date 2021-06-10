---
title: HMAC signing
keywords: hmac, hmac-sha256, authorization, azure, aws, azure iot hub, azure service bus, azure monitor, aws version 4 signing, aws sns, signing
tags: [connectivity]
permalink: connectivity-hmac-signing.html
---

## HMAC signing

Ditto provides an extensible framework for HMAC-based signing authentication processes for HTTP Push and
AMQP 1.0 connections. Three algorithms are available out of the box:

- `aws4-hmac-sha256` (HTTP Push only): [Version 4 request signing](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html) for Amazon Web Services (AWS)
- `az-monitor-2016-04-01` (HTTP Push only): [Version 2016-04-01 request signing](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api#authorization) for Azure Monitor Data Collector
- `az-sasl` (HTTP Push and AMQP 1.0): [Shared Access Signatures](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-dev-guide-sas?tabs=node) for Azure IoT Hub
- `az-sasl` (HTTP Push only): [Shared Access Signatures](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-sas) for Azure Service Bus


To use a request signing algorithm for authentication, set the `credentials` field of the connection as follows.
{%raw%}
```json
{
  "connectionType": "http-push",
  "uri": "https://...:443",
  "credentials": {
    "type": "hmac",
    "algorithm": "<algorithm>", // e.g.: "az-monitor-2016-04-01"
    "parameters": {
       // parameters of the algorithm named above.
       ...
    }
  },
  ...
}
```
{%endraw%}


### Pre-defined algorithms

#### aws4-hmac-sha256

This algorithm works for AWS SNS and other services using [Version 4 request signing](https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html).

The parameters of the algorithm `aws4-hmac-sha256` are:
- `region`: Region of the AWS endpoint.
- `service`: Service name of the AWS endpoint.
- `accessKey`: Access key of the signing user.
- `secretKey`: Secret key of the signing user.
- `doubleEncode`: Whether to double-encode and normalize path segments during request signing. Should be `false` for S3 and `true` for other services. Defaults to `true`.
- `canonicalHeaders`: Array of names of headers to include in the signature. Default to `["host"]`.
- `xAmzContentSha256`: Configuration for the header `x-amz-content-sha256`, which is mandatory for S3. Possible values are:
    - `EXCLUDED`: Do not send the header for non-S3 services. This is the default.
    - `INCLUDED`: Sign the payload hash as the value of the header for S3.
    - `UNSIGNED`: Omit the payload hash in the signature for S3.

#### az-monitor-2016-04-01

This algorithm works for [Version 2016-04-01 request signing](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api#authorization)
for Azure Monitor Data Collector.

The parameters of the algorithm `az-monitor-2016-04-01` are:
- `workspaceId`: ID of the Azure Monitor workspace.
- `sharedKey`: Primary or secondary key of the Azure Monitor workspace.

#### az-sasl

This algorithm works for Azure IoT Hub (HTTP and AMQP) [Shared Access Signatures](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-dev-guide-sas?tabs=node)
and Azure Service Bus (HTTP) [Shared Access Signatures](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-sas).

The parameters of the algorithm `az-sasl` are:
- `sharedKeyName`: Name of the used `sharedKey`.
- `sharedKey`: Primary or secondary key of `sharedKeyName`. The key for Azure Service Bus needs an additional
  `Base64` encoding to work (e.g. a primary key `theKey` should be encoded to `dGhlS2V5` and used in this format).
- `endpoint`: The endpoint which is used in the signature. For Azure IoT Hub this is expected
  to be the `resourceUri` without protocol (e.g. `myHub.azure-devices.net`, see the respective Azure documentation).
  For Azure Service Bus, this is expected to be the full URI of the resource to which access
  is claimed (e.g. `http://myNamespaces.servicebus.windows.net/myQueue`, see the respective Azue documentation)
- `ttl` (optional): The time to live of a signature as a string in duration format. Allowed time units are "ms" (milliseconds),
  "s" (seconds) and "m" (minutes), e.g. "10m" for ten minutes.
  `ttl` should only be set for AMQP connections and defines how long the connection signing is valid.
  The broker (e.g. Azure IoT Hub) will close the connection after `ttl` and Ditto will reconnect with a new signature.
  Defaults to 7 days.
  

### Supported connection types

|                          | HTTP Push connection | AMQP 1.0 connection |
| ------------------------ | -------------------- | ------------------- |
| `aws-hmac-sha256`        | ✓                    |                     |
| `az-monitor-2016-04-01`  | ✓                    |                     |
| `az-sasl`                | ✓                    | ✓ (for Azure IoT Hub) |

### Configuration

Algorithm names and implementations are configured in [`connectivity.conf`](https://github.com/eclipse/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf).
The default configuration provides the names and implementations of the available pre-defined algorithms for the given
connection types.
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

Users may add own request signing algorithms by implementing a defined interface and providing the fully
qualified class name of the implementation in the config. The following table provides information where to update the configuration
and which interface needs to be implemented.

|                          | HTTP Push connection | AMQP 1.0 connection |
| ------------------------ | -------------------- | ------------------- |
| Config path       | `ditto.connectivity.connection.http-push.hmac-algorithms`   | `ditto.connectivity.connection.amqp10.hmac-algorithms` |
| Class to implement  | [HttpRequestSigningFactory](https://github.com/eclipse/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/httppush/HttpRequestSigningFactory.java)  | [AmqpConnectionSigningFactory](https://github.com/eclipse/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/amqp/AmqpConnectionSigningFactory.java) |


### Example integrations

#### AWS SNS

#### AWS S3

#### Azure Monitor Data Collector HTTP API

#### Azure IoT Hub HTTP API

What follows is an example of a connections which forwards live messages sent to things as direct method calls to
Azure IoT Hub via HTTP. You'll receive the response of the direct method call.
The placeholders in `<angle brackets>` need to be replaced.

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure IoT Hub HTTP",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<hostname>:443",
  "sources": [],
  "targets": [{
    "address": "POST:/twins/{{ thing:id }}/methods?api-version=2018-06-30",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "issuedAcknowledgementLabel": "live-response",
    "headerMapping": {},
    "payloadMapping": ["javascript"]
  }
  ],
  "clientCount": 1,
  "failoverEnabled": true,
  "validateCertificates": true,
  "processorPoolSize": 5,
  "specificConfig": {
    "parallelism": "1"
  },
  "mappingDefinitions": {
    "javascript": {
      "mappingEngine": "JavaScript",
      "options": {
        "incomingScript": "function mapToDittoProtocolMsg(\n  headers,\n  textPayload,\n  bytePayload,\n  contentType\n) {\n\n  if (contentType === 'application/vnd.eclipse.ditto+json') {\n    return JSON.parse(textPayload);\n  } else if (contentType === 'application/octet-stream') {\n    try {\n      return JSON.parse(Ditto.arrayBufferToString(bytePayload));\n    } catch (e) {\n      return null;\n    }\n  }\n  return null;\n}\n",
        "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n\n  let headers = dittoHeaders;\n  let textPayload = JSON.stringify(value);\n  let bytePayload = null;\n  let contentType = 'application/json';\n\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}\n",
        "loadBytebufferJS": "false",
        "loadLongJS": "false"
      }
    }
  },
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<shared-access-policy-name>",
      "sharedKey": "<shared-access-key>",
      "endpoint": "<hostname>"
    }
  },
  "tags": []
}
```
{%endraw%}


Required parameters:
* `<hostname>`: The hostname of your iot hub instance. E.g. `my-hub.azure-devices.net`.
* `<shared-access-policy-name>`: The name of the (Azure IoT Hub) shared access policy, which has the `Service connect` permission. E.g. `service`.
* `<shared-access-key>`: The primary or secondary key of above policy. E.g. `theKey`.

The `outgoingScript` provides JavaScript payload mapping which maps the outgoing ditto protocol message into a plain
`application/json` message, which contains the value of the original ditto protocol message:
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
  let textPayload = JSON.stringify(value);
  let bytePayload = null;
  let contentType = 'application/json';

  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
    textPayload, // The external mapped String
    bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
  );
}
```

This is required since Azure IoT Hub direct methods require a [specific format](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-direct-methods#invoke-a-direct-method-from-a-back-end-app):
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

Send live messages to things in this format and with the above shown payload mapping, they can successfully invoke
the direct method on their respective devices.

#### Azure IoT Hub AMQP API

What follows is an example of a connections which forwards live messages sent to things as Cloud To Device (C2D)
messages to Azure IoT Hub via AMQP 1.0.
The placeholders in `<angle brackets>` need to be replaced.

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure IoT Hub AMQP",
  "connectionType": "amqp-10",
  "connectionStatus": "open",
  "uri": "amqps://<hostname>:5671",
  "sources": [],
  "targets": [{
    "address": "/messages/devicebound",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "headerMapping": {
      "iothub-ack": "full",
      "to": "/devices/{{thing:id}}/messages/devicebound"
    }
  }
  ],
  "clientCount": 1,
  "failoverEnabled": true,
  "validateCertificates": true,
  "processorPoolSize": 5,
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<shared-access-policy-name>",
      "sharedKey": "<shared-access-key>",
      "endpoint": "<hostname>"
    }
  },
  "tags": []
}
```
{%endraw%}


Required parameters:
* `<hostname>`: The hostname of your iot hub instance. E.g. `my-hub.azure-devices.net`.
* `<shared-access-policy-name>`: The name of the (Azure IoT Hub) shared access policy, which has the `Service connect` permission. E.g. `service`.
* `shared-access-key>`: The primary or secondary key of above policy. E.g. `theKey`.


#### Azure Service Bus HTTP API

What follows is an example of a connection which forwards live messages sent to things to a queue in Azure Service Bus.
The placeholders in `<angle brackets>` need to be replaced. 

{%raw%}
```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "name": "Azure Service Bus HTTP",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://<hostname>:443",
  "sources": [],
  "targets": [{
    "address": "POST:/<queue-name>/messages",
    "topics": ["_/_/things/live/messages"],
    "authorizationContext": ["integration:ditto"],
    "issuedAcknowledgementLabel": "live-response",
    "headerMapping": {}
  }
  ],
  "clientCount": 1,
  "failoverEnabled": true,
  "validateCertificates": true,
  "processorPoolSize": 5,
  "specificConfig": {
    "parallelism": "1"
  },
  "credentials": {
    "type": "hmac",
    "algorithm": "az-sasl",
    "parameters": {
      "sharedKeyName": "<shared-access-policy-name>",
      "sharedKey": "<base64-encoded-shared-key>",
      "endpoint": "https://<hostname>/<queue-name>"
    }
  },
  "tags": []
}
```
{%endraw%}

Required parameters:
* `<hostname>`: The hostname of your service bus instance. E.g. `my-bus.servicebus.windows.net`.
* `<queue-name>`: The queue name to which you want to publish in your service bus instance. E.g. `my-queue`'.
* `<shared-access-policy-name>`: The name of the (Azure Service Bus) shared access policy, which has `Send` and `Listen` permissions. E.g. `RootManageSharedAccessKey`.
* `<base64-encoded-shared-key>`: The `Base64` encoded primary or secondary key of above policy. The signing work if you
  encode the key with `Base64` (although it already has `Base64` encoding)`. E.g. if the primary key is `theKey`, you need to use
  its encoded version `dGhlS2V5`
