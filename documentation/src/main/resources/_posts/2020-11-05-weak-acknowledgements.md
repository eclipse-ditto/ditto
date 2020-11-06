---
title: "Weak acknowledgments to decouple signal publishers and subscribers"
published: true
permalink: 2020-11-05-weak-acknowledgements.html
layout: post
author: yufei_cai
tags: [blog]
hide_sidebar: true
sidebar: false
toc: true
---

## Motivation

[Ditto 1.2.0](2020-08-31-release-announcement-120) introduced at-least-once delivery via
[acknowledgement requests](basic-acknowledgements).
It increased coupling between the publisher and the subscriber of signals in that the subscriber is no longer at the
liberty to filter for signals it is interested in. Instead, the subscriber must consume all signals in order to
fulfill acknowledgement requests and prevent endless redelivery.
To combat the problem,
[Ditto 1.4.0](2020-10-28-release-announcement-140) made acknowledgement labels unique and introduced the requirement
to manage [_declared acknowledgements_](basic-acknowledgements#issuing-acknowledgements), identifying of each
subscriber.
It is now possible for Ditto to issue
[_weak acknowledgements_](basic-acknowledgements#weak-acknowledgements-wacks) on behalf of the subscriber
whenever it decides to not consume a signal. That allows subscribers to configure RQL and namespace filters freely
without causing any futile redelivery.

{% include note.html content="Weak acknowledgements are available since Ditto 1.5.0." %}

## What it is

A  [_weak acknowledgement_](basic-acknowledgements#weak-acknowledgements-wacks) is issued by Ditto for any
[acknowledgement request](basic-acknowledgements#requesting-acks) that will not be fulfilled now or ever without
configuration change. A weak acknowledgement is identified by the header `ditto-weak-ack: true`. The status code
of weak acknowledgements is 200 OK; it signifies that any redelivery is not to be made on their account.
A weak acknowledgement may look like this in Ditto protocol:
```json
{
  "topic": "com.acme/xdk_53/things/twin/acks/my-mqtt-connection:my-mqtt-topic",
  "headers": {
    "ditto-weak-ack": true
  },
  "path": "/",
  "value": "Acknowledgement was issued automatically, because the subscriber is not authorized to receive the signal.",
  "status": 200
}
```

## How it works

Since Ditto 1.4.0, subscribers of _twin events_ or _live signals_ are required to declare unique acknowledgement labels
they are allowed to send. The labels of acknowledgement requests are then identifying the intended subscribers.
If the intended subscriber exists but does not receive the signal for non-transient reasons, Ditto issues
a weak acknowledgement for that subscriber. Such reasons may be:
- The intended subscriber **is not authorized** to receive the signal by policy;
- The intended subscriber did not subscribe for the signal type (_twin event, live command, live event or live message);
- The intended subscriber filtered the signal out by its [namespace or RQL filter](basic-changenotifications#filtering);
- The intended subscriber dropped the signal because its [payload mapper](connectivity-mapping) produced nothing.

## Limitation

The distributed nature of cluster pub/sub means that weak acknowledgements are not always issued correctly.
They are only _eventually correct_ in the sense that some time after a change to the publisher-subscriber pair,
the issued weak acknowledgements will reflect the change. Such changes include:
- Opening and closing of Websocket or other connections acting as the subscriber;
- Subscribing and unsubscribing for different signal types via Websocket;
- Modification of connections via the [connectivity API](connectivity-manage-connections);
- Migration of a connection from one Ditto cluster member to another due to load balancing.
