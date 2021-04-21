---
title: Digital twins
keywords: digital twin, digitaltwin, twin, administrationshell, asset
tags: [getting_started]
permalink: intro-digitaltwins.html
---

{% include callout.html content="**TL;DR**<br/>Digital twins are a pattern for simplifying IoT solution development." type="primary" %}

The problem with the term **digital twin** is that there are many different understandings of what it means. 
Furthermore, the term was previously mostly used and coined by marketing. The term was/is missing a 
technical foundation of what to expect from a framework for digital twins.

Eclipse Ditto provides such a framework for digital twins and this page describes how Ditto defines/sees digital twins
from a technical perspective. 

## Digital twin from a technical perspective

For Eclipse Ditto the **digital twin** is a concept for abstracting a real world asset/device with 
all capabilities and aspects including its digital representation.

A digital twin
* mirrors physical assets/devices
* acts as a "single source of truth" for a physical asset
* provides various aspects+services around devices
* keeps real and digital worlds in sync
* can be applied in both industrial and consumer-centric IoT scenarios

A digital twin framework
* provides capabilities (APIs) to interact with digital twins
* ensures that access to twins can only be done by authorized parties
* allows to not only interact with single twins but also with populations of many of them
* integrates into other back-end infrastructure (like messaging systems, brokers)

## Industrial context

In the <a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.iiot}}">IIoT</a> the **digital twin** 
metaphor is becoming a popular concept for tracking a produced product/good in its complete lifecycle. 

Another term often used in the IIoT in combination with digital twin is the "Asset Administration Shell" 
("Verwaltungsschale" in german).
