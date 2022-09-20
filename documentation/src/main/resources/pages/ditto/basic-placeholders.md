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

### Entity Placeholder

| Placeholder                               | Description                                                                                 |
|-------------------------------------------|---------------------------------------------------------------------------------------------|
| `{%raw%}{{ entity:id }}{%endraw%}`        | full ID composed of ''namespace'' + '':'' as a separator + ''name'' for things and policies | 
| `{%raw%}{{ entity:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) for things and policies                                |
| `{%raw%}{{ entity:name }}{%endraw%}`      | Name (i.e. second part of an ID ) for things and policies                                   |

### Thing Placeholder

| Placeholder                              | Description                                                         |
|------------------------------------------|---------------------------------------------------------------------|
| `{%raw%}{{ thing:id }}{%endraw%}`        | full ID composed of ''namespace'' + '':'' as a separator + ''name'' |
| `{%raw%}{{ thing:namespace }}{%endraw%}` | the namespace (i.e. first part of an ID) of the related thing       |
| `{%raw%}{{ thing:name }}{%endraw%}`      | the name (i.e. second part of an ID ) of the related thing          |

### Feature Placeholder

| Placeholder                         | Description                                                                             |
|-------------------------------------|-----------------------------------------------------------------------------------------|
| `{%raw%}{{ feature:id }}{%endraw%}` | the ID of the feature (only available if the processed signal was related to a feature) |

### Policy Placeholder

| Placeholder                               | Description                                                         |
|-------------------------------------------|---------------------------------------------------------------------|
| `{%raw%}{{ policy:id }}{%endraw%}`        | full ID composed of ''namespace'' + '':'' as a separator + ''name'' |
| `{%raw%}{{ policy:namespace }}{%endraw%}` | the namespace (i.e. first part of an ID) of the related policy      |
| `{%raw%}{{ policy:name }}{%endraw%}`      | the name (i.e. second part of an ID ) of the related policy         |

### Policy Entry Placeholder

| Placeholder                                 | Description                                                                  |
|---------------------------------------------|------------------------------------------------------------------------------|
| `{%raw%}{{ policy-entry:label }}{%endraw%}` | label of the policy entry in which the token integration subject is injected |

### Connection Placeholder

| Placeholder                            | Description                                                |
|----------------------------------------|------------------------------------------------------------|
| `{%raw%}{{ connection:id }}{%endraw%}` | the ID of the connection which receives/publishes a signal |

### Header Placeholder

| Placeholder                                   | Description                                                                                                                                                                              |
|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ header:<header-name> }}{%endraw%}` | Value of the header with the given name. For incoming signals the headers of the used protocol are used. For outgoing values the Ditto protocol headers are used.(both case-insensitive) |

### Topic Placeholder

