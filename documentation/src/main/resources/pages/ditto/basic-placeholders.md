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

| Placeholder    | Description                                                                |
|----------------|----------------------------------------------------------------------------|
| `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which sent the command / did the request |
| `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone         | 
| `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string    |

### Scope: Policy actions

In [policy actions](basic-policy.html#actions), the following placeholders are available in general:

| Placeholder                       | Description                                                                                                |
|-----------------------------------|------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | HTTP header values passed along the HTTP action request                                                    |
| `{%raw%}{{ jwt:<jwt-body-claim> }}{%endraw%}` | any standard or custom claims in the body of the authenticated JWT - e.g., `jwt:sub` for the JWT "subject" |
| `{%raw%}{{ policy-entry:label }}{%endraw%}` | label of the policy entry in which the token integration subject is injected                               |
| `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                        | 
| `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                    | 

### Scope: RQL expressions when filtering for Ditto Protocol messages

When using [RQL expressions](basic-rql.html) in scope of either [change notifications](basic-changenotifications.html)
or subscriptions for live messages, the following placeholders are available in general:

| Placeholder                       | Description                                                                                                                                                                                     |
|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `topic:full` | full [Ditto Protocol topic path](protocol-specification-topic.html)<br/>in the form `{namespace}/{entityName}/{group}/`<br/>`{channel}/{criterion}/{action-subject}`                            |
| `topic:namespace` | Ditto Protocol [topic namespace](protocol-specification-topic.html#namespace)                                                                                                                   |
| `topic:entityName` | Ditto Protocol [topic entity name](protocol-specification-topic.html#entity-name)                                                                                                               |
| `topic:group` | Ditto Protocol [topic group](protocol-specification-topic.html#group)                                                                                                                           |
| `topic:channel` | Ditto Protocol [topic channel](protocol-specification-topic.html#channel)                                                                                                                       |
| `topic:criterion` | Ditto Protocol [topic criterion](protocol-specification-topic.html#criterion)                                                                                                                   |
| `topic:action` | Ditto Protocol [topic action](protocol-specification-topic.html#action-optional)                                                                                                                |
| `topic:subject` | Ditto Protocol [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands)                                                                             |
| `topic:action-subject` | either Ditto Protocol [topic action](protocol-specification-topic.html#action-optional) or [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |
| `resource:type` | the type of the Ditto Protocol [path](protocol-specification.html#path) , one of: `"thing" "policy" "message" "connection"`                                                                     |
| `resource:path` | the affected resource's path being the Ditto Protocol [path](protocol-specification.html#path) in JsonPointer notation, e.g. `/` when a complete thing was created/modified/deleted             |
| `time:now` | the current timestamp in ISO-8601 format as string in UTC timezone                                                                                                                              | 
| `time:now_epoch_millis` | the current timestamp in "milliseconds since epoch" formatted as string                                                                                                                         | 

### Scope: Connections

In [connections](basic-connections.html), the following placeholders are available in general:

| Placeholder                       | Description                                                                                                                                                                                     |
|-----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ entity:id }}{%endraw%}` | full ID composed of ''namespace'' + '':'' as a separator + ''name'' for things and policies                                                                                                     | 
| `{%raw%}{{ entity:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) for things and policies                                                                                                                                    |
| `{%raw%}{{ entity:name }}{%endraw%}` | Name (i.e. second part of an ID ) for things and policies                                                                                                                                       |
| `{%raw%}{{ thing:id }}{%endraw%}` | full ID composed of ''namespace'' + '':'' as a separator + ''name''                                                                                                                             | 
| `{%raw%}{{ thing:namespace }}{%endraw%}` | the namespace (i.e. first part of an ID) of the related thing                                                                                                                                   |
| `{%raw%}{{ thing:name }}{%endraw%}` | the name (i.e. second part of an ID ) of the related thing                                                                                                                                      |
| `{%raw%}{{ feature:id }}{%endraw%}` | the ID of the feature (only available if the processed signal was related to a feature)                                                                                                         |
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | external header value for connection sources, or Ditto protocol header value for targets and reply-targets (both case-insensitive)                                                              |
| `{%raw%}{{ request:subjectId }}{%endraw%}` | primary authorization subject of a command, or primary authorization subject that caused an event                                                                                               |
| `{%raw%}{{ topic:full }}{%endraw%}` | full [Ditto Protocol topic path](protocol-specification-topic.html)<br/>in the form `{namespace}/{entityName}/{group}/`<br/>`{channel}/{criterion}/{action-subject}`                            |
| `{%raw%}{{ topic:namespace }}{%endraw%}` | Ditto Protocol [topic namespace](protocol-specification-topic.html#namespace)                                                                                                                   |
| `{%raw%}{{ topic:entityName }}{%endraw%}` | Ditto Protocol [topic entity name](protocol-specification-topic.html#entity-name)                                                                                                               |
| `{%raw%}{{ topic:group }}{%endraw%}` | Ditto Protocol [topic group](protocol-specification-topic.html#group)                                                                                                                           |
| `{%raw%}{{ topic:channel }}{%endraw%}` | Ditto Protocol [topic channel](protocol-specification-topic.html#channel)                                                                                                                       |
| `{%raw%}{{ topic:criterion }}{%endraw%}` | Ditto Protocol [topic criterion](protocol-specification-topic.html#criterion)                                                                                                                   |
| `{%raw%}{{ topic:action }}{%endraw%}` | Ditto Protocol [topic action](protocol-specification-topic.html#action-optional)                                                                                                                |
| `{%raw%}{{ topic:subject }}{%endraw%}` | Ditto Protocol [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands)                                                                             |
| `{%raw%}{{ topic:action-subject }}{%endraw%}` | either Ditto Protocol [topic action](protocol-specification-topic.html#action-optional) or [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |
| `{%raw%}{{ resource:type }}{%endraw%}` | the type of the Ditto Protocol [path](protocol-specification.html#path) , one of: `thing`, `policy`, `message` or `connection`                                                                  |
| `{%raw%}{{ resource:path }}{%endraw%}` | the affected resource's path being the Ditto Protocol [path](protocol-specification.html#path) in JsonPointer notation, e.g. `/` when a complete thing was created/modified/deleted             |
| `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                                                                                              | 
| `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                                                                                                         | 


#### Examples

For a topic path with the intention of [creating a Thing](protocol-examples-creatething.html) 
_org.eclipse.ditto/device-123/things/twin/commands/create_ these placeholders would be resolved as follows:

| Placeholder | Resolved value |
|-------------|----------------|
| `topic:full` | _org.eclipse.ditto/device-123/things/twin/commands/create_ |
| `topic:namespace` | _org.eclipse.ditto_ |
| `topic:entityName` | _device-123_ |
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
| `topic:entityName` | _device-123_ |
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

The first expression in such a pipeline **must always** be a placeholder to start with, in the example above `thing:name`.  
Followed are functions separated by the pipe (`|`) symbol - each function in the pipeline receives the value of the
previous expression (which may also be `empty`). 

The function either contains no parameters or contains parameters which are either string constants or could also 
be placeholders again.

### Function library

The following functions are provided by Ditto out of the box:

| Name          | Signature                      | Description  | Examples |
|---------------|--------------------------------|--------------|----------|
| `fn:filter`   | `(String filterValue, String rqlFunction, String comparedValue)`| Removes the result of the previous expression in the pipeline unless the condition specified by the parameters is satisfied. | `fn:filter(header:response-required,'eq','true')` <br/> `fn:filter(header:response-required,'exists')` <br/> `fn:filter(header:response-required,'exists','false')` |
| `fn:default`  | `(String defaultValue)` | Provides the passed `defaultValue` when the previous expression in a pipeline resolved to `empty` (e.g. due to a non-defined `header` placeholder key).<br/>Another placeholder may be specified which is resolved to a String and inserted as `defaultValue`. | `fn:default('fallback')`<br/>`fn:default("fallback")`<br/>`fn:default(thing:id)` |
| `fn:substring-before` | `(String givenString)` | Parses the result of the previous expression and passes along only the characters _before_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`. | `fn:substring-before(':')`<br/>`fn:substring-before(":")` |
| `fn:substring-after`  | `(String givenString)` | Parses the result of the previous expression and passes along only the characters _after_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`. | `fn:substring-after(':')`<br/>`fn:substring-after(":")` |
| `fn:lower`    | `()` | Makes the String result of the previous expression lowercase in total. | `fn:lower()` |
| `fn:upper`    | `()` | Makes the String result of the previous expression uppercase in total. | `fn:upper()` |
| `fn:replace`  | `(String from, String to)`     | Replaces a string with another using Java's `String::replace` method. | `fn:replace('foo', 'bar')` |
