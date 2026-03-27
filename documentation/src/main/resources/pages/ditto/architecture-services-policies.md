---
title: Policies service
keywords: architecture, service, policies
tags: [architecture]
permalink: architecture-services-policies.html
---

The Policies service persists and enforces [Policies](basic-policy.html), which control access to all resources in Ditto.

{% include callout.html content="**TL;DR**: The Policies service owns all Policy entities, persists them via event sourcing in MongoDB, and enforces authorization on policy-related commands using the policy itself." type="primary" %}

## Overview

The Policies service is responsible for the complete lifecycle of [Policy](basic-policy.html) entities -- creation, modification, retrieval, and deletion. Every authorization decision for policy-level operations flows through this service.

## How it works

### Model

The service is built around the `Policy` entity:

* [Policy model](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model)

### Signals

Other services communicate with the Policies service through two signal types:

* [PolicyCommands](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model/signals/commands/PolicyCommand.java): Commands that the service processes (create, modify, retrieve, delete)
* [PolicyEvents](https://github.com/eclipse-ditto/ditto/tree/master/policies/model/src/main/java/org/eclipse/ditto/policies/model/signals/events/PolicyEvent.java): Events emitted when policy entities change

### Persistence

The Policies service uses [Pekko persistence](https://pekko.apache.org/docs/pekko/current/persistence.html?language=java) with [event sourcing](basic-signals.html#architectural-style) to persist changes and restore [policies](basic-policy.html).

### Enforcement

The service authorizes all [policy signals](#signals) using the policy's own rules. In other words, you need the right permissions in a policy to modify that policy.

## Further reading

* [Policy concept](basic-policy.html)
* [Architecture Overview](architecture-overview.html)
* [Things Service](architecture-services-things.html)