| Placeholder                                   | Description                                                                                                                                                                                     |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ topic:full }}{%endraw%}`           | full [Ditto Protocol topic path](protocol-specification-topic.html)<br/>in the form `{namespace}/{entityName}/{group}/`<br/>`{channel}/{criterion}/{action-subject}`                            |
| `{%raw%}{{ topic:namespace }}{%endraw%}`      | Ditto Protocol [topic namespace](protocol-specification-topic.html#namespace)                                                                                                                   |
| `{%raw%}{{ topic:entityName }}{%endraw%}`     | Ditto Protocol [topic entity name](protocol-specification-topic.html#entity-name)                                                                                                               |
| `{%raw%}{{ topic:group }}{%endraw%}`          | Ditto Protocol [topic group](protocol-specification-topic.html#group)                                                                                                                           |
| `{%raw%}{{ topic:channel }}{%endraw%}`        | Ditto Protocol [topic channel](protocol-specification-topic.html#channel)                                                                                                                       |
| `{%raw%}{{ topic:criterion }}{%endraw%}`      | Ditto Protocol [topic criterion](protocol-specification-topic.html#criterion)                                                                                                                   |
| `{%raw%}{{ topic:action }}{%endraw%}`         | Ditto Protocol [topic action](protocol-specification-topic.html#action-optional)                                                                                                                |
| `{%raw%}{{ topic:subject }}{%endraw%}`        | Ditto Protocol [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands)                                                                             |
| `{%raw%}{{ topic:action-subject }}{%endraw%}` | either Ditto Protocol [topic action](protocol-specification-topic.html#action-optional) or [topic subject](protocol-specification-topic.html#messages-criterion-actions) (for message commands) |

### Resource Placeholder

| Placeholder                            | Description                                                                                                                                                                         |
|----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ resource:type }}{%endraw%}` | the type of the Ditto Protocol [path](protocol-specification.html#path) , one of: `"thing" "policy" "message" "connection"`                                                         |
| `{%raw%}{{ resource:path }}{%endraw%}` | the affected resource's path being the Ditto Protocol [path](protocol-specification.html#path) in JsonPointer notation, e.g. `/` when a complete thing was created/modified/deleted |

### Request Placeholder

| Placeholder                                | Description                                                                |
|--------------------------------------------|----------------------------------------------------------------------------|
| `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which sent the command / did the request |

### Time Placeholder

| Placeholder                                    | Description                                                             |
|------------------------------------------------|-------------------------------------------------------------------------|
| `{%raw%}{{ time:now }}{%endraw%}`              | the current timestamp in ISO-8601 format as string in UTC timezone      | 
| `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string |

### JWT Placeholder

| Placeholder                                  | Description                                                                                                                                                                                                                                                                                                                                                                                                            |
|----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{%raw%}{{ jwt:<claim-pointer> }}{%endraw%}` | Any standard or custom claims in the body of the authenticated JWT in JsonPointer notation - e.g., `jwt:sub` for the JWT "subject" or `jwt:extra/roles` for (potentially multiple) "roles" extracted from a JsonObject in the JWT named `extra`.<br/>JWT claims being JSON arrays are expanded (each value of the array is added) and provided [functions](#function-expressions) are performed on each array element. |


### Scope: Entity creation / modification

Whenever creating or modifying [things](basic-thing.html) or [policies](basic-policy.html), the following placeholders
may be used:
* [request placeholder](#request-placeholder)
* [time placeholder](#time-placeholder)


### Scope: OpenID Connect configuration

When configuring [OpenID connect](installation-operating.html#openid-connect) providers in a Ditto setup, the `jwt:`
placeholder can be used in order to extract certain claims from the
<a href="#" data-toggle="tooltip" data-original-title="{{site.data.glossary.jwt}}">JWT</a> in order to use them as
[authorization subjects](basic-policy.html#subjects) for Ditto's [policy based authorization](basic-policy.html).

The following placeholders may be used inside the configuration file:
* [JWT placeholder](#jwt-placeholder)

In combination with [functions](#function-expressions), use of this placeholder can provide and modify the subjects to
extract from a JWT in a very powerful way.  
The `fn:split(' ')` function may for example be used in order to further split the default claim "scope", which is by 
default a whitespace separated list of scopes, as an array, producing multiple entries for each provided scope.

**Example**:

Assuming the provided JWT looks like this:

```json
{
  "sub": "u123456789",
  "iss": "https://<the-issuer-domain>/",
  "exp": 1490922820,
  "iat": 1490886820,
  "email": "john.doe@eclipse.org",
  "aud": "client-id-0815",
  "scopes": "openid email",
  "extra": {
    "roles": [
      "administrator",
      "super-moderator",
      "moderator"
    ]
  }
}
```

Applied on the example JWT, the following `jwt:` placeholders in combination with [functions](#function-expressions) 
are resolved in the following way:

* `{%raw%}{{ jwt:sub }}{%endraw%}` - resolves to `"u123456789"`
* `{%raw%}{{ jwt:sub }}@{{ jwt:aud }}{%endraw%}` - resolves to `"u123456789@client-id-0815"`
* `{%raw%}{{ jwt:extra/roles }}{%endraw%}` - resolves to 3 entries: `"administrator"`, `"super-moderator"` and `"moderator"`
* `{%raw%}{{ jwt:scopes | fn:split(' ') }}{%endraw%}` - resolves to 2 entries: `"openid"` and `"email"`
* `{%raw%}{{ jwt:extra/roles | fn:filter('like','*moderator') }}@{{ jwt:aud }}{%endraw%}` - resolves to 2 entries: `"super-moderator@client-id-0815"` and `"moderator@client-id-0815"`




### Scope: Policy actions

In [policy actions](basic-policy.html#actions), the following placeholders are available in general:
* [header placeholder](#header-placeholder)
* [JWT placeholder](#jwt-placeholder)
* [Policy entry placeholder](#policy-entry-placeholder)
* [time placeholder](#time-placeholder)

### Scope: RQL expressions when filtering for Ditto Protocol messages

When using [RQL expressions](basic-rql.html) in scope of either [change notifications](basic-changenotifications.html)
or subscriptions for live messages, the following placeholders are available in general:
* [topic placeholder](#topic-placeholder)
* [resource placeholder](#resource-placeholder)
* [time placeholder](#time-placeholder)
Note that in RQL expressions placeholders must not be surrounded by curly braces.

### Scope: Websocket Signal Enrichment
When [declaring extra fields](basic-enrichment.html) which should be enriched to a published signal for a Websocket connection, the following placeholders are available in general:
* [entity placeholder](#entity-placeholder)
* [thing placeholder](#thing-placeholder)
* [policy placeholder](#policy-placeholder)
* [feature placeholder](#feature-placeholder)
* [header placeholder](#header-placeholder)
* [request placeholder](#request-placeholder)
* [resource placeholder](#resource-placeholder)
* [topic placeholder](#topic-placeholder)
* [time placeholder](#time-placeholder)

### Scope: SSE Signal Enrichment
When [declaring extra fields](basic-enrichment.html) which should be enriched to a published signal for a Websocket connection, the following placeholders are available in general:
* [entity placeholder](#entity-placeholder)
* [thing placeholder](#thing-placeholder)
* [policy placeholder](#policy-placeholder)
* [feature placeholder](#feature-placeholder)
* [header placeholder](#header-placeholder)
* [request placeholder](#request-placeholder)
* [resource placeholder](#resource-placeholder)
* [topic placeholder](#topic-placeholder)
* [time placeholder](#time-placeholder)

### Scope: Connections

In [connections](basic-connections.html), the following placeholders are available in general:
* [entity placeholder](#entity-placeholder)
* [thing placeholder](#thing-placeholder)
* [policy placeholder](#policy-placeholder)
* [connection placeholder](#connection-placeholder)
* [feature placeholder](#feature-placeholder)
* [header placeholder](#header-placeholder)
* [request placeholder](#request-placeholder)
* [resource placeholder](#resource-placeholder)
* [topic placeholder](#topic-placeholder)
* [time placeholder](#time-placeholder)

#### Examples

For a topic path with the intention of [creating a Thing](protocol-examples-creatething.html) 
_org.eclipse.ditto/device-123/things/twin/commands/create_ these placeholders would be resolved as follows:

| Placeholder            | Resolved value                                             |
|------------------------|------------------------------------------------------------|
| `topic:full`           | _org.eclipse.ditto/device-123/things/twin/commands/create_ |
| `topic:namespace`      | _org.eclipse.ditto_                                        |
| `topic:entityName`     | _device-123_                                               |
| `topic:group`          | _things_                                                   |
| `topic:channel`        | _twin_                                                     |
| `topic:criterion`      | _commands_                                                 |
| `topic:action`         | _create_                                                   |
| `topic:subject`        | &#10060;                                                   |
| `topic:action-subject` | _create_                                                   |

For a topic path with the intention of [sending a message to a Thing](protocol-specification-things-messages.html#sending-a-message-to-a-thing) 
_org.eclipse.ditto/device-123/things/live/messages/hello.world_ these placeholders are resolved as follows:

| Placeholder            | Resolved value                                                  |
|------------------------|-----------------------------------------------------------------|
| `topic:full`           | _org.eclipse.ditto/device-123/things/live/messages/hello.world_ |
| `topic:namespace`      | _org.eclipse.ditto_                                             |
| `topic:entityName`     | _device-123_                                                    |
| `topic:group`          | _things_                                                        |
| `topic:channel`        | _live_                                                          |
| `topic:criterion`      | _messages_                                                      |
| `topic:action`         | &#10060;                                                        |
| `topic:subject`        | _hello.world_                                                   |
| `topic:action-subject` | _hello.world_                                                   |


## Function expressions

Whenever placeholders can be used (e.g. for [connections](basic-connections.html#placeholders)), function expressions 
may additionally be specified.

The syntax of such function expressions are specified similar to a UNIX `pipe`, e.g.:
```
{%raw%}{{ thing:name | fn:substring-before('-') | fn:default('fallback') | fn:upper() }}{%endraw%}
```

Normally, the first expression in such a pipeline would be a placeholder to start with, in the example above `thing:name`.  
Functions could follow it separated by the pipe (`|`) symbol - each function in the pipeline receives the value of the
previous expression (which may also be `empty`).  
A function in the beginning of a pipeline would get `empty` as its input. 

The function either contains no parameters or contains parameters which are either string constants or could also 
be placeholders again.

### Function library

The following functions are provided by Ditto out of the box:

| Name                  | Signature                                                        | Description                                                                                                                                                                                                                                                                        | Examples                                                                         |
|-----------------------|------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| `fn:filter`           | `(String filterValue, String rqlFunction, String comparedValue)` | Removes the result of the previous expression in the pipeline unless the condition specified by the parameters is satisfied.<br/>The first parameter `filterValue` may also be omitted in which case the 2 passed parameters will be appplied on the previous pipeline expression. | `fn:filter('like','allowlist1|foobar|include')` <br/>`fn:filter(header:response-required,'eq','true')` <br/> `fn:filter(header:response-required,'exists')` <br/> `fn:filter(header:response-required,'exists','false')` |
| `fn:default`          | `(String defaultValue)`                                          | Provides the passed `defaultValue` when the previous expression in a pipeline resolved to `empty` (e.g. due to a non-defined `header` placeholder key).<br/>Another placeholder may be specified which is resolved to a String and inserted as `defaultValue`.                     | `fn:default('fallback')`<br/>`fn:default("fallback")`<br/>`fn:default(thing:id)` |
| `fn:substring-before` | `(String givenString)`                                           | Parses the result of the previous expression and passes along only the characters _before_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`.                                                                    | `fn:substring-before(':')`<br/>`fn:substring-before(":")`                        |
| `fn:substring-after`  | `(String givenString)`                                           | Parses the result of the previous expression and passes along only the characters _after_ the first occurrence of `givenString`.<br/>If `givenString` is not contained, this function will resolve to `empty`.                                                                     | `fn:substring-after(':')`<br/>`fn:substring-after(":")`                          |
| `fn:lower`            | `()`                                                             | Makes the String result of the previous expression lowercase in total.                                                                                                                                                                                                             | `fn:lower()`                                                                     |
| `fn:upper`            | `()`                                                             | Makes the String result of the previous expression uppercase in total.                                                                                                                                                                                                             | `fn:upper()`                                                                     |
| `fn:delete`           | `()`                                                             | Deletes the result of the previous pipeline expression unconditionally. Any following expressions are ignored.                                                                                                                                                                     | `fn:delete()`                                                                    |
| `fn:replace`          | `(String from, String to)`                                       | Replaces a string with another using Java's `String::replace` method.                                                                                                                                                                                                              | `fn:replace('foo', 'bar')`                                                       |
| `fn:split`            | `(String separator)`                                             | Splits the previous pipeline using the passed `separator` resulting an "array" pipeline output containing several elements.<br/>May only be used in combination with the [JWT placeholder](#scope-openid-connect-configuration) as input placeholder.                              | `fn:split(' ')`<br/>`fn:split(',')`                                              |

### RQL functions

The following RQL functions are available for `fn:filter`

| Name                  | Signatures                                                                                                         | Description                                                                                                                                                                                                                                                                                                                        |
|-----------------------|--------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eq`                  | `(String filterValue, 'eq', String comparedValue)`, <br/>`('eq', String comparedValue)`                            | If 3 parameters are passed in, the function filters on the first parameter being equal to the last. <br/>If 2 parameters are passed in, the function filters the previous pipeline element being equal to the last parameter.                                                                                                      |
| `ne`                  | `(String filterValue, 'ne', String comparedValue)`, <br/>`('ne', String comparedValue)`                            | If 3 parameters are passed in, the function filters on the first parameter being not equal to the last. <br/>If 2 parameters are passed in, the function filters the previous pipeline element being not equal to the last parameter.                                                                                              |
| `exists`              | `(String filterValue, 'exists', String true|false)`,  <br/>`('exists', String true/false)`, <br/>`('exists')`      | If 3 parameters are passed in, the function filters on the first parameter being existent/non-existent. <br/>If 2 parameters are passed in, the function filters the previous pipeline element being being existent/non-existent. <br/>If 1 parameter is passed in, the function filters the previous pipeline element being true. |
| `like`                | `(String filterValue, 'like', String regex)`, <br/>`('like', String regex)`                                        | If 3 parameters are passed in, the function filters on the first parameter matching the last. <br/>If 2 parameters are passed in, the function filters the previous pipeline element matching the last parameter.                                                                                                                  |

The `like` function can be used with different expressions:
* `*` : One or more arbitrary characters
* `?` : One arbitrary character
* `|` : Element prior to the operator OR element after the operator
