---
title: HMAC request signing
keywords: hmac, hmac-sha256, authorization, azure, aws, azure iot hub, azure service bus, azure monitor, aws version 4 signing, aws sns
tags: [connectivity]
permalink: connectivity-hmac-signing.html
---

## HMAC request signing

Ditto provides an extensible framework for HMAC-based request signing authentication processes for HTTP Push and
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
- `sharedKey`: Primary or secondary key of `sharedKeyName`. The key for Azure Service Bus will need an additional
  `Base64` encoding to work (e.g. the primary key `theKey` should be encoded to `dGhlS2V5` and used in this format).
- `endpoint`: The endpoint which is used in the signature. For Azure IoT Hub this is expected
  to be the `resourceUri` without protocol (e.g. `myHub.azure-devices.net`, see the respective Azure documentation).
  For Azure Service Bus, this is expected to be the full URI of the resource to which access
  is claimed (e.g. `http://myNamespaces.servicebus.windows.net/myQueue`, see the respective Azue documentation)
- `ttl` (optional): The time to live of a signature. Should only be used for AMQP connections and defines how long
  the connection signing is valid. The broker (e.g. Azure IoT Hub) will close the connection after `ttl`, Ditto
  will calculate a new signature and connect again.
  

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
    //   "my.package.MyOwnImplementationOfRequestSigningFactory"
  }
  amqp10.hmac-algorithms = {

    az-sasl =
      "org.eclipse.ditto.connectivity.service.messaging.signing.AzSaslSigningFactory"

    // my-own-request-signing-algorithm =
    //   "my.package.MyOwnImplementationOfRequestSigningFactory"
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
