---
title: "Support for HMAC-SHA256 signing for connections"
published: true
permalink: 2021-06-17-hmac-credentials.html
layout: post
author: florian_fendt
tags: [blog, architecture, connectivity]
hide_sidebar: true
sidebar: false
toc: true
---

With the upcoming release of Eclipse Ditto **version 2.1.0** it will be possible to use HMAC-SHA256 signing for
connections. The currently implemented algorithms support you in authenticating requests against:
* Azure IoT Hub REST API
* Azure IoT Hub AMQP 1.0
* Azure HTTP Monitor Data Collector API
* Azure Service Bus REST API
* Amazon Simple Notification Service (Amazon SNS) 
* other AWS services supporting Signature Version 4 signing (see external AWS documentation on 
  [Signing AWS API requests](https://docs.aws.amazon.com/general/latest/gr/signing_aws_api_requests.html))
  
Detailed information can be found at [Connectivity API > HMAC request signing](connectivity-hmac-signing.html).

This blog post shows different configurations with the `az-sasl` algorithm, that allow you to sign requests
against Azure IoT Hub (HTTP Push and AMQP 1.0) as well as Azure Service Bus (HTTP Push).

# Prerequisites

For the examples you'll need a running instance of Eclipse Ditto (see [Running Ditto](installation-running.html)).
Additionally, a simple Thing is required, to which messages can be sent to.
Create the Thing `ditto:thing` with a `PUT` request to `<ditto>/api/2/things/ditto:thing` and content
{%raw%}
```json
 {
  "thingId": "ditto:thing",
  "attributes": {},
  "features": {},
  "_policy": {
    "entries": {
      "DEFAULT": {
        "subjects": {
          "{{ request:subjectId }}": {
            "type": "the creator"
          },
          "integration:ditto": {
            "type": "the connections"
          }
        },
        "resources": {
          "policy:/": {
            "grant": [
              "READ",
              "WRITE"
            ],
            "revoke": []
          },
          "thing:/": {
            "grant": [
              "READ",
              "WRITE"
            ],
            "revoke": []
          },
          "message:/": {
            "grant": [
              "READ",
              "WRITE"
            ],
            "revoke": []
          }
        }
      }
    }
  }
}
```
{%endraw%}

# Azure IoT Hub REST API

This example shows how to invoke a [direct method](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-direct-methods)
on a device using Eclipse Ditto and Azure IoT Hub.

For creating signed requests against the Azure IoT Hub REST API, you'll need the following information from your
Azure IoT Hub instance:
* The hostname (e.g. `my-hub.azure-devices.net`).
* The name of a (Azure IoT Hub) shared access policy, which has the `Service connect` permission. By default, there
  should be a policy named `service` which provides this permission.
* The primary or secondary key of above policy.  
* A device in Azure IoT Hub with the ID `ditto:thing` (for the example this needs to be the same ID as the Thing created in prerequisites).

What follows is a sample connection JSON for a connection named `Azure IoT Hub HTTP`, using hostname `my-hub.azure-devices.net` and
shared access policy `service` with key `theKey`. You can set the correct values from your Azure IoT Hub subscription
in the fields `uri`, `credentials.parameters.sharedKeyName`, `credentials.parameters.sharedKey` and
`credentials.parameters.endpoint`.

{%raw%}
```json
{
		"id": "60d193e3-2639-415b-af29-0e337741141d",
		"name": "Azure IoT Hub HTTP",
		"connectionType": "http-push",
		"connectionStatus": "open",
		"uri": "https://my-hub.azure-devices.net:443",
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
				"sharedKeyName": "service",
				"sharedKey": "theKey",
				"endpoint": "my-hub.azure-devices.net"
			}
		},
		"tags": []
	}
```
{%endraw%}

This connection configuration sends live messages to the endpoint at
{%raw%}`https://my-hub.azure-devices.net:443/twins/{{ thing:id }}/methods?api-version=2018-06-30`{%endraw%}
and signs each request with a [Shared Access Signature](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-dev-guide-sas?tabs=node).
The configuration also contains an outgoing JavaScript payload mapping, which extracts the subject of the live message,
its timeout and its payload and uses this to construct a JSON message in the required direct method format.
You'll see that format below.

## Listen for direct method on the device

Your Azure IoT Hub device needs to listen to the direct method you're calling. Imagine you want to call a
device method `getDeviceLog` on a device. You can use the Nodejs sample [device_methods.js from azure-iot-sdk-node/device/samples](https://github.com/Azure/azure-iot-sdk-node/blob/f526f203ddc9ee32e3c92066312417d2ef6303de/device/samples/device_methods.js)
as a starter. You'll need to set the `DEVICE_CONNECTION_STRING` environment variable to a connection string of the
device you are using and can run the device with `npm install && node device_methods.js`.

## Send a live message to the Thing

To invoke the `getDeviceLog` method on the device, you should now be able to send a live message to the Thing. The message
will be forwarded through your connection `Azure IoT Hub HTTP` to the Azure IoT Hub, which will route it to the device
and respond with its response.

`POST` a message to `<ditto>/api/2/things/ditto:thing/inbox/messages/getDeviceLog?timeout=5s` with content:
```json
{
  "service": "my-microservice",
  "amount": 9000
}
```

The payload mapping of the connection will turn this into a direct method:
```json
{
  "methodName": "getDeviceLog",
  "responseTimeoutInSeconds": 5,
  "payload": {
    "service": "my-microservice",
    "amount": 9000
  }
}
```

If you didn't change the sample code of the device, you should get a response containing `example payload`.


# Azure IoT Hub AMQP

For using the Azure IoT Hub AMQP endpoint, basically the same prerequisites as for the HTTP endpoint apply.
The difference is, that it's not possible to invoke direct methods using the AMQP endpoint. Instead, you can
send a Cloud To Device (C2D) message, on which the device listens.

{% include note.html content="Instead of signing each request like in HTTP push connections, the connection itself is
established with signed connection information. For this, the `ttl` parameter of the `az-sasl` algorithm applies 
(see [documentation of the az-sasl algorithm](connectivity-hmac-signing.html#az-sasl))."
%}

You can use the Nodejs sample [simple_sample_device.js from azure-iot-sdk-node/device/samples](https://github.com/Azure/azure-iot-sdk-node/blob/f526f203ddc9ee32e3c92066312417d2ef6303de/device/samples/simple_sample_device.js)
to listen on C2D messages as a device. You'll need to set the `DEVICE_CONNECTION_STRING` environment variable to a connection string of the
device you are using and can run the device with `npm install && node simple_sample_device.js`.

What follows is a sample connection JSON for a connection named `Azure IoT Hub AMPQ`, using hostname `my-hub.azure-devices.net` and
shared access policy `service` with key `theKey`. You can set the correct values from your Azure IoT Hub subscription
in the fields `uri`, `credentials.parameters.sharedKeyName`, `credentials.parameters.sharedKey` and
`credentials.parameters.endpoint`.
{%raw%}
```json
{
  "id": "8caca8c6-10d1-4886-a61f-3ea6270f9d8e",
  "name": "Azure IoT Hub AMQP",
  "connectionType": "amqp-10",
  "connectionStatus": "open",
  "uri": "amqps://my-hub.azure-devices.net:5671",
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
      "sharedKeyName": "service",
      "sharedKey": "theKey",
      "endpoint": "my-hub.azure-devices.net"
    }
  },
  "tags": []
}
```
{%endraw%}

Instead of applying a payload mapping like in the HTTP example, the connection just sends the ditto protocol live message
and you should see its content on the device.

`POST` a message to `<ditto>/api/2/things/ditto:thing/inbox/messages/C2DMessage?timeout=0` with content
```json
{
  "Hello": "from Ditto"
}
```
and see it arrive at the device.

{% include note.html content="Using the query parameter `timeout=0`, you can tell Ditto to not wait for an answer, since
devices can't respond to C2D messages."
%}


# Azure Service Bus

This example shows how to send a message to an Azure Service Bus via Eclipse Ditto.

For creating signed requests against the Azure Service Bus, you'll need the following information from your
Azure IoT Hub instance:
* A queue (e.g. `my-queue`).
* The hostname (e.g. `my-bus.servicebus.windows.net`).
* The name of a (Azure Service Bus) shared access policy, which has the `Send` and `Listen` permissions. By default, there
  should be a policy named `RootManageSharedAccessKey` which provides this permission (but shouldn't be used in production
  scenarios).
* The `Base64` encoded primary or secondary key of above policy. The signing will only work if you encode the key with
`Base64` (although it already has `Base64` encoding). E.g. if the primary key is `theKey`, you need to use its encoded
  version `dGhlS2V5`.


What follows is a sample connection JSON for a connection named `Azure Service Bus HTTP`, using hostname `my-bus.servicebus.windows.net` and
shared access policy `RootManageSharedAccessKey` with encoded key `dGhlS2V5` (`theKey`). You can set the correct values
from your Azure Service Bus subscription in the fields `uri`, `credentials.parameters.sharedKeyName`,
`credentials.parameters.sharedKey` and
`credentials.parameters.endpoint` (which is a combination of the hostname and queue name).

{%raw%}
```json
{
  "id": "adec2846-4d11-4e0a-b456-d8bfc2192fc6",
  "name": "Azure Service Bus HTTP",
  "connectionType": "http-push",
  "connectionStatus": "open",
  "uri": "https://my-bus.servicebus.windows.net:443",
  "sources": [],
  "targets": [{
    "address": "POST:/my-queue/messages",
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
      "sharedKeyName": "RootManageSharedAccessKey",
      "sharedKey": "dGhlS2V5",
      "endpoint": "https://my-bus.servicebus.windows.net/my-queue"
    }
  },
  "tags": []
}
```
{%endraw%}

This connection configuration sends live messages to the endpoint at `https://my-hub.servicebus.windows.net.net:443/my-queue/messages`
and signs each request with a [Shared Access Signature](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-sas#overview-of-sas).

## Listen for the message

To listen for the message you send into the Service Bus, you can use the Nodejs sample 
[receiveMessagesStreaming.js from azure-sdk-for-js/sdk/servicebus/service-bus/samples/v7/javascript](https://github.com/Azure/azure-sdk-for-js/blob/d7d67ccc865318f3dc6395e0db6997b5af8cf5e8/sdk/servicebus/service-bus/samples/v7/javascript/receiveMessagesStreaming.js).
You'll need to set the `SERVICEBUS_CONNECTION_STRING` environment variable to the connection string of the used
shared access policy. Also, you'll need to set the `QUEUE_NAME` environment variable to the
used queue (e.g. `my-queue`). You should be able to run the sample using `npm install && node receiveMessagesStreaming.js`.

## Send a live message to the Thing

To send a message to Service Bus, you can simply send a live message to a Thing. The message
will be forwarded through your connection `Azure Service Bus HTTP` to the Azure Service Bus, from which the sample
app can read the message.

`POST` a message to `<ditto>/api/2/things/ditto:thing/inbox/messages/HelloServiceBus` with content:
```json
{
  "Hello": "from Ditto"
}
```

If you didn't change the sample code you should see the message arriving there.

## Feedback?

Find details on the different algorithms and their parameters at
[Connectivity API > HMAC request signing](connectivity-hmac-signing.html).

Please [get in touch](feedback.html) if you have feedback or questions regarding this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
