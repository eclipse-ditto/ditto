---
title: Connections - Announcement protocol specification
keywords: protocol, specification, announcement, connection, connections, connectivity
tags: [protocol]
permalink: protocol-specification-connections-announcement.html
---

{% include note.html content="The *topic path* of connection announcements contains a *_* (underscore) as *namespace* 
and no *channel* element. 
See the [specification](protocol-specification-connections.html#ditto-protocol-topic-structure-for-connections) for 
details. " %}

## Connection announcements

A connection announcement contains the announcement name as last part of the topic:
```
_/<connectionId>/connections/announcements/<announcement-name>
```

The Ditto Protocol representation of an `Announcement` is specified as follows:

{% include docson.html schema="jsonschema/protocol-announcement.json" %}

The following Connection announcements are currently supported:

### ConnectionOpenedAnnouncement

Announcement indicating that the connection is being opened.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `_/<connectionId>/connections/announcements/opened` |
| **path**  | `/`     |
| **value** |  `JsonObject` containing<br/>* `openedAt` timestamp (as ISO-8601 `string`)|

**Example:** [Announcement for connection opened](protocol-examples-connections-announcement-opened.html)

### ConnectionClosedAnnouncement

Announcement indicating that the connection is being closed gracefully.

| Field     | Value                   |
|-----------|-------------------------|
| **topic** | `_/<connectionId>/connections/announcements/closed` |
| **path**  | `/`     |
| **value** |  `JsonObject` containing<br/>* `closedAt` timestamp (as ISO-8601 `string`)|

**Example:** [Announcement for connection closed](protocol-examples-connections-announcement-closed.html)
