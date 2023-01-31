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
  is claimed (e.g. `https://myNamespaces.servicebus.windows.net/myQueue`, see the respective Azure documentation)
- `ttl` (optional): The time to live of a signature as a string in duration format. Allowed time units are "ms" (milliseconds),
  "s" (seconds), "m" (minutes) and "h" (hours), e.g. "10m" for ten minutes.
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

Algorithm names and implementations are configured in [`connectivity.conf`](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/resources/connectivity.conf).
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
| Class to implement  | [HttpRequestSigningFactory](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/httppush/HttpRequestSigningFactory.java)  | [AmqpConnectionSigningFactory](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/messaging/amqp/AmqpConnectionSigningFactory.java) |


### Example integrations

#### AWS SNS

This example is an HTTP connection to [AWS SNS (Simple Notification Service)](https://aws.amazon.com/sns/) publishing
twin events as messages to an SNS topic. Prerequisites are:
- An IAM user with access to SNS,
- An SNS topic,
- A subscription on the SNS topic to receive twin events, e.g. by email.

The SNS connection is shown below.

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
      "topics": [ "_/_/things/twin/events" ],
      "authorizationContext": [ "integration:ditto" ],
      "headerMapping": {},
      "payloadMapping": [ "javascript" ]
  }],
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
        "incomingScript": "function mapToDittoProtocolMsg() {\n    return undefined;\n}",
        "outgoingScript": "function mapFromDittoProtocolMsg(namespace,name,group,channel,criterion,action,path,dittoHeaders,value,status,extra) {\n  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra));\n  let query = 'Action=Publish&Message=' + encodeURIComponent(textPayload) + '&Subject=ThingModified&TopicArn=<sns-topic-arn>';\n  let headers = {\"http.query\":query};\n  return Ditto.buildExternalMsg(headers,'',null,'text/plain');\n}\n",
        "loadBytebufferJS": "false",
        "loadLongJS": "false"
      }
    }
  },
  "tags": []
}
```
{%endraw%}

Parameters:
- `<aws-region>`: The region of the SNS service.
- `<aws-access-key>` The access key of the user authorized for SNS.
- `<aws-secret-key>` The secret key of the user authorized for SNS.
- `<sns-topic-arn>` The ARN of the SNS topic. Note that it is a part of the URI and every `:` needs to be encoded as
`%3A`.

Here is the outgoing payload mapping in a readable format.
Since the HTTP API of SNS requires GET requests with all necessary information in
the query parameters, the payload mapper computes the query string and sets it via
the [special header](connectivity-protocol-bindings-http.html#target-header-mapping) `http.query`.
It is important to set payload to `null` so that the HMAC
signature is computed from the SHA256 hash of the empty string, which SNS expects.
```js
function mapFromDittoProtocolMsg(namespace,name,group,channel,criterion,
                                 action,path,dittoHeaders,value,status,extra) {
  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name,
      group, channel, criterion, action, path, dittoHeaders, value, status, extra));
  let query = 'Action=Publish&Message=' + encodeURIComponent(textPayload) +
      '&Subject=ThingModified&TopicArn=<sns-topic-arn>';
  let headers = { "http.query": query };
  return Ditto.buildExternalMsg(headers, '', null, 'text/plain');
}
```

#### AWS S3

This example is an HTTP connection to [AWS S3 (Simple Storage Service)](https://aws.amazon.com/s3/) publishing
twin events as objects in an S3 bucket. Prerequisites are:
- An IAM user with access to S3,
- An S3 bucket.

The S3 connection is shown below. The credentials parameter `"xAmzContentSha256": "INCLUDED"` is necessary in order
to include the payload hash as the value of the header `x-amz-content-sha256`. In addition,
the parameter `"doubleEncode"` must be set to `false` because S3 performs URI-encoding only once when computing
the signature, in contrast to other AWS services where the path segments of HTTP requests are encoded twice.

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
      "canonicalHeaders": [ "host", "x-amz-date" ],
      "xAmzContentSha256": "INCLUDED"
    }
  },
  "sources": [],
  "targets": [{
      "address": "PUT:/",
      "topics": [ "_/_/things/twin/events" ],
      "authorizationContext": [ "integration:ditto" ],
      "headerMapping": {},
      "payloadMapping": [
        "javascript"
      ]
  }],
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
        "incomingScript": "function mapToDittoProtocolMsg() {\n  return undefined;\n}\n",
        "outgoingScript": "function mapFromDittoProtocolMsgWrapper(msg) {\n  let topic = msg['topic'].split('/').join(':');\n  let headers = {\n      'http.path': topic+':'+msg['revision']\n  };\n  let textPayload = JSON.stringify(msg);\n  let bytePayload = null;\n  let contentType = 'application/json';\n\n  return Ditto.buildExternalMsg(\n    headers,\n    textPayload,\n    bytePayload,\n    contentType\n  );\n}\n",
        "loadBytebufferJS": "false",
        "loadLongJS": "false"
      }
    }
  },
  "tags": []
}
```
{%endraw%}

Parameters:
- `<s3-bucket>`: Name of the S3 bucket.
- `<aws-region>`: The region of the S3 service.
- `<aws-access-key>` The access key of the user authorized for S3.
- `<aws-secret-key>` The secret key of the user authorized for S3.

