---
title: Protocol twin/live channel
keywords: channel, command, device, event, live, message, protocol, response, twin
tags: [protocol]
permalink: protocol-twinlive.html
---

The Ditto Protocol furthermore covers two different communication channels to address different aspects of devices and 
their **digital twins**.

## Twin

The first *channel*, **twin**, connects to the digital representation of a Thing.
This Thing is managed with Ditto and its state and properties can be read and updated.

{% include image.html file="pages/protocol/ditto-twin-channel.png" alt="Ditto twin channel" caption="Ditto twin channel pattern" max-width=800 %}

## Live

The second *channel*, **live**, routes a command/message towards an actual device.
The handling and execution of a received command/message by a device (or a gateway which connects the device) is very 
specific to the solution and thus out of Ditto's scope.

{% include image.html file="pages/protocol/ditto-live-channel.png" alt="Ditto live channel" caption="Ditto live channel pattern" max-width=800 %}

What Ditto however does, when routing **live** commands/messages, is an [authorization check](basic-auth.html).
Thus Ditto ensures that only authorized parties are able to send commands or messages.

{% include note.html content="In order to use the live channel, the device receiving live commands must be able to understand
    and answer in [Ditto Protocol messages](protocol-specification.html) with the 
    [topic's channel being `live`](protocol-specification-topic.html#live-channel)." %}


## Other

Policy commands do not fit any of the above two categories as they are not directly related to a device. A Policy is 
not a **twin** of a device. Hence, the **live** channel cannot be used to address the device directly.
Therefore, Policy commands have no *channel* in the Ditto Protocol format specification.   
