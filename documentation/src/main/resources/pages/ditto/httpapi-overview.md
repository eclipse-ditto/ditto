---
title: HTTP API overview
keywords: api, http, overview, REST
tags: [http]
permalink: httpapi-overview.html
---

Ditto's HTTP API is documented separately in the [HTTP API Doc](http-api-doc.html).

There you can explore the two different API versions (the difference is described in the
[Basic Overview](basic-overview.html)).

Ditto does not provide a fully compliant RESTful API in the
[academic sense](https://www.ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm) as it does not include
hyperlinks in the HTTP responses.
It however tries to follow the other best practices.

If you have any feedback on how to improve at that point, Ditto's developer team is [eager to learn](feedback.html).

## Channel

Ditto supports two types of communication channels: `twin` and `live`. 

The `twin` channel is used to communicate with the persisted **digital twin** representation of a device.  
The `live` channel can be used to communicate directly with an actual device.

In case the `live` channel is used, the device itself is responsible for answering the command/message.   
If no response is returned, the request will result in a timeout, and Ditto will respond with `408 Request Timeout`.
This timeout can be set with the `timeout` parameter. If no `timeout` parameter is set, the default of `10s` is used to
wait for response of the device.  
When routing live commands to devices, Ditto is doing an [authorization check](basic-auth.html) based on the policy
of the thing. Ditto also filters responses based on that policy. 

Ditto ensures that the response from the device contains the same `correlation ID`, `entity ID` and `path`.
For the device it is necessary to send back the correlating command response type for the send command. In case there is
a mismatch of the command and command response type Ditto will drop the response from the device and the request will 
result in a timeout.  

The default channel for all HTTP requests is the `twin` channel. The channel can either be set via HTTP header or via
query parameter.

For more information about the channel concept see section [Ditto Protocol > Protocol twin/live channel](protocol-twinlive.html).

## Content Type

Currently, the content-type `application/json` is supported for all REST resources except the _PATCH_ resource.
There the content-type has to be `application/merge-patch+json`.

