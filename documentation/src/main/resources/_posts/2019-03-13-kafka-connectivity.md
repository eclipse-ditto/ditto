---
title: "Connectivity to Apache Kafka in Eclipse Ditto"
published: true
permalink: 2019-03-13-kafka-connectivity.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

Today we added connectivity to [Apache Kafka](https://kafka.apache.org/). In a first step, it is possible to publish
*twin events*, *messages*, *live commands and events* to Kafka topics.

Since the last addition to Ditto's connectivity which [added MQTT connectivity](2018-10-16-example-mqtt-bidirectional.html),
the connectivity feature got a lot of stabilization and new smaller features, e.g. the recent addition of 
[placeholder functions](basic-placeholders.html#function-expressions).

Returning to the Kafka integration Ditto can now, for example, whenever a [digital twin](intro-digitaltwins.html) is 
changed (e.g. a device updated some state data), publish a *twin event* to a Kafka topic.

If you already rely on Apache Kafka as a source for your data lake or analytics, integrating Ditto and its digital twins
is now super easy.

Find out more at our [Kafka documentation](connectivity-protocol-bindings-kafka2.html).


{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
