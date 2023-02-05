---
title: "Ditto's connectivity capabilities are pimped up"
published: true
permalink: 2018-04-25-connectivity-service.html
layout: post
author: thomas_jaeckle
tags: [blog, connectivity]
hide_sidebar: true
sidebar: false
toc: false
---

It has been quite lately on our website and on GitHub as the Ditto team currently prepares its new `connectivity` 
microservice. Until now Ditto's `amqp-bridge` service could connect to AMQP1.0 endpoints 
(e.g. [Eclipse Hono](https://www.eclipse.org/hono/)).

That worked quite well, but still had some issues:

* failover/reconnection was not always done properly
* the current connection state could not yet be retrieved
* AMQP 1.0 is a great protocol including [reactive principles](https://www.reactivemanifesto.org) but it still is not very "mainstream"
* the AMQP 1.0 messages consumed by Ditto already had to be in [Ditto Protocol](protocol-overview.html), otherwise Ditto
  could not understand them

Our current implementation focus lies on two GitHub issues resolving those problems:

* [Enhance existing AMQP-bridge with AMQP 0.9.1 connectivity](https://github.com/eclipse-ditto/ditto/issues/129)
* [Support mapping arbitrary message payloads in AMQP-bridge](https://github.com/eclipse-ditto/ditto/issues/130)


## Changes and Enhancements


### Renaming

With the new responsibilities of the former amqp-bridge we have renamed the `amqp-bridge-service` to `connectivity-service`. <br/>
The Docker image and the Maven artifacts are affected by this change.


### Enhanced connectivity

The new [connectivity](architecture-services-connectivity.html) microservice can now manage and handle both AMQP 1.0 and 
AMQP 0.9.1 connections at the same time. <br/>
That means that Ditto from now on supports connecting to running AMQP 1.0 endpoints or to AMQP 0.9.1 brokers (e.g. RabbitMQ).
The architecture of the `connectivity` microservice is designed to also support connecting via other protocols in the future.

Need to connect to a Kafka in order to process digital twin [commands](basic-signals-command.html) from there or publish 
[change notifications](basic-changenotifications.html)? <br />
Or want to send all state changes happening to twins to a time series database?

The `connectivity` service is the new place to integrate your managed digital twins with other systems. 


### JSON format of connections

As Ditto now supports more than AMQP 1.0, we had to adjust the JSON format for creating new connections. 
The new one is documented here: [Manage connections in connectivity](connectivity-manage-connections.html).


### Payload mapping of external messages

Eclipse Ditto is about providing access to IoT devices via the [digital twin](intro-digitaltwins.html) pattern. In order to
provide structured APIs for different heterogeneous devices Ditto defines a lightweight JSON based [model](basic-overview.html).

Devices in the IoT, may they be brownfield devices or newly produced devices, will probably not send their data to the
cloud in the structure and [protocol](protocol-overview.html) Ditto requires. They should not need to be aware of something
like Ditto running in the cloud mirroring them as digital twins.

That's why we added a JavaScript based payload mapping to the `connectivity` service which is responsible for:

* transforming text- or byte-payload from messages consumed via a `source` of a created connection to 
  [Ditto Protocol](protocol-overview.html) [commands](basic-signals-command.html) and [messages](basic-messages.html)
* transforming back [responses](basic-signals-commandresponse.html) issued by commands and [events](basic-signals-event.html)
  from Ditto Protocol to some text- or byte-payload before sending the message back via the configured `target` channel 

The `incoming` and `outgoing` scripts must be configured when creating a new connection 
[via DevOps commands](connectivity-manage-connections.html).


## Example
Please find more information and examples at:


* [Connectivity overview](connectivity-overview.html)
* [Payload mapping in connectivity](connectivity-mapping.html)


<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
