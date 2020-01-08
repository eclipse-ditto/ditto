---
title: Placeholders
keywords: placeholder, function, expression, substring, default, lower, upper
tags: [connectivity]
permalink: basic-placeholders.html
---

## Placeholders

Placeholders may be used at several places in Ditto where something should be resolved by a variable.<br/>
The general syntax of a placeholder is `{% raw %}{{ prefix:name }}{% endraw %}`.
Which placeholder values are available depends on the context where the placeholder is used. 

### Scope: Entity creation / modification

Whenever creating or modifying [things](basic-thing.html) or [policies](basic-policy.html), the following placeholders
may be used:

| Placeholder    | Description  |
|----------------|--------------|
| `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which sent the command / did the request | 

### Scope: Connections

In [connections](basic-connections.html), the following placeholders are available in general:

| Placeholder                       | Description  |
|-----------------------------------|--------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | full ID composed of ''namespace'' + '':'' as a separator + ''name''  | 
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (i.e. second part of an ID ) |
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | external header value for connection sources, or Ditto protocol header value for targets and reply-targets |
| `{%raw%}{{ request:subjectId }}{%endraw%}` | primary authorization subject of a command, or primary authorization subject that caused an event |
| `{%raw%}{{ topic:full }}{%endraw%}` | full [Ditto Protocol topic path](protocol-specification-topic.html)<br/>in the form `{namespace}/{entityId}/{group}/`<br/>`{channel}/{criterion}/{action-subject}` |
| `{%raw%}{{ topic:namespace }}{%endraw%}` | Ditto Protocol [Namespace](protocol-specification-topic.html#namespace) |
| `{%raw%}{{ topic:entityId }}{%endraw%}` | Ditto Protocol [Entity ID](protocol-specification-topic.html#entity-id) |
| `{%raw%}{{ topic:group }}{%endraw%}` | Ditto Protocol [Group](protocol-specification-topic.html#group) |
| `{%raw%}{{ topic:channel }}{%endraw%}` | Ditto Protocol [Channel](protocol-specification-topic.html#channel) |
| `{%raw%}{{ topic:criterion }}{%endraw%}` | Ditto Protocol [Criterion](protocol-specification-topic.html#criterion) |
| `{%raw%}{{ topic:action }}{%endraw%}` | Ditto Protocol [Action](protocol-specification-topic.html#action-optional) |
| `{%raw%}{{ topic:subject }}{%endraw%}` | Ditto Protocol [Subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |
| `{%raw%}{{ topic:action-subject }}{%endraw%}` | either Ditto Protocol [Action](protocol-specification-topic.html#action-optional) or [Subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |


#### Examples

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
| `topic:action-subject` | _create_ |

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
| `topic:action-subject` | _hello.world_ |


## Function expressions

Whenever placeholders can be used (e.g. for [connections](basic-connections.html#placeholders)), function expressions 
may additionally be specified.

The syntax of such function expressions are specified similar to a UNIX `pipe`, e.g.:
```
{%raw%}{{ thing:name | fn:substring-before('-') | fn:default('fallback') | fn:upper() }}{%endraw%}
```

The first expression in such a pipeline **must always** be a placeholder to start with, in the example above `thing:name`.<br/>
Followed are functions separated by the pipe (`|`) symbol - each function in the pipeline receives the value of the
previous expression (which may also be `empty`). 

The function either contains no parameters or contains parameters which are either string constants or could also be placeholders again.

### Function library

The following functions are provided by Ditto out of the box:

| Name          | Signature                      | Description  | Examples |
|---------------|--------------------------------|--------------|----------|
| `fn:default`  | `(String defaultValue)` | Provides the passed `defaultValue` when the previous expression in a pipeline resolved to `empty` (e.g. due to a non-defined `header` placeholder key).<br/>Another placeholder may be specified which is resolved to a String and inserted as `defaultValue`. | `fn:default('fallback')`<br/>`fn:default("fallback")`<br/>`fn:default(thing:id)` |
| `fn:substring-before` | `(String givenString)` | Parses the result of the previous expression and passes along only the characters _before_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`. | `fn:substring-before(':')`<br/>`fn:substring-before(":")` |
| `fn:substring-after`  | `(String givenString)` | Parses the result of the previous expression and passes along only the characters _after_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`. | `fn:substring-after(':')`<br/>`fn:substring-after(":")` |
| `fn:lower`    | `()` | Makes the String result of the previous expression lowercase in total. | `fn:lower()` |
| `fn:upper`    | `()` | Makes the String result of the previous expression uppercase in total. | `fn:upper()` |
