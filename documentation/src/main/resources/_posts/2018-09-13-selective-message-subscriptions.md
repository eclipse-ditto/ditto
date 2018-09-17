---
title: "Selective message subscriptions available!"
published: true
permalink: 2018-09-13-selective-message-subscriptions.html
layout: post
author: philipp_michalski
tags: [blog, connectivity, rql, connection]
hide_sidebar: true
sidebar: false
toc: true
---

The connectivity service supercharged Ditto's flexibility in integrating with other services. It's such a great feature to let the other connected services know about thing updates and property changes. Even the direct message exchange with real-world assets became more flexible through the multi-protocol support. But with a steady increase in connected devices, those messages easily sum up to a huge number. 

Also, not every message consuming application needs to know everything that's going on. In fact, the only use case that requires processing of every message is logging. Therefore most of the times an application waits for a specific message to trigger a specific action. So all other messages are discarded unused. This adds a lot of unnecessary overhead both to the message transport capabilities and the processing of messages at the receiving end.

But what if you could avoid receiving those messages at all. Well, you can! This is exactly what selective message subscriptions do: Configurable message filters that are applied to Ditto's publishing connection before anything goes on the line. They can help you with a lot of problems in a bunch of scenarios:


* Bandwith limitations: The amount of occurring events is too large and/or frequent to be delivered via the available subscription channels. With selective message filters, you can mute the noise in your event stream.
* Information hiding: Let consuming services only know what they need to know. Message filters allow you to control all published content in great detail.
* Specialized notifications: A specific event filter can be used to set a value thresholds or a status-change trigger. This removes the burden of implementing filter logic on the application side.
* Event routing: Create multiple connections with Ditto's connectivity service and route your events through those aligned with your requirements. All by specifying appropriate filters for your connection targets.


With the new Ditto release `0.8.0-M1`, those filters are available for the following endpoints:

* WebSocket
* Server-Sent Events (SSE)
* All supported connectivity protocols (AMQP 0.9.1, AMQP 1.0, MQTT)

You can use a basic namespace filter on the following topics:

* Twin events
* Live events
* Live messages
* Live commands

This filter is a comma-separated list of selected namespaces. It only allows messages related to one of the given namespaces.

Furthermore, there is an additional [RQL filter](/basic-rql.html) for advanced description of twin and live events. Powered by the mighty syntax of Ditto's search API it allows configuring the selected events in the same manner as you search for things.

Check out the [documentation](/basic-changenotifications.html#filtering) for more information on options and configuration.
