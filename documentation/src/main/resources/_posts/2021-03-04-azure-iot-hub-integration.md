---
title: "Use Eclipse Ditto with Azure IoT Hub as broker"
published: true
permalink: 2021-3-04-azure-iot-hub-integration.html
layout: post
author: david_schwilk
tags: [blog, architecture, connection]
hide_sidebar: true
sidebar: false
toc: true
---

This blogpost is based upon Eclipse Ditto Version **1.5.0**, the Azure IoT Suite as of
**2021-03-04** and the azure-iot-device-client version **1.29.2**.

# Connecting devices to Eclipse Ditto via Azure IoT Hub
This blog post elaborates on connecting and managing devices in Eclipse Ditto by using the Azure IoT Hub 
as a broker.

The basic functionality that can be used at the moment of creating this blogpost are:

- \[D2C\] Sending telemetry data from the device to update its digital-twin representation.
- \[D2C\] Same ID enforcement based on the Azure IoT Hub device-id to prevent spoofing other digital-twins.
- \[C2D\] Sending live-messages to the device.
- \[D2C\] Sending feedback to live messages to the service.

## Setting up connections in Ditto
The features described above will work with an "out-of-the-box" Azure IoT Hub subscription, 
so no additional configuration is needed in the IoT Hub. In order to connect Ditto to the IoT Hub you have to set up two connections.
One for receiving telemetry data, the other for sending live-messages and receiving live-message feedback.

### Telemetry Connection

This connection subscribes to telemetry messages, published by the Azure IoT Hub built-in "Event Hub like" endpoint.

Adding an enforcement for the ```{{ thing:id }}``` based on the ```{{ header:iothub-connection-device-id }}``` prevents 
applying a digital-twin update to the twin of another device (Device Spoofing).

To establish this connection the placeholder below have to be substituted by:

```{{userName}}```: The SharedAccessKeyName in your Event Hub-compatible endpoint. (i.e. service)

```{{password}}```: The SharedAccessKey in your Event Hub-compatible endpoint.

```{{endpoint}}```: The Endpoint in your Event Hub-compatible endpoint. (Cut away leading "sb://" and trailing slash)

```{{entityPath}}```: The EntitiyPath in your Event Hub-compatible endpoint.

