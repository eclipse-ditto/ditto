---
title: Protocol topic
keywords: topic, protocol, specification, twin, digital twin, channel, criterion, action
tags: [protocol]
permalink: protocol-specification-topic.html
---

The Ditto Protocol defines a **Topic** for each Protocol message having following structure:

_[{namespace}](#namespace)/[{entity-name}](#entity-name)/[{group}](#group)/[{channel}](#channel)/[{criterion}](#criterion)/[{action}](#action-optional)_

Examples for valid topic paths are:
* `org.eclipse.ditto/fancy-car-1/things/twin/commands/create`
* `org.eclipse.ditto/fancy-car-23/things/twin/commands/merge`
* `org.eclipse.ditto/fancy-car-0815/things/live/events/modified`
* `org.eclipse.ditto/fancy-car-23/things/twin/search`
* `org.eclipse.ditto/fancy-car-42/things/live/messages/hello.world`
* `org.eclipse.ditto/fancy-policy-1/policies/commands/create`
* `org.eclipse.ditto/fancy-policy-1/policies/commands/delete`
* `org.eclipse.ditto/fancy-policy-1/policies/announcements/subjectDeletion`

## Namespace

The entity's namespace in which the entity is located.

## Entity Name

The entity's name (e.g. a `Thing Name`) to address.

## Group

The `{group}` contains which type of entity should be referenced with the Protocol message.

### Things Group

Addressing the _things_ group in the topic path indicates that a `Thing` entity is targeted which means that the entity
identifier in the first two segments should be treated as `Thing ID`.

### Policies Group

Addressing the _policies_ group in the topic path indicates that a `Policy` entity is targeted which means that the 
entity identifier in the first two segments should be treated as `Policy ID`.

## Channel

The `{channel}` specifies whether the Protocol message is addressed to the *digital twin*, to the actual *live* device
or to none of both.

### Twin channel

When addressing the *twin* channel, the `{criterion}` (e.g. a **command**) together with the optional `{action}` is
applied to the digital representation, the **digital twin**, of a `Thing`.
Ditto enforces that **digital twins** can only be accessed in an [authorized way](basic-auth.html) and responds with 
an error if the required permissions are not met.
Addressing on the *twin* channel means addressing the backend representation of a `Thing` which might be outdated, but
roundtrips to actual devices are saved (e.g. a device in sleep mode must not be waken up in order to retrieve its last
reported state).

Protocol messages with the *search* `{criterion}` are only possible on the *twin* channel as the search is done on the 
server side representation of all digital twins. 

### Live channel

When addressing the *live* channel, the `{criterion}` (e.g. a **Command**) together with the optional `{action}` is
applied to the actual device.
Ditto also enforces for the *live* channel that Protocol messages are [authorized](basic-auth.html) and blocks
unauthorized access to a `Thing` by responding with an error.

Protocol messages with the *messages* `{criterion}` are only possible on the *live* channel as Ditto only acts as a
broker of connected actual devices.

### No channel

Some commands (e.g. Policy commands) are not related to an actual device and thus have no associated twin. 
For these commands the *twin*/*live* semantics does not fit and consequently they have no channel assigned in the
 *Topic* of the Ditto Protocol message.
 
For example a *CreatePolicy* command has the following *Topic*: `<namespace>/<policyName>/policies/commands/create`

## Criterion

The `{criterion}` segment contains the type of action of the Protocol message in the specified entity `{group}` and on
the defined `{channel}`.

### Commands criterion

*commands* are sent to Ditto in order to do something, either on the digital twin or on a real connected device.  
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
They contain a query string defining on which data to search in the population of all **digital twins**.
Ditto respects the [authorization](basic-auth.html) information while searching for the requested data and returns the
search result as paged list of search hits.

### Messages criterion

*messages* are always exchanged via the *live channel*.  
They carry a custom payload and can be answered by another, correlated message.

### Errors criterion

*errors* are returned for commands which could not be executed due to client errors or internal server errors.
They contain a *status integer* which reflects an HTTP status code with the same semantics as in HTTP.

### Acks criterion

[Commands](#commands-criterion) can specify a number of [acknowledgements](basic-acknowledgements.html) (ACKs) which 
have to be successfully fulfilled to regard the command as successfully executed.

*acks* can be returned in response to [events](#events-criterion) which have defined in their `headers`, that specific 
acknowledgement labels were required by the issuing command.  
Acks contain a *status integer* which reflects a status code with the same semantics as in HTTP, reflecting whether the
ack was successful (2xx status range) or not (4xx or 5xx status range).  
Acks contain *headers* which include at least the `correlation-id` of the command/event to acknowledge, and optionally 
contain a custom *payload*. 

### Announcement criterion

*announcements* are published by Ditto prior to an *event* taking place.  
They are created by Ditto and are e.g. published a configured amount of time before an event will likely happen.


## Action (optional)

For command, event, and messages criteria, additional actions are available, which further distinguish the purpose of 
a Protocol message. 

### Command criterion actions

Requests to

* `create`,
* `retrieve`,
* `modify`,
* `merge` or
* `delete`

an entity or an aspect of an entity.

### Event criterion actions

An entity (e.g. a Thing) or an aspect of an entity was

* `created`,
* `modified`,
* `merged` or
* `deleted`.

### Messages criterion actions

For the *messages* criterion, the *action* segment specifies the message subject, and can be freely chosen by 
the sender, provided that it conforms to [RFC-3986](https://tools.ietf.org/html/rfc3986) (URI).

### Search criterion actions

The action of a command or an event of the [search protocol](protocol-specification-things-search.html) is
* `subscribe`,
* `request`,
* `cancel`,
* `created`,
* `next`,
* `complete`, or
* `failed`.

### Acknowledgement criterion actions

For *acks* criterion, the *action* segment specifies the identifier, which is defined by the system which issued the ACK.
The criterion has to match the regular expression `[a-zA-Z0-9-_:]{3,100}`, i.e. letters of the Latin alphabet, numbers,
dashes, and underscores.

### Announcement criterion actions

For the *announcement* criterion, the *action* segment specifies the announcement name.
