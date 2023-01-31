---
title: "Example demonstrating REST and WebSocket API"
published: true
permalink: 2018-01-15-example-demonstrating-rest-to-websocket.html
layout: post
author: florian_fendt
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

There's a new example showing how to combine the REST and WebSocket API
over at the [Eclipse Ditto examples repository](https://github.com/eclipse-ditto/ditto-examples/tree/master/rest-to-websocket).
Right from the project's description:

>This example shows how to leverage the powers of combining the REST and
 WebSocket Messages API of [Eclipse Ditto](https://www.eclipse.org/ditto/).
 It demonstrates how to send direct Messages to a *live* Thing, as well as
 updating the *twin* representation of a Thing inside Ditto.
<br/> 
<br/> 
 The Thing in this case is a smart coffee machine ("SmartCoffee") that has
 some basic functionality. It accepts Messages that allow to start or stop
 the heating of the water tank. Moreover you can request the coffee
 machine to brew coffee, so you don't have to wait for your dose of caffeine.<br/>
 But before starting to brew a coffee, SmartCoffee will send a captcha
 that has to be solved.

{% include external_image.html 
href="https://raw.githubusercontent.com/eclipse/ditto-examples/master/rest-to-websocket/docs/images/make-coffee.gif" 
alt="Eclipse Ditto REST to WebSocket example gif" 
max-width=800 
caption="Source: https://github.com/eclipse-ditto/ditto-examples" %}

If you have any wishes, improvements, are missing something
or just want to get in touch with us, you can use one of
our [feedback channels](https://www.eclipse.org/ditto/feedback.html).

{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
