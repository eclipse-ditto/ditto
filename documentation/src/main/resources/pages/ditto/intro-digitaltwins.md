---
title: Digital Twins Explained
keywords: digital twin, digitaltwin, twin, administrationshell, asset
tags: [getting_started]
permalink: intro-digitaltwins.html
---

A digital twin is a virtual representation of a physical device or asset that stays synchronized
with the real world.

{% include callout.html content="**TL;DR**: A digital twin mirrors a physical device as a JSON data structure in the
cloud, giving your applications a single, always-available source of truth about that device." type="primary" %}

## What is a digital twin?

Imagine you have a temperature sensor in a warehouse. The sensor connects to the internet and
reports its reading every 30 seconds. A digital twin for that sensor is a JSON object stored in
Ditto that always reflects the sensor's latest state:

```json
{
  "thingId": "com.example:warehouse-sensor-1",
  "attributes": {
    "location": "Warehouse B, Shelf 3"
  },
  "features": {
    "temperature": {
      "properties": {
        "value": 22.5,
        "unit": "Celsius"
      }
    }
  }
}
```

When the sensor sends a new reading, Ditto updates the twin. When your dashboard queries the twin,
it gets the latest value -- even if the sensor is temporarily offline.

## How Ditto defines digital twins

From a technical perspective, a digital twin in Ditto:

* **Mirrors a physical asset** -- stores the device's current and desired state as structured JSON
* **Acts as a single source of truth** -- applications read from and write to the twin instead of
  talking directly to the device
* **Stays synchronized** -- updates flow from device to twin and from twin to device
* **Enforces access control** -- a [Policy](basic-policy.html) determines who can read or modify
  each part of the twin

Ditto as a digital twin framework:

* Provides APIs (HTTP, WebSocket, and other messaging protocols) to interact with twins
* Ensures that only authorized parties can access twin data
* Supports working with individual twins or querying across large populations of twins
* Integrates with messaging systems and brokers for device connectivity

## Industrial context

In the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iiot}}">IIoT</a>,
digital twins track manufactured products and assets throughout their lifecycle. The concept is
closely related to the "Asset Administration Shell" used in Industry 4.0 scenarios.

## Further reading

* [Hello World Tutorial](intro-hello-world.html) -- create your first digital twin
* [Data Model Overview](basic-overview.html) -- understand how Things, Features, and Policies fit
  together