In order to create a distinct object for each event,
the payload mapper that computes the path string and sets it via
the [special header](connectivity-protocol-bindings-http.html#target-header-mapping) `http.path`.
The mapper overrides the function `mapFromDittoProtocolMsgWrapper` instead of the usual
`mapFromDittoProtocolMsg` so that it has access to the revision number of twin events.
The value of `http.path` is the name of the object, which consists of the topic of the event with `/` replaced by `:`
followed by its revision. For example, a thing-modified event of revision 42 generates the object
`<namespace>:<name>:things:twin:events:modified:42`.

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

#### Azure Monitor Data Collector HTTP API

This example is an HTTP connection pushing twin events into
[Azure Monitor Data Collector API](https://docs.microsoft.com/en-us/azure/azure-monitor/logs/data-collector-api).
It requires an Azure Log Analytics Workspace.

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
      "topics": [ "_/_/things/twin/events" ],
      "authorizationContext": [ "integration:ditto" ],
      "headerMapping": {
        "Content-Type": "application/json",
        "Log-Type": "TwinEvent"
      }
  }],
  "clientCount": 1,
  "failoverEnabled": true,
  "validateCertificates": true,
  "processorPoolSize": 5,
  "specificConfig": {
    "parallelism": "1"
  },
  "tags": []
}
```
{%endraw%}

Parameters:
- `<workspace-id>`: The ID of the log analytics workspace.
- `<shared-key>`: The primary or secondary shared key of the log analytics workspace.

The connection publishes all fields of all twin events under the log type `TwinEvent`. After a maximum of 30 minutes
after the first event, Azure Monitor should have created a custom log type `TwinEvent_CL` containing a twin event
in each row.

#### Azure IoT Hub HTTP API

What follows is an example of a connection which forwards live messages sent to things as direct method calls to
[Azure IoT Hub](https://docs.microsoft.com/en-us/azure/iot-hub/about-iot-hub) via HTTP. You'll receive the response of the direct method call.

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
  }],
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
        "incomingScript": "function mapToDittoProtocolMsg() {\n  return undefined;\n}\n",
        "outgoingScript": "function mapFromDittoProtocolMsg(\n  namespace,\n  name,\n  group,\n  channel,\n  criterion,\n  action,\n  path,\n  dittoHeaders,\n  value,\n  status,\n  extra\n) {\n\n  let headers = dittoHeaders;\n  let payload = {\n      \"methodName\": action,\n      \"responseTimeoutInSeconds\": parseInt(dittoHeaders.timeout),\n      \"payload\": value\n  };\n  let textPayload = JSON.stringify(payload);\n  let bytePayload = null;\n  let contentType = 'application/json';\n\n  return Ditto.buildExternalMsg(\n    headers, // The external headers Object containing header values\n    textPayload, // The external mapped String\n    bytePayload, // The external mapped byte[]\n    contentType // The returned Content-Type\n  );\n}\n",
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

Parameters:
* `<hostname>`: The hostname of your iot hub instance. E.g. `my-hub.azure-devices.net`.
* `<shared-access-policy-name>`: The name of the (Azure IoT Hub) shared access policy, which has the `Service connect` permission. E.g. `service`.
* `<shared-access-key>`: The primary or secondary key of above policy. E.g. `theKey`.

The `outgoingScript` provides JavaScript payload mapping which maps the outgoing ditto protocol message into
the required [direct method format](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-direct-methods#invoke-a-direct-method-from-a-back-end-app).
I.e. it uses the live message subject as `methodName`, the timeout as `responseTimeoutInSeconds` and the value as `payload`:

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

This is required since Azure IoT Hub direct methods require a specific format:
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

Send live messages to a Thing, which contain as value the expected `payload`, as live message subject the expected
`methodName` and as timeout the expected `responseTimeoutInSeconds`. Then they can successfully invoke
the direct method on their respective devices.

E.g. a live message for the above direct method would be a `POST` to
`<ditto>/api/2/things/<thing-id>/inbox/messages/reboot?timeout=200s` with body:
```json
{
    "input1": "someInput",
    "input2": "anotherInput"
}
```

#### Azure IoT Hub AMQP API

What follows is an example of a connections which forwards live messages sent to things as Cloud To Device (C2D)
messages to [Azure IoT Hub](https://docs.microsoft.com/en-us/azure/iot-hub/about-iot-hub) via AMQP 1.0.

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

Parameters:
* `<hostname>`: The hostname of your iot hub instance. E.g. `my-hub.azure-devices.net`.
* `<shared-access-policy-name>`: The name of the (Azure IoT Hub) shared access policy, which has the `Service connect` permission. E.g. `service`.
* `<shared-access-key>`: The primary or secondary key of above policy. E.g. `theKey`.


#### Azure Service Bus HTTP API

What follows is an example of a connection which forwards live messages sent to things to a queue in
[Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-messaging-overview).

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

Parameters:
* `<hostname>`: The hostname of your service bus instance. E.g. `my-bus.servicebus.windows.net`.
* `<queue-name>`: The queue name to which you want to publish in your service bus instance. E.g. `my-queue`'.
* `<shared-access-policy-name>`: The name of the (Azure Service Bus) shared access policy, which has `Send` and `Listen` permissions. E.g. `RootManageSharedAccessKey`.
* `<base64-encoded-shared-key>`: The `Base64` encoded primary or secondary key of above policy. The signing work if you
  encode the key with `Base64` (although it already has `Base64` encoding). E.g. if the primary key is `theKey`, you need to use
  its encoded version `dGhlS2V5`.
