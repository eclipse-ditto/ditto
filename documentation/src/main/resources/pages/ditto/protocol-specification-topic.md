---
title: Protocol topic
keywords: topic, protocol, specification, twin, digital twin, channel, criterion, action
tags: [protocol]
permalink: protocol-specification-topic.html
---

Every Ditto Protocol message includes a **topic** string that identifies the target entity, the communication channel, and the intent of the message.

{% include callout.html content="**TL;DR**: The topic path determines routing and authorization — Ditto uses it to resolve the target entity, select twin or live processing, and dispatch the correct command handler." type="primary" %}

## Overview

The topic path has this structure:

_[{namespace}](#namespace)/[{entity-name}](#entity-name)/[{group}](#group)/[{channel}](#channel)/[{criterion}](#criterion)/[{action}](#action-optional)_

Examples of valid topic paths:

* `org.eclipse.ditto/fancy-car-1/things/twin/commands/create`
* `org.eclipse.ditto/fancy-car-23/things/twin/commands/merge`
* `org.eclipse.ditto/fancy-car-0815/things/live/events/modified`
* `org.eclipse.ditto/fancy-car-23/things/twin/search`
* `org.eclipse.ditto/fancy-car-42/things/live/messages/hello.world`
* `org.eclipse.ditto/fancy-policy-1/policies/commands/create`
* `org.eclipse.ditto/fancy-policy-1/policies/commands/delete`
* `org.eclipse.ditto/fancy-policy-1/policies/announcements/subjectDeletion`

## Namespace

The entity's namespace (e.g., `org.eclipse.ditto`).

## Entity Name

The entity's name (e.g., a `Thing Name`) within the namespace.

## Group

The `{group}` segment identifies the entity type targeted by the message.

### Things Group

Use the `things` group to target a `Thing` entity. The namespace and entity name segments form the `Thing ID`.

### Policies Group

Use the `policies` group to target a `Policy` entity. The namespace and entity name segments form the `Policy ID`.

## Channel

The `{channel}` segment specifies whether the message addresses the digital twin, the live device, or neither.

### Twin channel

The **twin** channel applies the criterion and action to the server-side digital twin of a `Thing`. Ditto enforces [authorization](basic-auth.html) and returns an error if required permissions are missing.

Use the twin channel when you want to interact with the persisted representation. This avoids round-trips to the device (e.g., a sleeping sensor does not need to be woken up).

Protocol messages with the **search** criterion only work on the twin channel, because Ditto searches across server-side digital twins.

### Live channel

The **live** channel applies the criterion and action to the actual device. Ditto also enforces [authorization](basic-auth.html) on live channel messages.

Protocol messages with the **messages** criterion only work on the live channel, because Ditto acts as a broker between connected devices and applications.

### No channel

Some commands (e.g., Policy commands) are not related to a device and have no associated twin. These commands omit the channel from the topic path.

For example, a CreatePolicy command has this topic: `<namespace>/<policyName>/policies/commands/create`

## Criterion

The `{criterion}` segment describes what type of action the message performs.

### Commands criterion

**commands** tell Ditto to do something -- either on the digital twin or on a connected device. Commands are split into:

- **ModifyCommands**: create, modify, merge, delete
- **QueryCommands**: retrieve

For each command it processes, Ditto creates a command response indicating success or failure. The response uses the same topic path as the command.

### Events criterion

**events** are emitted by Ditto when a command successfully modifies an entity. Each ModifyCommand triggers a specific event type that subscribers can receive.

### Search criterion

**search** requests work only on the twin channel. They contain a query string that searches across all digital twins. Ditto respects [authorization](basic-auth.html) and returns paginated results.

### Messages criterion

**messages** are exchanged only via the live channel. They carry custom payloads and can be answered by correlated response messages.

### Errors criterion

**errors** are returned when a command fails due to a client error or server error. They include an HTTP-semantics status code.

### Acks criterion

**acks** (acknowledgements) can be sent in response to events that requested specific [acknowledgement labels](basic-acknowledgements.html). Each ack includes:

- A status code (HTTP semantics: 2xx for success, 4xx/5xx for failure)
- Headers including the `correlation-id` of the original command/event
- An optional custom payload

### Announcement criterion

**announcements** are published by Ditto before an event takes place -- for example, a configured amount of time before a policy subject expires.

## Action (optional)

For commands, events, and messages, the `{action}` segment further specifies the message's purpose.

### Command criterion actions

- `create` -- create an entity or sub-resource
- `retrieve` -- read an entity or sub-resource
- `modify` -- replace an entity or sub-resource
- `merge` -- partially update using JSON Merge Patch
- `delete` -- remove an entity or sub-resource

### Event criterion actions

- `created` -- an entity or sub-resource was created
- `modified` -- an entity or sub-resource was replaced
- `merged` -- an entity or sub-resource was partially updated
- `deleted` -- an entity or sub-resource was removed

### Messages criterion actions

For the **messages** criterion, the action is the message subject. You choose it freely, provided it conforms to [RFC-3986](https://tools.ietf.org/html/rfc3986) (URI).

### Search criterion actions

Search protocol actions:
* `subscribe`, `request`, `cancel` -- client-to-Ditto commands
* `created`, `next`, `complete`, `failed` -- Ditto-to-client events

See [Search protocol specification](protocol-specification-things-search.html) for details.

### Acknowledgement criterion actions

For the **acks** criterion, the action is the acknowledgement label. It must match the regular expression `[a-zA-Z0-9-_:]{3,100}`.

### Announcement criterion actions

For the **announcement** criterion, the action is the announcement name.

## Further reading

- [Protocol specification](protocol-specification.html) -- the full envelope format
- [Twin and live channels](protocol-twinlive.html) -- channel semantics
- [Things specification](protocol-specification-things.html) -- Thing-specific topic paths
- [Policies specification](protocol-specification-policies.html) -- Policy-specific topic paths
