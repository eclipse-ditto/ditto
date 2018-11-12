---
title: Header mapping for connections
keywords: header, mapping
tags: [connectivity]
permalink: connectivity-header-mapping.html
---

When receiving messages from external systems or sending messages to external systems, the protocol headers of the 
messages can be mapped to and from internal DittoHeaders.

That way the headers can be passed through Ditto or defined DittoHeaders like for example `correlation-id` may be 
mapped to a header used for message correlation in the external system.

## Supported placeholders

The already defined [placeholders of connections](basic-connections.html#placeholders) are supported inside header
mappings combined with the header mapping specific [topic placeholder](#topic-placeholders).

In total, the following placeholders are available during header mapping:

| Placeholder    | Description  |
|----------------|--------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | full ID composed of ''namespace'' + '':'' as a separator + ''name''  | 
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (i.e. second part of an ID ) |
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | any external protocol header |
| `{%raw%}{{ topic:full }}{%endraw%}` | full [Ditto Protocol topic path](protocol-specification-topic.html) |
| `{%raw%}{{ topic:namespace }}{%endraw%}` | Ditto Protocol [Namespace](protocol-specification-topic.html#namespace) |
| `{%raw%}{{ topic:entityId }}{%endraw%}` | Ditto Protocol [Entity ID](protocol-specification-topic.html#entity-id) |
| `{%raw%}{{ topic:group }}{%endraw%}` | Ditto Protocol [Group](protocol-specification-topic.html#group) |
| `{%raw%}{{ topic:channel }}{%endraw%}` | Ditto Protocol [Channel](protocol-specification-topic.html#channel) |
| `{%raw%}{{ topic:criterion }}{%endraw%}` | Ditto Protocol [Criterion](protocol-specification-topic.html#criterion) |
| `{%raw%}{{ topic:action }}{%endraw%}` | Ditto Protocol [Action](protocol-specification-topic.html#action-optional) |
| `{%raw%}{{ topic:subject }}{%endraw%}` | Ditto Protocol [Subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |
| `{%raw%}{{ topic:action|subject }}{%endraw%}` | either Ditto Protocol [Action](protocol-specification-topic.html#action-optional) or [Subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |


## Topic placeholders

Only inside the header mapping, the [Ditto Protocol topic](protocol-specification-topic.html) may be accessed via the 
`topic:` placeholders:

* `{% raw %}{{ topic:full }}{% endraw %}`: full path in the form `{namespace}/{entityId}/{group}/{channel}/{criterion}/{action|subject}`
* `{% raw %}{{ topic:namespace }}{% endraw %}`: [Namespace](protocol-specification-topic.html#namespace) part
* `{% raw %}{{ topic:entityId }}{% endraw %}`: [Entity ID](protocol-specification-topic.html#entity-id) part
* `{% raw %}{{ topic:group }}{% endraw %}`: [Group](protocol-specification-topic.html#group) part
* `{% raw %}{{ topic:channel }}{% endraw %}`: [Channel](protocol-specification-topic.html#channel) part
* `{% raw %}{{ topic:criterion }}{% endraw %}`: [Criterion](protocol-specification-topic.html#criterion) part
* `{% raw %}{{ topic:action }}{% endraw %}`: [Action](protocol-specification-topic.html#action-optional) part
* `{% raw %}{{ topic:subject }}{% endraw %}`: [Subject](protocol-specification-topic.html#messages-criterion-actions) part (for message commands)
* `{% raw %}{{ topic:action|subject }}{% endraw %}`: either [Action](protocol-specification-topic.html#action-optional) or [Subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands)

For a topic path with the intention of [creating a Thing](protocol-examples-creatething.html) 
_org.eclipse.ditto/device-123/things/twin/commands/create_ these placeholders would be resolved as follows:

| Placeholder | Resolved value |
|-------------|----------------|
| `topic:full` | _org.eclipse.ditto/device-123/things/twin/commands/create_ |
| `topic:namespace` | _org.eclipse.ditto_ |
| `topic:entityId` | _device-123_ |
| `topic:group` | _things_ |
| `topic:channel` | _twin_ |
| `topic:criterion` | _commands_ |
| `topic:action` | _create_ |
| `topic:subject` | &#10060; |
| `topic:action|subject` | _create_ |

For a topic path with the intention of [sending a message to a Thing](protocol-specification-things-messages.html#sending-a-message-to-a-thing) 
_org.eclipse.ditto/device-123/things/live/messages/hello.world_ these placeholders are resolved as follows:

| Placeholder | Resolved value |
|-------------|----------------|
| `topic:full`  | _org.eclipse.ditto/device-123/things/live/messages/hello.world_ |
| `topic:namespace` | _org.eclipse.ditto_ |
| `topic:entityId` | _device-123_ |
| `topic:group` | _things_ |
| `topic:channel` | _live_ |
| `topic:criterion` | _messages_ |
| `topic:action` | &#10060; |
| `topic:subject` | _hello.world_ |
| `topic:action|subject` | _hello.world_ |
