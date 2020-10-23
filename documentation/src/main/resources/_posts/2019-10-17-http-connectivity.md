---
title: "Integration of HTTP endpoints/webhooks"
published: true
permalink: 2019-10-17-http-connectivity.html
layout: post
author: thomas_jaeckle
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

By adding another connectivity type - [HTTP](connectivity-protocol-bindings-http.html) - to Ditto's 
 connectivity, it is now (to be released in the next Ditto milestone 1.0.0-M2) possible to 
 publish *twin events*, *messages*, *live commands and events* to existing HTTP servers/endpoints.

That is especially useful for invoking existing APIs (which are most of the time HTTP based) whenever e.g.
 a digital twin was modified.

One example on how to benefit from this new feature is to invoke a custom 
 [IFTTT](https://ifttt.com) (if-this-than-that) [webhook](https://ifttt.com/maker_webhooks) via a HTTP `POST` request
 which then may trigger other IFTTT follow-up-actions (e.g. send a chat message to a [Slack](https://ifttt.com/slack) 
 room).

For IFTTT "webhooks" the address would be `POST https://maker.ifttt.com/trigger/<your-event-name>/with/key/<your-key>` 
 and the expected JSON body:

```json
{
  "value1": "...",
  "value2": "...",
  "value3": "..."
}
```

In combination with [payload mapping](connectivity-mapping.html), the `value1` to `value3` fields requested by the IFTTT
API can be extracted from the [Ditto Protocol](protocol-specification.html) and could contain the changed value.

In combination with [filters for targets](basic-connections.html#target-topics-and-filtering) you can even specify to
only publish e.g. `twin events`, where the temperature of a twin exceeded a certain threshold:

```
{
  "address": "POST:/trigger/<your-event-name>/with/key/<your-key>",
  "topics": [
    "_/_/things/twin/events?filter=gt(features/temperature/properties/value,25)"
  ],
  ...
}
```

Get creative on which HTTP APIs to trigger based on twin events. E.g. invoke a "function-as-a-service" API or invoke the 
Twitter API and let your digital twin tweet whenever it detects, that it is getting too warm in your office. 


{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
