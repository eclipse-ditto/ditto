---
title: What is Eclipse Ditto?
keywords: purpose, about, motivation, digital twin, digitaltwin, twin
tags: [getting_started]
permalink: intro-overview.html
---

Eclipse Ditto is an open-source framework for building [digital twins](intro-digitaltwins.html) in the
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iot}}">IoT</a>.
It lets you represent physical devices -- sensors, machines, vehicles, and more -- as JSON-based
"Things" that your applications interact with through standard web APIs.

{% include callout.html content="**TL;DR**: Ditto provides a ready-to-use backend for managing digital twins, so you can
interact with devices through HTTP, WebSocket, and messaging APIs without building a custom IoT backend." type="primary" %}

## What is it?

Ditto mirrors real-world devices as virtual "Things" in the cloud. Each Thing holds the device's
last-known state, its metadata, and a policy that controls who can read or write its data.

Your applications never need to know *how* a device connects or *which protocol* it speaks.
You work with a Thing the same way you work with any web resource: through REST-style APIs.

## What is it not?

Ditto is **not** an end-to-end IoT platform. It does not:

* Run software on gateways or edge devices
* Define or implement a device communication protocol
* Prescribe the data structure a device must use

Ditto focuses on the **backend layer**. It assumes your devices are already connected to the internet
(for example via [Eclipse Hono](https://www.eclipse.org/hono/)) and provides web APIs so your
applications can work with those devices as digital twins.

## When to use it?

Consider a typical IoT solution. You have hardware (sensors, actuators) and software (mobile apps,
dashboards, backend services). The backend is responsible for:

* **Providing an API** that abstracts away hardware details
* **Routing requests** between devices and applications
* **Enforcing authorization** so that each user or service accesses only the data it should
* **Caching device state** so applications can read data even when a device is offline
* **Notifying interested parties** when device state changes

Ditto handles all of these responsibilities out of the box.

{% include callout.html content="Ditto's goal is to eliminate the need to build and operate a custom IoT backend.
You focus on connecting your devices and building your business applications -- Ditto handles
everything in between." type="info" %}
