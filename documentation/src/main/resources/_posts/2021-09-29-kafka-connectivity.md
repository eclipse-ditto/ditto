---
title: "Completed Kafka Connectivity"
published: true
permalink: 2021-09-29-kafka-connectivity.html
layout: post
author: yannic_klem
tags: [blog]
hide_sidebar: true
sidebar: false
toc: true
---

## Consuming messages from Apache Kafka in Eclipse Ditto

Eclipse Ditto did support publishing events and messages to Apache Kafka for quite some time now.  
The time has come to support consuming as well.

A Kafka connection behaves slightly different from other consuming Connections in Ditto.  
The following aspects are special:
* [Backpressure by using acknowledgements](connectivity-protocol-bindings-kafka2.html#quality-of-service)
* [Preserve order of messages on redelivery due to failed acknowledgements](connectivity-protocol-bindings-kafka2.html#backpressure-by-using-acknowledgements)
* [Expiry of messages](connectivity-protocol-bindings-kafka2.html#message-expiry)

### Scalability

Kafka's way of horizontal scaling is to use partitions.
The higher the load the more partitions should be configured.  
On consumer side this means that a so-called consumer group can have as many consuming clients as number of partitions exist.  
Each partition would then be consumed by one client.

This perfectly matches with Ditto connections scaling, each Ditto connection builds such a consumer group.    
For a connection there are two ways of scaling:
1. `clientCount` on [connection level](basic-connections.html#connection-model)
2. `consumerCount` on [source level](basic-connections.html#sources)

A connection client bundles all consumers for all sources and all publishers for all targets.
It is guaranteed that for a single connection only one client can be instantiated per instance of the connectivity microservice.  
This way Ditto provides horizontal scaling.

Therefore, the `clientCount` should never be configured higher than the number of available connectivity instances. 

If the connectivity instance is not fully used by a single connection client, the `consumerCount` can be used to scale a 
connection's consumers vertically.
The `consumerCount` of a source indicates how many consumers should be started for a single connection client for this source.
Each consumer is a separate consuming client in the consumer group of the connection.

This means that the number of partition should be greater or equal than `clientCount` multiplied by the highest 
`consumerCount` of a source.

### Backpressure and Quality of Service

Usually there is an application connected to Ditto which is consuming either messages or events of devices connected to Ditto.  
These messages and events can now be issued by devices via Kafka.  
What happens now when the connected application temporarily can't process the messages emitted by Ditto in the rate the 
devices publish their messages via Kafka into Ditto?  
The answer is: "It depends."

There are two steps of increasing delivery guarantee for messages to the connected application.
1. Make use of [acknowledgements](basic-connections.html#source-acknowledgement-requests)
2. Configure the `qos` for the [source](basic-connections.html#sources) to `1`

The first will introduce backpressure from the consuming application to the Kafka consumer in Ditto.  
This means that the consumer will automatically slow down consuming messages when the performance of the connected 
application slows down.
This way the application has time to scale up, while the messages are buffered in Kafka.

The second step can be used when it's necessary to ensure that the application not just received but successfully 
processed the message. If the message could not be processed successfully or if the acknowledgement didn't arrive in time, 
the Kafka consumer will restart consuming messages from the last successfully committed offset.

### Expiry

Now that we know about backpressure we also know, that messages could remain in Kafka for some time.  
The time can be limited by Kafka's retention time, but this would be applied to all messages in the same way.
What if some messages become invalid after some time, but others won't?

Ditto provides an [expiry of messages on a per-message level](connectivity-protocol-bindings-kafka2.html#message-expiry).
That way Ditto filters such expired messages but still processes all others.

## We embrace your feedback

Did you recognize a possible match of Ditto for some of your usecases?
Do you miss something in this new feature?  
We would love to get your [feedback](feedback.html).

<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
