---
title: Things service
keywords: architecture, service, things
tags: [architecture]
permalink: architecture-services-things.html
---

The Things service persists and enforces authorization for [Things](basic-thing.html) and [Features](basic-feature.html), which represent your digital twins.

{% include callout.html content="**TL;DR**: The Things service owns all Thing and Feature entities, persists them via event sourcing in MongoDB, and enforces authorization using the Policy referenced by each Thing's `policyId`." type="primary" %}

## Overview

The Things service manages the full lifecycle of [Thing](basic-thing.html) and [Feature](basic-feature.html) entities. It handles creation, modification, retrieval, and deletion, and it enforces access control on every operation.

## How it works

### Model

The service is built around two entities, `Thing` and `Feature`:

* [Thing model](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model)

### Signals

Other services communicate with the Things service through two signal types:

* [ThingCommands](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model/signals/commands/ThingCommand.java): Commands that the service processes (create, modify, retrieve, delete)
* [ThingEvents](https://github.com/eclipse-ditto/ditto/tree/master/things/model/src/main/java/org/eclipse/ditto/things/model/signals/events/ThingEvent.java): Events emitted when Thing or Feature entities change

### Persistence

The Things service uses [Pekko persistence](https://pekko.apache.org/docs/pekko/current/persistence.html?language=java) with [event sourcing](basic-signals.html#overview) to persist changes and restore [things](basic-thing.html).

### Enforcement

The service authorizes all [thing signals](#signals) using the [Policy](basic-policy.html) referenced by the Thing's `policyId`. You must have the appropriate permissions granted in the referenced policy to perform operations on a Thing.

## Further reading

* [Thing concept](basic-thing.html)
* [Feature concept](basic-feature.html)
* [Architecture Overview](architecture-overview.html)
* [Policies Service](architecture-services-policies.html)
