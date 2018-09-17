---
title: "Selective message subscriptions available. On all offered channels! "
published: true
permalink: 2018-09-13-selective-message-subscriptions.html
layout: post
author: philipp_michalski
tags: [blog, connectivity, rql, connection]
hide_sidebar: true
sidebar: false
toc: true
---

The connectivity service supercharged Ditto's flexibility in integrating with other services. It's such a great feature to let the other connected services know about thing updates and property changes. Even direct message exchange with real world assets became more flexible through the multi-protocol support. But with a steady increase of connected devices, those messages easily sum up to a huge number. 

Also not every message consuming application needs to know everything thats going on. In fact the only use case that requires processing of every message is logging. Therefore most of the times an application waits for a specific message to trigger a specific action. So all other messages are discarded unused. This adds a lot of unnecessary overhead both to the message transport capabilities and the processing of messages at the receiving end.

But what if you could avoid receiving those messages at all. Well you can! This is exactly what selective message subscriptions do: Configurable message filters that are applied to Ditto's publishing connection before anything goes on the line.


// use cases

// examples