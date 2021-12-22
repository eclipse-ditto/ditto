---
title: "Conditionally direct retrieving API calls to either live or twin channel"
published: true
permalink: 2021-12-22-live-channel-condition.html
layout: post
author: thomas_jaeckle
tags: [blog, http]
hide_sidebar: true
sidebar: false
toc: true
---

After the added option to target the [live channel](protocol-twinlive.html#live) by adding the `channel=live` to HTTP
requests (see also [blog post about "HTTP live channel"](2021-12-20-http-live-channel.html)), Eclipse Ditto 
**version 2.3.0** will in addition support to define a 
[live channel condition](basic-conditional-requests.html#live-channel-condition), which, when evaluating to `true`, 
will retrieve data from a device via the live channel, or fall back to the persisted [twin](protocol-twinlive.html#twin) 
otherwise.

{% include note.html content="In order to use the live channel, the device receiving live commands must be able to understand
    and answer in [Ditto Protocol messages](protocol-specification.html) with the
[topic's channel being `live`](protocol-specification-topic.html#live-channel)." %}

## Relying on conditions

Ditto 2.1.0 added support for [conditional requests](basic-conditional-requests.html) by e.g. specifying a `condition`
query parameter for HTTP API calls.

This mechanism can now additionally be used to formulate a condition through the query parameter `live-channel-condition`
whether data shall be retrieved via the `live` channel from the device.

## Optionally update twin automatically based on retrieved live data

Whenever a device reports back its actual sensor readings, one can be sure that this data is the most recent "truth".  
It therefore is obvious to use that data in order to update the persisted twin managed by Ditto.

This can optionally be enabled by configuring the new 
[UpdateTwinWithLiveResponse mapper](connectivity-mapping.html#updatetwinwithliveresponse-mapper) in a Ditto 
[managed connection](basic-connections.html).  
This mapper will transform each "live response" retrieving and transporting data from a device to an additional 
[merge command](protocol-specification-things-merge.html) modifying the twin with that `live` data.


## Example: Device is marked to be polled

This is for example helpful if the device does not push its newest sensor readings actively into its twin 
representation managed by Ditto, but relies on being polled for the newest readings.  
In that case, the twin could be marked e.g. as `attributes/polling-mode=true`:
```json
{
  "thingId": "my.namespace:my-polling-device-1",
  "policyId": "my.namespace:my-polling-device-1",
  "attributes": {
    "polling-mode": true
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 23.42
      }
    }
  }
}
```

When an IoT application now needs to retrieve the latest temperature value, it can formulate a query (e.g. in HTTP):
```
GET /api/2/things/my.namespace:my-polling-device-1/features/temperature/properties/value
  ?live-channel-condition=eq(attributes/polling-mode,true)
  &timeout=10s
  &live-channel-timeout-strategy=use-twin
```

The specified `live-channel-condition` will evaluate to `true`, meaning that the retrieve is transformed to a 
[live command](protocol-twinlive.html#live) and sent to the device, e.g. connected via a 
[managed connection](basic-connections.html).  
Upon receiving the "live retrieve" at the device, the device can create a command response correlated with the same
`correlation-id` and send it back to Ditto with the current value.  
This value is then returned as result of the `GET`, the HTTP response header `channel` will indicate that the data was
sent by the device by having the value `live`.

If the device does not answer with a correctly correlated response within the given `timeout`, the request will fall back
to the [twin](protocol-twinlive.html#twin) channel, retrieving the data from the last known persisted temperature value 
in the twin managed by Ditto.  
The HTTP response header `channel` will indicate that the data was received by the persisted twin by having the value 
`twin`.


## Example: Device contains a connection status

Another perfect fit for that feature is when the device (or the device connectivity layer) is able to reflect the 
connection status of the device in the Ditto managed twin.  
When e.g. using [Eclipse Hono](https://www.eclipse.org/hono/) in combination with Ditto, the 
[ConnectionStatus](connectivity-mapping.html#connectionstatus-mapper) mapper can be configured in a Ditto 
[managed connection](basic-connections.html) which will automatically update a [feature](basic-thing.html#features) in
the twin based on Hono's [device notifications](https://www.eclipse.org/hono/docs/concepts/device-notifications/) 
reflecting a `"readySince"` and `"readyUntil"` timestamp:

```json
{
  "thingId": "my.namespace:my-connection-aware-device-1",
  "policyId": "my.namespace:my-connection-aware-device-1",
  "features": {
    "ConnectionStatus": {
      "definition": [ "org.eclipse.ditto:ConnectionStatus:1.0.0" ],
      "properties": {
        "status": {
          "readySince": "2021-12-22T14:16:18Z",
          "readyUntil": "9999-12-31T23:59:59Z"
        }
      }
    },
    "temperature": {
      "properties": {
        "value": 23.42
      }
    }
  }
}
```

In that case, the `"readyUntil"` will contain a timestamp how long the device will be ready to receive commands, the 
timestamp `"9999-12-31T23:59:59Z"` being an alias for indefinitely (once the device disconnects, e.g. from the MQTT 
adapter of Eclipse Hono, this timestamp will be set to the disconnection time).

Utilizing this "connection awareness", we can now easily define a query to retrieve data from the real device when it 
is connected and use the persisted `twin` when it is not connected or runs into a timeout.  
For using the current time as ISO-8601 timestamp, a new placeholder `time:now` was also introduced, usable in RQL
expressions everywhere in Ditto:
```
GET /api/2/things/my.namespace:my-connection-aware-device-1/features/temperature/properties/value
  ?live-channel-condition=gt(features/ConnectionStatus/properties/status/readyUntil,time:now)
  &timeout=10s
  &live-channel-timeout-strategy=use-twin
```

Of course every other field in the persisted twin may also be used in the `live-channel-condition`, if your devices e.g.
are aware if they are connected or not by other means (e.g. by setting an attribute), this can be utilized as well.


## Feedback?

Please [get in touch](feedback.html) if you have feedback or questions towards this new functionality.

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/> 
The Eclipse Ditto team
