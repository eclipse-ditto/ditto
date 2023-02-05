---
title: Release notes 0.8.0
tags: [release_notes]
keywords: release notes, announcements, changelog
summary: "Version 0.8.0 of Eclipse Ditto, released on 28.11.2018"
permalink: release_notes_080.html
---

Roughly one year after the [initial code contribution](2017-11-10-welcome-to-ditto.html) of Eclipse Ditto and several
milestone releases our team is very proud to announce the first official release of Ditto: `0.8.0`.

Different to milestone releases which we already did this release is completely [IP (intellectual property) checked by 
the Eclipse Foundation](https://www.eclipse.org/projects/handbook/#ip) meaning that project code as well as all used 
dependencies were "[...] reviewed to ensure that the copyrights expressed are correct, licensing is valid 
and compatible, and that other issues have been uncovered and properly investigated."

## What's in this release?

Eclipse Ditto 0.8.0 focuses on providing advanced capabilities in building and working with the _digital twins_ pattern.<br/>
Building and exposing digital twins is possible via different APIs: HTTP/REST, WebSockets, AMQP 1.0, AMQP 0.9.1 and 
MQTT 3.1.1 are supported.<br/>
Interaction with the bidirectional APIs is done via the "Ditto Protocol", a protocol Ditto defined for twin interaction.<br/>
When working with a large set of twins (millions of), searching, finding and selecting partial data is possible.<br/>
On all APIs, Ditto ensures that only authorized subjects may interact (read/write) with the digital twins with the use 
of fine grained policies.

In order to build digital twins of real world devices several integration approaches may be applied.<br/>
Ditto can establish a connection to Eclipse Hono or other AMQP 1.0 endpoints, AMQP 0.9.1 brokers or MQTT 3.1.1 brokers 
(e.g. Eclipse Mosquitto) and optionally transform received messages to "Ditto Protocol".<br/>
Powerful devices may alternatively directly send their data to Ditto's HTTP/WebSocket endpoints in order to reflect 
changes made to them.

For integrating with [Eclipse Hono](https://eclipse.org/hono/) this release makes it possible to subscribe to 
telemetry/events from devices connected via Hono and to also send command&control messages to devices 
connected to Hono and correlate replies from Hono accordingly.

### Changelog

Compared to the latest milestone release [0.8.0-M3](release_notes_080-M3.html), the following changes, new features and
bugfixes were added.

#### New features

##### [Support Hono's command&control in Ditto connectivity](https://github.com/eclipse-ditto/ditto/issues/164)

Eclipse Ditto can now map arbitrary headers when connecting to AMQP 1.0 endpoints or AMQP 0.9.1 brokers.
That way Ditto can send "command&control" messages to Eclipse Hono and correlate a potential response coming from a 
device.

#### Bugfixes

This release contains several bugfixes, this is a complete list of the 
[merged pull requests](https://github.com/eclipse-ditto/ditto/pulls?q=is%3Apr+milestone%3A0.8.0+).



## API stability

As you noticed, Ditto is not yet released as 1.x version, it is still an 
[incubating Eclipse project](https://wiki.eclipse.org/Development_Resources/Process_Guidelines/What_is_Incubation).

That means that the project _could_ choose to break APIs at any time without prior deprecation.

We however can already guarantee that at the HTTP API for API version 1 and 2 the
API stability is already ensured for the core functionality of managing `things` and `policies`.

As the commercial product based on Eclipse Ditto, [Bosch IoT Things](https://www.bosch-iot-suite.com/things/), is 
already used productive and in various projects, the API stability has to be and will be ensured moving forward towards 
a 1.0.0 release.

## Roadmap

The Ditto project plans on releasing (non-milestone releases) twice per year, once every 6 months. 

In summer 2019 we expect to release a version 0.9.0 and in late 2019 we expect to graduate 
(exit the Eclipse incubation phase) with a 1.0.0 release. 

The plans towards which features will roughly be included will be added soon.
