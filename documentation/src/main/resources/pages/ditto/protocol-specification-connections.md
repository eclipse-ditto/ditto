---
title: Protocol specification for connections
keywords: protocol, specification, general, connection, connections, connectivity
tags: [protocol]
permalink: protocol-specification-connections.html
---


## Ditto Protocol topic structure for connections

A valid topic consists of four elements, describing the connection affected by this message and the type of the message:

```
_/<connectionId>/connections/announcements/<subject>
```

1. `_`: empty field.
2. `connectionId`: the ID of the connection.
3. `group`: the group for addressing connections is `connections`.
4. `criterion`: the type of protocol messages addressing connection [announcements](basic-signals-announcement.html) is 
   [`announcements`](protocol-specification-policies-announcement.html).
5. `subject`: for [announcements](basic-signals-announcement.html) the `subject` contains the announcement name
       
{% include note.html content="The topic path of the *connections* group does not contain a namespace since there is 
no correlation between namespaces and connections. Also there is no channel unlike the *things* group." %}
