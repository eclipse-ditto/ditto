---
title: Release notes x.x.x
tags: [release_notes]
published: true
keywords: release notes, announcements, changelog
summary: "Version x.x.x of Eclipse Ditto, released on dd.MM.yyyy"
permalink: release_notes_next.html
---

## Changelog


### Changes

#### [Removed content-type header mapping for connection targets](https://github.com/eclipse/ditto/pull/934)

Removed the default header mapping of `content-type` for new connection targets. The header mapping led to irritating
results, when payload mapping and header mapping disagreed on the actual `content-type`. Existing connections will still
keep the "old" default and map the `content-type` header.

### New features

#### [Merge updates](https://github.com/eclipse/ditto/issues/288)

This new feature allows updating parts of a thing without affecting existing parts. You may now for example update an
attribute, add a new property to a feature and delete a property of a different feature in a _single request_. The new
merge functionality is available via the HTTP API and the all channels using the Ditto Protocol. See
[Merge updates via HTTP](httpapi-concepts.html#merge-updates)
or the [Merge protocol specification](protocol-specification-things-merge.html) for more details and examples.

### Bugfixes

## Migration notes

### content-type header mapping in connection targets

Due to
the [removed default content-type header mapping for connection targets](https://github.com/eclipse/ditto/pull/934), it
might be necessary to update the way connection targets are created in case you create connection targets without
explicit
`headerMapping` and rely on a specific content-type on the receiving side. The request to create connection targets
can be updated to contain the "old" default in this case:
```json
{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:createConnection",
            "connection": {
              "targets":[{
                "headerMapping": {
                  "content-type": "{%raw%}{{header:content-type}}{%endraw%}",
                  "correlation-id": "{%raw%}{{header:correlation-id}}{%endraw%}",
                  "reply-to": "{%raw%}{{header:reply-to}}{%endraw%}"
                },
                // ...
              }]
              // ...
            }
    }
}
```
