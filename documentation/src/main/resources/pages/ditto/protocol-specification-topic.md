---
title: Protocol topic
keywords: topic, protocol, specification, twin, digital twin, channel, criterion, action
tags: [protocol]
permalink: protocol-specification-topic.html
---

The Ditto Protocol defines a **Topic** for each Protocol message having following structure:

_[{namespace}](#namespace)/[{entityId}](#entity-id)/[{group}](#group)/[{channel}](#channel)/[{criterion}](#criterion)/[{action}](#action-optional)_

Examples for valid topic paths are:
* `org.eclipse.ditto/fancy-car-1/things/twin/commands/create`
* `org.eclipse.ditto/fancy-car-0815/things/live/events/modified`
* `org.eclipse.ditto/fancy-car-23/things/twin/search`
* `org.eclipse.ditto/fancy-car-42/things/live/messages/hello.world`


## Namespace

The entity's namespace in which the entity is located.

## Entity ID

The entity's identifier (e.g. a `Thing ID`) to address.

## Group

The `{group}` contains which type of entity should be referenced with the Protocol message.

### Things Group

Addressing the _things_ group in the topic path indicates that a `Thing` entity is targeted which means that the entity
identifier in the first two segments should be treated as `Thing ID`.


## Channel

The `{channel}` specifies whether the Protocol message is addressed to the *digital twin* or to the actual *live* device.  

### Twin channel

When addressing the *twin* channel, the `{criterion}` (e.g. a **command**) together with the optional `{action}` is
applied to the digital representation, the **Digital Twin**, of a `Thing`.
Ditto enforces that **Digital Twins** can only be accessed in an [authorized way](basic-auth.html) and responds with an 
error if the required permissions are not met.
Addressing on the *twin* channel means addressing the backend representation of a `Thing` which might be outdated, but
roundtrips to actual devices are saved (e.g. a device in sleep mode must not be waken up in order to retrieve its last
reported state).

Protocol messages with the *search* `{criterion}` are only possible on the *twin* channel as the search is done on the 
server side representation of all Digital Twins. 

### Live channel

When addressing the *live* channel, the `{criterion}` (e.g. a **Command**) together with the optional `{action}` is
applied to the actual device.
Ditto also enforces for the *live* channel that Protocol messages are [authorized](basic-auth.html) and blocks
unauthorized access to a `Thing` by responding with an error.

Protocol messages with the *messages* `{criterion}` are only possible on the *live* channel as Ditto only acts as a
broker of connected actual devices.


## Criterion

The `{criterion}` segment contains the type of action of the Protocol message in the specified entity `{group}` and on
the defined `{channel}`.

### Commands criterion

*commands* are sent to Ditto in order to do something, either on the Digital Twin or on a real connected device.
They are separated in ModifyCommands for creating, modifying, deleting and QueryCommands for retrieving.

For each command Ditto processed a command response is created.
This command response indicates whether the command was successfully applied or if an error occurred while 
processing the command.
Command responses have the same topic path as the commands which they answer to.

### Events criterion

*events* are emitted by Ditto for each command which successfully *modified* an entity.
Each ModifyCommand causes a specific Event type to be published for which interested parties can subscribe themselves.

### Search criterion

*search* requests can only be put on the *twin channel*.
They contain a query string defining on which data to search in the population of all **Digital Twins**.
Ditto respects the [authorization](basic-auth.html) information while searching for the requested data and returns the
search result as paged list of search hits.

### Messages criterion

*messages* are always exchanged via the *live channel*.
They carry a custom payload and can be answered by another, correlated message.

### Errors criterion

*errors* are returned for commands which could not be executed due to client errors or internal server errors.
They contain a *status integer* which reflects an HTTP status code with the same semantics as in HTTP.


## Action (optional)

For command, event and messages criteria additional actions are available which further distinguish the purpose of a 
Protocol message. 

### Command criterion actions

Requests to

* `create`,
* `retrieve`,
* `modify` or
* `delete`

an entity or an aspect of an entity.

### Event criterion actions

An entity (e.g. a Thing) or an aspect of an entity was

* `created`,
* `modified` or
* `deleted`.

### Messages criterion actions

For the *messages* criterion the *action* segment specifies the message subject and can be freely chosen by the sender 
provided that it conforms to [RFC-2396](https://tools.ietf.org/html/rfc2396) (URI).