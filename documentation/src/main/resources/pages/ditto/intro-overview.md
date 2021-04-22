---
title: Eclipse Ditto™ documentation overview
keywords: purpose, about, motivation, digital twin, digitaltwin, twin
tags: [getting_started]
permalink: intro-overview.html
---


## What is it?

Eclipse Ditto™ is a technology in the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iot}}">IoT</a> 
implementing a software pattern called “**[digital twins](intro-digitaltwins.html)**”.<br/>
A digital twin is a virtual, cloud based, representation of his real world counterpart 
(real world “Things”, e.g. devices like sensors, smart heating, connected cars, smart grids, EV charging stations, …).

The technology mirrors potentially millions and billions of digital twins residing in the digital world 
with physical “**Things**”. This simplifies developing IoT solutions for software developers as they do not need 
to know how or where exactly the physical “Things” are connected.

With Ditto a thing can just be used as any other web service via its digital twin.


## What is it not?

Ditto is not another fully-fledged IoT platform. It does not provide software running on IoT gateways, and it does not
define or implement an IoT protocol in order to communicate with devices.

Its focus lies on back end scenarios by providing web APIs in order to simplify working with already connected (e.g. 
via [Eclipse Hono](https://www.eclipse.org/hono/)) devices and “Things” from customer apps or other back end software.

It also does not specify which data or which structure a “Thing” in the IoT has to provide. 


## When to use it?

  {% include callout.html content="**TL;DR**<br/>Use it in order to get a fully-fledged, authorization aware API 
  (HTTP, WebSocket and other messaging protocols) for interacting with your digital twins and all aspects around them." type="primary" %}

Imagine you are building an IoT solution. And let's assume that you use both hardware (e.g. sensors or actuators) and
software (e.g. a mobile or web app) in order to solve your customer's problem.

In such a scenario you have several places where to implement software:
* on or near the hardware, e.g. on an Arduino using `C/C++` or on an Raspberry PI using `Python`,
* optionally on a gateway establishing the Internet connectivity (e.g. based on [Eclipse Kura](https://www.eclipse.org/kura/)),
* in the mobile or web app using `Java`, `Javascript`, `Swift` etc.,
* in the “back end” fulfilling several responsibilities like
    * providing an API abstracting from the hardware,
    * routing requests between hardware and customer apps,
    * ensuring only authorized access,
    * persisting last reported state of hardware as cache and for providing the data when hardware is currently not connected,
    * notifying interested parties (e.g. other back end services) about changes,
    * …

Ditto focuses on solving the responsibilities a typical “back end” has in such scenarios.

  {% include callout.html content="Its goal is to free IoT solutions from the need of implementing and operating a 
  custom back end. Instead by using Eclipse Ditto they can focus on business requirements, on connecting devices to
  the cloud/back end and on implementing business applications." type="info" %} 