*Note: I would suggest using the "service" policy instead of the "iothubowner" policy, since this is more restricitve 
and represents the actual use of Ditto as a  northbound service.*

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {},
  "piggybackCommand": {
    "type": "connectivity.commands:createConnection",
    "connection": {
      "id": "azure-example-connection-telemetry",
      "connectionType": "amqp-10",
      "connectionStatus": "open",
      "failoverEnabled": false,
      "uri": "amqps://{{userName}}:{{password}}@{{endpoint}}:5671",
      "source": [
        {
          "addresses": [
          "{{entityPath}}/ConsumerGroups/$Default/Partitions/0",
          "{{entityPath}}/ConsumerGroups/$Default/Partitions/1"
        ],
          "authorizationContext": ["ditto"],
          "enforcement": {
            "input": "{{ header:iothub-connection-device-id }}",
            "filters": [
              "{{ thing:id }}"
            ]
          }
        }
      ]
    }
  }
}
```

[Further information on D2C messaging capabilities of Azure IoT Hub](https://docs.microsoft.com/en-us/azure/iot-hub/iot-hub-devguide-messages-d2c)

[Further information on the built-in "event-hub like" endpoint of Azure IoT Hub](https://docs.microsoft.com/de-de/azure/iot-hub/iot-hub-devguide-messages-read-builtin)

### Message connection

This connection enables forwarding live messages to the Azure IoT Hub (which forwards it to the message)
and receiving feedback to these live-messages from the device.

Adding the header-mapping ```"message_id": "{{header:correlation-id}}"``` enables Azure IoT Hub to correlate messages.
Adding the header-mapping ```"to": "/devices/{{ header:ditto-message-thing-id }}/messages/deviceInbound"``` is 
necessary for correct message routing by Azure IoT Hub.

To establish this connection the placeholder below have to be substituted by:

```{{userName}}```: The name of the chosen policy + "@sas.root." + the name of your IoT Hub (i.e. service@sas.root.my-hub)

```{{hostName}}```: The Hostname of your IoT Hub (i.e. my-hub.azure-devices.net)

```{{encodedSasToken}}```: An URL encoded SAS token. Information on how to generate one can be found [here](https://docs.microsoft.com/en-us/cli/azure/ext/azure-iot/iot/hub?view=azure-cli-latest#ext_azure_iot_az_iot_hub_generate_sas_token). 
The generated token has to be additional URL encoded. (browser console -> ```encodeURI('{{generatedToken}}')```)

*Note: The generated SAS token has a maximum TTL of 365 days, so the token has to be changed to a newly generated before expiry. 
Otherwise, the connection closes automatically.*

```json
{
  "targetActorSelection": "/system/sharding/connection",
  "headers": {},
  "piggybackCommand": {
    "type": "connectivity.commands:createConnection",
    "connection": {
      "id": "azure-example-connection-messages",
      "connectionType": "amqp-10",
      "connectionStatus": "open",
      "failoverEnabled": false,
      "uri": "amqps://{{userName}}:{{encodedSasToken}}@{{hostName}}:5671",
      "target": [
        {"address": "/messages/devicebound",
          "topics": [
            "_/_/things/live/messages"
          ],
          "authorizationContext": ["ditto"],
          "headerMapping": {
            "message_id": "{{header:correlation-id}}",
            "to": "/devices/{{ header:ditto-message-thing-id }}/messages/deviceInbound"
          }
        }
      ]
    }
  }
}
```

**The java azure-iot-device-client currently can not be used to receive messages from Eclipse Ditto. Further information can 
be found in the corresponding [GitHub Issue](https://github.com/Azure/azure-iot-sdk-java/issues/1138).**

[Further information on C2D messaging capabilities of Azure IoT Hub](https://docs.microsoft.com/de-de/azure/iot-hub/iot-hub-devguide-messages-c2d)

## Possible improvements

Some features of Ditto could be used in combination with Azure IoT Hub with some adjustments. These include:

- Using the ImplicitThingCreationMapper to implicitly create a new thing when a new device is registered in Azure IoT Hub.
- Using the ConnectionStatusMapper to update the ConnectionStatus of things, when their devices disconnect from Azure IoT Hub.
- \[C2D\] Directly invoke methodAs on the device. (Direct Method Invocation)

### Using the ImplicitThingCreation and ConnectionStatus features based on Azure IoT Hub events

Azure IoT Hub has the possibility to publish Events for ConnectionStatus changes of devices and the creation/removal of new devices.
These events are published via an Azure EventGrid to another chosen Azure Application. 
By publishing these Events to an Azure Event Hub, a Ditto AMQP connection can subscribe for them.

The payload-mappers for ImplicitThingCreation and ConnectionStatus could be adjusted to handle such event messages and 
create new Things/ update the ConnectionStatus feature depending on the received messages.

[Further information on the Events published by Azure ioT Hub](https://docs.microsoft.com/de-de/azure/event-grid/event-schema-iot-hub?tabs=event-grid-event-schema)

### Using direct method invocation

Azure IoT Hub provides an endpoint for directly invoking methods on a device, which can be compared to live-commands. 
This can only be done via HTTP however. The authentication mechanism that has to be used 
is SAS authentication. This authentication however is not yet implemented for HTTP Push 
connections.

[Further information on direct method invocations](https://docs.microsoft.com/de-de/azure/iot-hub/iot-hub-devguide-direct-methods)

## Overall connection schema
![Connection Overview](images/blog/2021-03-04-azure-iot-hub-integration-overview.png)