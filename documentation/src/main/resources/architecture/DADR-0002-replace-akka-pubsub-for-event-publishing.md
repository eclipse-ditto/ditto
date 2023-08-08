# Record architecture decisions

Date: 23.08.2019

## Status

accepted

## Context

Up to now Ditto used Pekko's distributed publish/subscribe in order to emit e.g. `ThingEvent`s to interested other services:

* gateway
    * websocket/SSE sessions publishing events
* connectivity
    * AMQP 1.0 / AMQP 0.9.1 / MQTT / Kafka sessions publishing events
* things-search
    * ThingUpdater updating the search index

That naive approach works, but does not provide *horizontal scalability*:

* each single service instance generally interested in `ThingEvent`s gets all of them, regardless of whether someone is actually interested in them
* as a result a lot of avoidable JSON deserialization is done
* when Ditto needs to scale the event publishing horizontally, adding new gateway or connectivity instances will not help scaling the event publishing
    * still all instances will have to process each `ThingEvent` and discard if not relevant

## Decision

We will implement a custom Ditto pub/sub which

* uses "authorization subjects" as topics when subscribing
* uses "read subjects" as topics when publishing
* manages and distributes the active subscriptions via Pekko Distributed Data (ddata)
* emits `ThingEvent`s only to service instances where at least one consumer consumes the event

## Consequences

The event publishing is no longer implemented by a proven and stable Pekko feature but lies in our own
responsibility. This has upsides (we can implement it just the way we need it) as well as downsides 
(we might add bugs which were not).

The consequence for horizontal scalability is that the event publishing should as well - like the rest of Ditto - be 
horizontally scalable.