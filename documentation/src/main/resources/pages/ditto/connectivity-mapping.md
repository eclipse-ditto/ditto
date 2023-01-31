---
title: Payload mapping in connectivity service
keywords: mapping, transformation, payload, javascript, mapper, protobuf
tags: [connectivity]
permalink: connectivity-mapping.html
---

{% include callout.html content="**TL;DR**<br/>The payload mapping feature in Ditto's connectivity APIs can be used to 
    transform arbitrary payload consumed via the different supported protocols 
    to [Ditto Protocol](protocol-overview.html) messages and vice versa." type="primary" %}


## Motivation

Eclipse Ditto is about providing access to IoT devices via the [digital twin](intro-digitaltwins.html) pattern. 
In order to provide structured APIs for different heterogeneous devices Ditto defines a lightweight JSON based 
[model](basic-overview.html).

A [Thing](basic-thing.html) might look like in the following example:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
  "attributes": {
    "location": "kitchen"
  },
  "features": {
    "transmission": {
       "properties": {
         "cur_speed": 90
       }
     }
  }
}
```

Devices in the IoT, may they be brownfield devices or newly produced devices, will probably not send their data to the
cloud in the structure and [protocol](protocol-overview.html) Ditto requires.

They should not need to be aware of something like Ditto running in the cloud mirroring them as digital twins.

So for example device payload could look like this:

```json
{
  "val": "23.42 °C",
  "ts": 1523946112727
}
```

In case of constrained devices or IoT protocols, even binary payload might be common.

```
0x08BD (hex representation)
```

## Builtin mappers

The following message mappers are included in the Ditto codebase:

| Mapper Alias | Description                    | Inbound           | Outbound           |
|------------|--------------------------------|---------------------------|---------------------------|
| [Ditto](#ditto-mapper) | Assumes that inbound/outbound messages are already in [Ditto Protocol](protocol-overview.html) (JSON) format. | ✓ | ✓ |
| [JavaScript](#javascript-mapper) | Converts arbitrary messages from and to the [Ditto Protocol](protocol-overview.html) format using **custom** JavaScript code executed by Ditto. | ✓ | ✓ |
| [Normalized](#normalized-mapper) | Transforms the payload of events to a normalized view. |  | ✓ |
| [ConnectionStatus](#connectionstatus-mapper) | This mapper handles messages containing `creation-time` and `ttd` headers by updating a feature of the targeted thing with [definition](basic-feature.html#feature-definition) [ConnectionStatus](https://github.com/eclipse/vorto/tree/development/models/org.eclipse.ditto-ConnectionStatus-1.0.0.fbmodel). | ✓ |  |
| [RawMessage](#rawmessage-mapper) | For outgoing message commands and responses, this mapper extracts the payload for publishing directly into the channel. For incoming messages, this mapper wraps them in a configured message command or response envelope. | ✓ | ✓ |
| [ImplicitThingCreation](#implicitthingcreation-mapper) | This mapper handles messages for which a Thing should be created automatically based on a defined template. | ✓ |  |
| [UpdateTwinWithLiveResponse](#updatetwinwithliveresponse-mapper) | This mapper creates a [merge Thing command](protocol-specification-things-merge.html) when an indiviudal [retrieve command](protocol-specification-things-retrieve.html) for an single Thing was received via the [live channel](protocol-twinlive.html#live) patching exactly the retrieved "live" data into the twin. | ✓ |  |
| [CloudEvents Mapper](#cloudevents-mapper) | The mapper maps incoming CloudEvent to Ditto Protocol. Supports both Binary and Structured CloudEvent. | ✓ | ✓ |

### Ditto mapper

This is the default [Ditto Protocol](protocol-overview.html) mapper. If you do not specify any payload mapping this
 mapper is used to map inbound and outbound messages. The mapper requires no mandatory options, so its alias can
 be directly used as a mapper reference.

It assumes that received messages are in [Ditto Protocol JSON](protocol-specification.html) and emits outgoing messages
 also in that format.

### JavaScript mapper

This mapper may be used whenever any inbound messages are not yet in [Ditto Protocol](protocol-overview.html). 
By using the built in [JavaScript mapping engine](#javascript-mapping-engine) (based on Rhino) custom defined 
JavaScript scripts can be executed which are responsible for creating [Ditto Protocol JSON](protocol-specification.html) 
message from arbitrary consumed payload.

The same is possible for outbound messages in order to transform [Ditto Protocol JSON](protocol-specification.html) 
messages (e.g. events or responses) to arbitrary other formats.

#### Configuration options

* `incomingScript` (required): the mapping script for incoming messages
* `outgoingScript` (required):  the mapping script for outgoing messages
* `loadBytebufferJS` (optional, default: `"false"`): whether to load ByteBufferJS library
* `loadLongJS` (optional, default: `"false"`): whether to load LongJS library

### Normalized mapper

This mapper transforms `created` and `modified` events (other type of messages are dropped) to a normalized view. 
Events are mapped to a nested sparse JSON.

```json
{
  "topic": "thing/id/things/twin/events/modified",
  "headers": { "content-type": "application/json" },
  "path": "/features/sensors/properties/temperature/indoor/value",
  "value": 42
}
```

would result in the following normalized JSON representation:

```json
{
  "thingId": "thing:id",
  "features": {
    "sensors": {
      "properties": {
        "temperature": {
          "indoor": {
            "value": 42
          }
        }
      }
    }
  },
  "_context": {
    "topic": "thing/id/things/twin/events/modified",
    "path": "/features/sensors/properties/temperature/indoor/value",
    "headers": {
      "content-type": "application/json"
    }
  }
}
```
The `_context` field contains the original message content excluding the `value`.

#### Configuration options

* `fields` (optional, default: all fields): comma separated list of fields that are contained in the result (see also
 chapter about [field selectors](httpapi-concepts.html#with-field-selector))
 
### ConnectionStatus mapper
This mapper transforms the information from the `ttd` and `creation-time` message headers 
(see Eclipse Hono [device notifications](https://www.eclipse.org/hono/docs/concepts/device-notifications/)) into a 
ModifyFeature command that complies with the [Vorto functionblock](https://github.com/eclipse/vorto/tree/development/models/org.eclipse.ditto-ConnectionStatus-1.0.0.fbmodel) `{%raw%}org.eclipse.ditto:ConnectionStatus{%endraw%}`. 
 
The connectivity state of the device is then represented in a Feature.<br/>
It is mostly used in conjunction with another mapper that transforms the payload e.g.:<br/>
`"payloadMapping": [ "Ditto" , "connectionStatus" ]`
 
Example of a resulting `ConnectionStatus` feature:
```json
{
  "thingId": "eclipse:ditto",
  "features": {
    "ConnectionStatus": {
      "definition": [ "org.eclipse.ditto:ConnectionStatus:1.0.0" ],
      "properties": {
        "status": {
          "readySince": "2019-10-29T14:16:18Z",
          "readyUntil": "2019-10-29T14:21:18Z"
        }
      }
    }
  }
}
```
 
#### Configuration options

* `thingId` (required): The ID of the Thing that is updated with the connectivity state. It can either be a fixed value
 or a header placeholder (e.g. `{%raw%}{{ header:device_id }}{%endraw%}`).
* `featureId` (optional, default: `ConnectionStatus`): The ID of the Feature that is updated. It can either be a
 fixed value or resolved from a message header (e.g. `{%raw%}{{ header:feature_id }}{%endraw%}`).

### RawMessage mapper

This mapper relates the payload in the `"value"` field of message commands and message responses to the payload
of AMQP, MQTT and Kafka messages and the body of HTTP requests. The encoding of the payload is chosen according to
the configured content type. The subject, direction, thing ID and feature ID of the envelope for incoming message
commands and responses need to be configured.

Messages with the Ditto protocol content type `application/vnd.eclipse.ditto+json` or signals that are not message
commands or responses are mapped by the [Ditto mapper](#ditto-mapper) instead.

For example, the mapper maps between the feature message command response
```json
{
  "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/heatUp",
  "headers": { "content-type": "application/octet-stream" },
  "path": "/features/water-tank/inbox/messages/heatUp",
  "value": "AQIDBAUG",
  "status": 200
}
```
and an AMQP, MQTT 5, Kafka message with payload or an HTTP request with body of 6 bytes
```
0x01 02 03 04 05 06
```
and headers
```
content-type: application/octet-stream
status: 200
subject: heatUp
ditto-message-direction: TO
ditto-message-thing-id: org.eclipse.ditto:smartcoffee
ditto-message-feature-id: water-tank
```
The headers are lost for connection protocols without application headers such as MQTT 3.
 
#### Configuration options

Example configuration:
```json
{
  "outgoingContentType": "application/octet-stream",
  "incomingMessageHeaders": {
    "content-type": "{%raw%}{{ header:content-type | fn:default('application/octet-stream') }}{%endraw%}",
    "status": "{%raw%}{{ header:status }}{%endraw%}",
    "subject": "{%raw%}{{ header:subject | fn:default('fallback-subject') }}{%endraw%}",
    "ditto-message-direction": "TO",
    "ditto-message-thing-id": "{%raw%}{{ header:ditto-message-thing-id | fn:default('ns:fallback-thing') }}{%endraw%}",
    "ditto-message-feature-id": "{%raw%}{{ header:ditto-message-feature-id }}{%endraw%}"
  }
}
```

* `outgoingContentType` (optional): The fallback content-type for outgoing message commands and responses without
  the content-type header. Default to `text/plain; charset=UTF-8`.
* `incomingMessageHeaders` (optional): A JSON object containing the following headers needed to construct a message
  command or response envelope containing the incoming message as payload in the field `"value"`. 
  The following placeholders may be used in the headers:

    | Placeholder                       | Description                                                                                            |
    |-----------------------------------|--------------|
    | `{%raw%}{{ header:<header-name> }}{%endraw%}` | header value from the external message, e.g. from protocol headers                                     |
    | `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which did the request - the one of the connection source in this case |
    | `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                    | 
    | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                | 

   * `content-type` (optional): The content type with which to encode the incoming message as payload.
      Default to `{%raw%}{{ header:content-type | fn:default('application/octet-stream') }}{%endraw%}`.
      If resolved to the Ditto protocol content type `application/vnd.eclipse.ditto+json`, then the entire payload
      is interpreted as a Ditto protocol message instead.
   * `status` (optional): Include for message responses. Exclude for message commands. Default to
     `{%raw%}{{ header:status }}{%endraw%}`.
   * `subject` (mandatory for MQTT 3): Subject of the message. Default to `{%raw%}{{ header:subject }}{%endraw%}`.
      Mapping will fail if not resolvable.
   * `ditto-message-direction` (optional): The message direction. Default to `TO`, which corresponds to `inbox` in
      message commands and responses.
   * `ditto-message-thing-id` (mandatory for MQTT 3): ID of the thing to send the message command or response to.
     Default to `{%raw%}{{ header:ditto-message-thing-id }}{%endraw%}`. Mapping will fail if not resolvable.
   * `ditto-message-feature-id` (optional): Include to send the message or message response to a feature of the thing.
     Exclude to send it to the thing itself. Default to `{%raw%}{{ header:ditto-message-feature-id }}{%endraw%}`.

### ImplicitThingCreation mapper

This mapper implicitly creates a new thing for an incoming message. 
 
The created thing contains the values defined in the template, configured in the `mappingDefinitions` `options`.  

#### Configuration options

* `thing` (required): The values of the thing that is created implicitly. It can either contain fixed values
 or header placeholders (e.g. `{%raw%}{{ header:device_id }}{%endraw%}`).
    * the following placeholders may be used inside the `"thing"` JSON:

      | Placeholder                       | Description                                                                                             |
      |------------------------------------|--------------|
      | `{%raw%}{{ header:<header-name> }}{%endraw%}` | header value from the external message, e.g. from protocol headers                                      |
      | `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which did the request - the one of the connection source in this case |
      | `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                     | 
      | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                 | 

    * The `"thing"` JSON may also include:
      * an inline policy: `"_policy"` containing the [Policy JSON](basic-policy.html#model-specification) to create a new policy
        from and link with the thing
      * a "copy policy from" statement: `"_copyPolicyFrom"` - see also [create Thing alternatives](protocol-examples-creatething.html#alternative-creatething-commands)
          * either including a policyId to copy from
          * or containing the link to a thing to copy the policy from in the form: `{% raw %}{{ ref:things/<theThingId>/policyId }}{% endraw %}`

* `commandHeaders` (optional, default: `{"If-None-Match": "*"}`): The Ditto headers to use for constructing the "create thing" command for creating the
  twin and to use for creating errors.
    * in this configured headers, the following placeholders may be used:

      | Placeholder                       | Description                                                                                             |
      |-----------------------------------|--------------|
      | `{%raw%}{{ header:<header-name> }}{%endraw%}` | header value from the external message, e.g. from protocol headers                                      |
      | `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which did the request - the one of the connection source in this case |
      | `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                     | 
      | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                 | 

* `allowPolicyLockout` (optional, default: `true`): whether it should be allowed to create policies without having `WRITE`
  permissions in the created policy for the subject which creates the policy 
  (the [authorizationContext](connectivity-manage-connections.html#authorization) of the connection source which 
  received the message for which a thing should be created implicitly)
 
Example of a template defined in `options`:
```json
{
  "thing": {
    "thingId": "{%raw%}{{ header:device_id }}{%endraw%}",
    "attributes": {
      "CreatedBy": "ImplicitThingCreation"
    }
  },
  "commandHeaders": {
    "If-None-Match": "*",
    "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}"
  },
  "allowPolicyLockout": true
}
```

### UpdateTwinWithLiveResponse mapper

This mapper creates a [merge Thing command](protocol-specification-things-merge.html) when a 
[retrieve command](protocol-specification-things-retrieve.html) was received via the 
[live channel](protocol-twinlive.html#live) patching exactly the retrieved "live" data into the twin.
 
#### Configuration options

* `dittoHeadersForMerge` (optional): The Ditto headers to use for constructing the "merge thing"
  command for updating the twin, may for example add a condition to apply in order to update the twin
  (default applied Ditto headers if not configured: `"response-required": false`, `"if-match": "*"`).
   * in this configured headers, the following placeholders may be used:

       | Placeholder                       | Description                                                                                            |
       |-----------------------------------|--------------|
       | `{%raw%}{{ header:<header-name> }}{%endraw%}` | header value from the external message, e.g. from protocol headers                                     |
       | `{%raw%}{{ request:subjectId }}{%endraw%}` | the first authenticated subjectId which did the request - the one of the connection source in this case |
       | `{%raw%}{{ time:now }}{%endraw%}` | the current timestamp in ISO-8601 format as string in UTC timezone                                                    | 
       | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | the current timestamp in "milliseconds since epoch" formatted as string                                | 


Example configuration:
```json
{
  "dittoHeadersForMerge": {
    "if-match": "*",
    "response-required": false,
    "put-metadata": [
      {"key":"*/updated-by","value":"{%raw%}{{ request:subjectId }}{%endraw%}"},
      {"key":"*/updated-via","value":"device-live-response"},
      {"key":"*/update-hint","value":"{%raw%}{{ header:some-custom-hint }}{%endraw%}"},
      {"key":"*/updated-at","value":"{%raw%}{{ time:now }}{%endraw%}"}
    ]
  }
}
```
### CloudEvents Mapper

This mapper maps incoming [CloudEvent](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md) to Ditto Protocol. It provides support for both Binary CloudEvents as well as Structured CloudEvents.

**Note**: The mapper supports incoming Structured CloudEvents  messages with `content-type:application/cloudevents+json` and Binary CloudEvents message with `content-type:application/vnd.eclipse.ditto+json`

#### CloudEvents examples

Incoming messages need to have the mandatory CloudEvents fields.
For example, a Binary CloudEvent for Ditto would look like this:

```
  headers:
      ce-specversion:1.0
      ce-id:some-id
      ce-type:some-type
      ce-source:generic-producer
      content-type:application/vnd.eclipse.ditto+json
```

```json
{
  "topic": "my.sensors/sensor01/things/twin/commands/modify",
  "path": "/",
  "value": {
    "thingId": "my.sensors:sensor01",
    "policyId": "my.test:policy",
    "attributes": {
      "manufacturer": "Well known sensors producer",
      "serial number": "100",
      "location": "Ground floor"
    },
    "features": {
      "measurements": {
        "properties": {
          "temperature": 100,
          "humidity": 0
        }
      }
    }
  }
}
```

A Structured CloudEvent for Ditto would look like this:

```

headers:
  content-type:application/cloudevents+json
```

```json
{
  "specversion": "1.0",
  "id": "3212e",
  "source": "http:somesite.com",
  "type": "com.site.com",
  "data": {
    "topic": "my.sensors/sensor01/things/twin/commands/modify",
    "path": "/",
    "value": {
      "thingId": "my.sensors:sensor01",
      "policyId": "my.test:policy",
      "attributes": {
        "manufacturer": "Well known sensors producer",
        "serial number": "100",
        "location": "Ground floor"
      },
      "features": {
        "measurements": {
          "properties": {
            "temperature": 100,
            "humidity": 0
          }
        }
      }
    }
  }
}
```

## Example connection with multiple mappers

The following example connection defines a `ConnectionStatus` mapping with the ID `status` and references it in a source.  
Messages received via this source will be mapped by the `Ditto` mapping and the `ConnectionStatus` mapping.  
The `Ditto` mapping requires no options to be configured, so you can directly use its alias `Ditto`.  

```json
{ 
  "name": "exampleConnection",
  "sources": [{
      "addresses": ["<source>"],
      "authorizationContext": ["ditto:inbound"],
      "payloadMapping": ["Ditto", "status"]
    }
  ],
  "mappingDefinitions": {
    "status": {
      "mappingEngine": "ConnectionStatus",
      "options": {
        "thingId": "{%raw%}{{ header:device_id }}{%endraw%}"
      }
    }
  }
}
```

{% include note.html content="Starting aliases with an uppercase character and IDs with a lowercase character is
 encouraged to avoid confusion but this is not enforced. "%}



## Example connection with mapping conditions

The following example connection defines `incomingConditions` and `outgoingConditions`for the ConnectionStatus 
mapping engine.  
Optional incomingConditions are validated before the mapping of inbound messages.  
Optional outgoingConditions are validated before the mapping of outbound messages.  
Conditional Mapping can be achieved by using [function expressions](basic-placeholders.html#function-expressions).
When multiple incoming or outgoing conditions are set for one `mappingEngine`, 
all have to equal true for the mapping to be executed.  

```json
{ 
  "name": "exampleConnection",
  "sources": [{
      "addresses": ["<source>"],
      "authorizationContext": ["ditto:inbound"],
      "payloadMapping": ["status"]
    }
  ],
  "mappingDefinitions": {
    "status": {
      "mappingEngine": "ConnectionStatus",
      "incomingConditions": {
        "sampleCondition": "fn:filter(header:incoming-mapping-required,'eq','true')"
      },
      "outgoingConditions": {
        "sampleCondition": "fn:filter(header:outgoing-mapping-required,'eq','true')"
      },
      "options": {
        "thingId": "{%raw%}{{ header:device_id }}{%endraw%}"
      }
    }
  }
}
```



## JavaScript mapping engine

Ditto utilizes the [Rhino](https://github.com/mozilla/rhino) JavaScript engine for Java for evaluating the JavaScript
to apply for mapping payloads.

Using Rhino instead of Nashorn, the newer JavaScript engine shipped with Java, has the benefit that sandboxing can be 
applied in a better way. 

Sandboxing of different payload scripts is required as Ditto is intended to be run as cloud service where multiple
connections to different endpoints are managed for different tenants at the same time. This requires the isolation of
each single script to avoid interference with other scripts and to protect the JVM executing the script against harmful
code execution.


### Constraints

Rhino does not fully support EcmaScript 6. Check which language constructs are supported before using
them in a mapping function. See [http://mozilla.github.io/rhino/compat/engines.html](http://mozilla.github.io/rhino/compat/engines.html).

Ditto currently includes Rhino version `1.7.14` and has the `VERSION_ES6` flag enabled.

#### Sandboxing

For sandboxing/security reasons following restrictions apply:


* access to Java packages and classes is not possible
* using `exit`, `quit`, `print`, etc. is not possible
* file access is not possible
* doing remote calls (e.g. to foreign web-servers) is not possible
* programming an endless-loop will terminate the script
* programming a recursion will terminate the script
* the file size of the script is limited
* no foreign JS libraries can be loaded (unless they fit in the file size limit and are included into the mapping script)

### Helper libraries

In order to work more conveniently with binary payloads, the following libraries may be loaded for payload transformations:


* [bytebuffer.js](https://github.com/dcodeIO/bytebuffer.js) a ByteBuffer implementation using ArrayBuffers
* [long.js](https://github.com/dcodeIO/long.js) for representing a 64-bit two's-complement integer value


### Adding additional JS libraries

The used [Rhino JS engine](https://github.com/mozilla/rhino) allows making use of "CommonJS" in order to load JS
modules via `require('')` into the engine.  
This feature is exposed to Ditto, configuring the configuration key `commonJsModulePath` or environment variable 
`CONNECTIVITY_MESSAGE_MAPPING_JS_COMMON_JS_MODULE_PATH` of the connectivity service to a path in the
connectivity Docker container where to load additional CommonJS modules from - e.g. use a volume mount in order to get
additional JS modules into the container.

For example, configure this variable to a folder to which you add (our mount) JavaScript libraries:
```
CONNECTIVITY_MESSAGE_MAPPING_JS_COMMON_JS_MODULE_PATH=/opt/commonjs-modules/
```

Then, for example, put [`pbf.js`](https://www.npmjs.com/package/pbf) (or any other JS library you want to use) 
into that folder.

Afterwards, the library can be used in your JS snippet using:
```javascript
var Pbf = require('pbf');
```


### Helper functions

Ditto comes with a few helper functions, which makes writing the mapping scripts easier. They are available under the
`Ditto` scope:

```javascript
/**
 * Builds a Ditto Protocol message from the passed parameters.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto". Or "_"
 * (underscore) for connection announcements.
 * @param {string} name - The name of the entity, e.g.: "device".
 * @param {string} channel - The channel for the signal: "twin"|"live"|"none"
 * @param {string} group - The affected group/entity: "things"|"policies"|"connections".
 * @param {string} criterion - The criterion to apply: "commands"|"events"|"search"|"messages"|"announcements"|"errors".
 * @param {string} action - The action to perform: "create"|"retrieve"|"modify"|"delete". Or the announcement name:
 * "opened"|"closed"|"subjectDeletion". Or the subject of the message.
 * @param {string} path - The path which is affected by the message (e.g.: "/attributes"), or the destination
 * of a message (e.g.: "inbox"|"outbox").
 * @param {Object.<string, string>} dittoHeaders - The headers Object containing all Ditto Protocol header values.
 * @param {*} [value] - The value to apply / which was applied (e.g. in a "modify" action).
 * @param {number} [status] - The status code that indicates the result of the command. If setting a status code,
 * the Ditto Protocol Message will be interpreted as a response (e.g. content will be ignored when using 204).
 * @param {Object} [extra] - The enriched extra fields when selected via "extraFields" option.
 * @returns {DittoProtocolMessage} dittoProtocolMessage(s) -
 *  The mapped Ditto Protocol message or
 *  <code>null</code> if the message could/should not be mapped
 */
function buildDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra) {
    const topic = buildTopic(namespace, name, group, channel, criterion, action);

    return {
        topic: topic,
        path: path,
        headers: dittoHeaders,
        value: value,
        status: status,
        extra: extra,
    };
}

/**
 * Builds a Ditto Protocol topic from the passed parameters.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto". Or "_"
 * (underscore) for connection announcements.
 * @param {string} name - The name of the entity, e.g.: "device".
 * @param {string} channel - The channel for the signal: "twin"|"live"|"none"
 * @param {string} group - The affected group/entity: "things"|"policies"|"connections".
 * @param {string} criterion - The criterion to apply: "commands"|"events"|"search"|"messages"|"announcements"|"errors".
 * @param {string} action - The action to perform: "create"|"retrieve"|"modify"|"delete". Or the announcement name:
 * "opened"|"closed"|"subjectDeletion". Or the subject of the message.
 * @returns {string} topic - the topic.
 */
function buildTopic(namespace, name, group, channel, criterion, action) {
    const topicChannel = 'none' === channel ? '' : '/' + channel;

    return namespace + "/" + name + "/" + group + topicChannel + "/" + criterion + "/" + action;
}

/**
 * Builds an external message from the passed parameters.
 * @param {Object.<string, string>} headers - The external headers Object containing header values
 * @param {string} [textPayload] - The external mapped String
 * @param {ArrayBuffer} [bytePayload] - The external mapped bytes as ArrayBuffer
 * @param {string} [contentType] - The returned Content-Type
 * @returns {ExternalMessage} externalMessage - 
 *  the mapped external message
 *  or <code>null</code> if the message could/should not be mapped
 */
function buildExternalMsg(headers, textPayload, bytePayload, contentType) {

  return {
    headers: headers,
    textPayload: textPayload,
    bytePayload: bytePayload,
    contentType: contentType,
  };
}

/**
 * Transforms the passed ArrayBuffer to a String interpreting the content of the passed arrayBuffer as unsigned 8
 * bit integers.
 *
 * @param {ArrayBuffer} arrayBuffer the ArrayBuffer to transform to a String
 * @returns {String} the transformed String
 */
function arrayBufferToString(arrayBuffer) {

  return String.fromCharCode.apply(null, new Uint8Array(arrayBuffer));
}

/**
 * Transforms the passed String to an ArrayBuffer using unsigned 8 bit integers.
 *
 * @param {String} string the String to transform to an ArrayBuffer
 * @returns {ArrayBuffer} the transformed ArrayBuffer
 */
function stringToArrayBuffer(string) {

  let buf = new ArrayBuffer(string.length);
  let bufView = new Uint8Array(buf);
  for (let i=0, strLen=string.length; i<strLen; i++) {
    bufView[i] = string.charCodeAt(i);
  }
  return buf;
}

/**
 * Transforms the passed ArrayBuffer to a {ByteBuffer} (from bytebuffer.js library which needs to be loaded).
 *
 * @param {ArrayBuffer} arrayBuffer the ArrayBuffer to transform
 * @returns {ByteBuffer} the transformed ByteBuffer
 */
function asByteBuffer(arrayBuffer) {
    
  let byteBuffer = new ArrayBuffer(arrayBuffer.byteLength);
  new Uint8Array(byteBuffer).set(new Uint8Array(arrayBuffer));
  return dcodeIO.ByteBuffer.wrap(byteBuffer);
}
```

### Mapping incoming messages

Incoming external messages can be mapped to Ditto Protocol conform messages by implementing the following JavaScript function:

```javascript
/**
 * Maps the passed parameters to a Ditto Protocol message.
 * @param {Object.<string, string>} headers - The headers Object containing all received header values
 * @param {string} [textPayload] - The String to be mapped
 * @param {ArrayBuffer} [bytePayload] - The bytes to be mapped as ArrayBuffer
 * @param {string} [contentType] - The received Content-Type, e.g. "application/json"
 * @returns {(DittoProtocolMessage|Array<DittoProtocolMessage>)} dittoProtocolMessage(s) -
 *  the mapped Ditto Protocol message,
 *  an array of Ditto Protocol messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapToDittoProtocolMsg(
  headers,
  textPayload,
  bytePayload,
  contentType
) {

  // ### Insert/adapt your mapping logic here.
  // Use helper function Ditto.buildDittoProtocolMsg to build Ditto protocol message
  // based on incoming payload.
  // See https://websites.eclipseprojects.io/ditto/connectivity-mapping.html#helper-functions for details.
  // ### example code assuming the Ditto protocol content type for incoming messages.
  if (contentType === 'application/vnd.eclipse.ditto+json') {
    // Message is sent as Ditto protocol text payload and can be used directly
    return JSON.parse(textPayload);
  } else if (contentType === 'application/octet-stream') {
    // Message is sent as binary payload; assume Ditto protocol message (JSON).
    try {
      return JSON.parse(Ditto.arrayBufferToString(bytePayload));
    } catch (e) {
      // parsing failed (no JSON document); return null to drop the message
      return null;
    }
  } else if (contentType === 'application/json') {
    let parsedJson = JSON.parse(textPayload);
    // the following variables would be determined from the "parsedJson" and from the "headers":
    let namespace = "";
    let name = "";
    let group = "things";
    let channel = "twin";
    let criterion = "commands";
    let action = "modify";
    let path = "/attributes";
    let dittoHeaders = {};
    let value = {
      "a": 1
    };
    return Ditto.buildDittoProtocolMsg(
      namespace, 
      name, 
      group, 
      channel, 
      criterion, 
      action, 
      path, 
      dittoHeaders, 
      value)
  }
  // no mapping logic matched; return null to drop the message
  return null;
}
```

The result of the function has to be a JavaScript object in [Ditto Protocol](protocol-overview.html) or an array of 
such JavaScript objects. That's where the helper method `Ditto.buildDittoProtocolMsg` is useful: 
it explicitly defines which parameters are required for the Ditto Protocol message.

There is another JavaScript function which is helpful when access to the complete external message is needed.
It is possible to define the `mapToDittoProtocolMsgWrapper` in the incoming payload mapping and access the original
`externalMsg`.

This is the default implementation of `mapToDittoProtocolMsgWrapper`, delegating to `mapToDittoProtocolMsg`:
```javascript
/**
 * Maps the passed external message to a Ditto Protocol message.
 * @param {ExternalMessage} externalMsg - The external message to map to a Ditto Protocol message
 * @returns {(DittoProtocolMessage|Array<DittoProtocolMessage>)} dittoProtocolMessage(s) -
 *  The mapped Ditto Protocol message,
 *  an array of Ditto Protocol messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapToDittoProtocolMsgWrapper(externalMsg) {

  let headers = externalMsg.headers;
  let textPayload = externalMsg.textPayload;
  let bytePayload = externalMsg.bytePayload;
  let contentType = externalMsg.contentType;

  return mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType);
}
```

### Mapping outgoing messages

Outgoing Ditto Protocol messages (e.g. [responses](basic-signals-commandresponse.html) or [events](basic-signals-event.html)) 
can be mapped to external messages by implementing the following JavaScript function:

```javascript
/**
 * Maps the passed parameters which originated from a Ditto Protocol message to an external message.
 * @param {string} namespace - The namespace of the entity in java package notation, e.g.: "org.eclipse.ditto". Or "_" 
 * (underscore) for connection announcements.
 * @param {string} name - The name of the entity, e.g.: "device".
 * @param {string} group - The affected group/entity: "things"|"policies"|"connections".
 * @param {string} channel - The channel for the signal: "twin"|"live"|"none"
 * @param {string} criterion - The criterion to apply: "commands"|"events"|"search"|"messages"|"announcements"|
 * "errors".
 * @param {string} action - The action to perform: "create"|"retrieve"|"modify"|"delete". Or the announcement name: 
 * "opened"|"closed"|"subjectDeletion". Or the subject of the message.
 * @param {string} path - The path which is affected by the message (e.g.: "/attributes"), or the destination
 * of a message (e.g.: "inbox"|"outbox").
 * @param {Object.<string, string>} dittoHeaders - The headers Object containing all Ditto Protocol header values.
 * @param {*} [value] - The value to apply / which was applied (e.g. in a "modify" action).
 * @param {number} [status] - The status code that indicates the result of the command. When this field is set,
 * it indicates that the Ditto Protocol Message contains a response.
 * @param {Object} [extra] - The enriched extra fields when selected via "extraFields" option.
 * @returns {(ExternalMessage|Array<ExternalMessage>)} externalMessage - The mapped external message, an array of 
 * external messages or <code>null</code> if the message could/should not be mapped.
 */
function mapFromDittoProtocolMsg(
  namespace,
  name,
  group,
  channel,
  criterion,
  action,
  path,
  dittoHeaders,
  value,
  status,
  extra
) {

  // ###
  // Insert your mapping logic here
  // ### example code using the Ditto protocol content type.
  let headers = dittoHeaders;
  let textPayload = JSON.stringify(Ditto.buildDittoProtocolMsg(namespace, name, group, channel, criterion, action, 
                                                               path, dittoHeaders, value, status, extra));
  let bytePayload = null;
  let contentType = 'application/vnd.eclipse.ditto+json';
  return Ditto.buildExternalMsg(
    headers, // The external headers Object containing header values
    textPayload, // The external mapped String
    bytePayload, // The external mapped byte[]
    contentType // The returned Content-Type
  );
}
```

The result of the function has to be a JavaScript object or, an array of JavaScript objects with the fields `headers`, 
`textPayload`, `bytePayload` and `contentType`. That's where the helper method `Ditto.buildExternalMsg` is useful: 
it explicitly defines which parameters are required for the external message.

There is another JavaScript function which is helpful when access to the complete Ditto protocol message is needed.
It is possible to define the `mapFromDittoProtocolMsgWrapper` in the outgoing payload mapping and access the
original `dittoProtocolMsg`.  
Please refer to the [Ditto Protocol specification](protocol-specification.html#dittoProtocolEnvelope)
to inspect which JSON fields are available when.

This is the default implementation of `mapFromDittoProtocolMsgWrapper`, delegating to `mapFromDittoProtocolMsg`:
```javascript
/**
 * Maps the passed Ditto Protocol message to an external message.
 * @param {DittoProtocolMessage} dittoProtocolMsg - The Ditto Protocol message to map
 * @returns {(ExternalMessage|Array<ExternalMessage>)} externalMessage -
 *  The mapped external message,
 *  an array of external messages or
 *  <code>null</code> if the message could/should not be mapped
 */
function mapFromDittoProtocolMsgWrapper(dittoProtocolMsg) {

  let topic = dittoProtocolMsg.topic;
  let splitTopic = topic.split("/");

  let namespace = splitTopic[0];
  let name = splitTopic[1];
  let group = splitTopic[2];

  let channel;
  let criterion;
  let action;
  if (hasChannel(group)) {
    channel = splitTopic[3];
    criterion = splitTopic[4];
    action = splitTopic[5];
  } else {
    channel = 'none';
    criterion = splitTopic[3];
    action = splitTopic[4];
  }

  let path = dittoProtocolMsg.path;
  let dittoHeaders = dittoProtocolMsg.headers;
  let value = dittoProtocolMsg.value;
  let status = dittoProtocolMsg.status;
  let extra = dittoProtocolMsg.extra;

  return mapFromDittoProtocolMsg(namespace, name, group, channel, criterion, action, path, dittoHeaders, value, status, extra);
}
```

## JavaScript payload types

Both, text payloads and byte payloads may be mapped.

### Text payloads

Working with text payloads is as easy as it gets in JavaScript. For example, for the content-type `application/json`
structured data may be processed like this:

```javascript
let value;
if (contentType === 'application/json') {
  let parsedJson = JSON.parse(textPayload);
  value = parsedJson.number1 + parsedJson['sub-field']; // remember to access JSON keys with dashes in a JS special way
} else {
  // a script may decide to not map other content-types than application/json
  return null;
}
// proceed ...
```

### Byte payloads

Working with byte payloads is also possible but does require a little bit of knowledge about JavaScript's 
[ArrayBuffer](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/ArrayBuffer) 
[TypedArrays](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray) and
[DataView](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView).

What you get in the mapping scripts is a `bytePayload` of type `ArrayBuffer` which lets you work on the bytes 
in different ways: 

#### Typed Arrays

> A TypedArray \[is\] a view into an ArrayBuffer where every item has the same size and type.<br/> [source](https://hacks.mozilla.org/2017/01/typedarray-or-dataview-understanding-byte-order/)

With TypedArrays you can simply wrap the `bytePayload` `ArrayBuffer` and work on all the items e.g. 
as unsigned 8-bit integers:

```javascript
let bytes = new Uint8Array(bytePayload);
bytes[0]; // access the first byte
bytes[1]; // access the second byte
``` 

#### DataViews

> The DataView \[is\] another view into an ArrayBuffer, but one which allows items of different size and type in the ArrayBuffer.<br/> [source](https://hacks.mozilla.org/2017/01/typedarray-or-dataview-understanding-byte-order/)

```javascript
let view = new DataView(bytePayload);
view.getInt8(0); // access a 8-bit signed integer (byte) on offset=0
view.getUint16(1); // access a 16-bit unsigned integer (usigned short) on offset=1
``` 

DataViews also allow to `set` bytes to an underlying ArrayBuffer conveniently.

#### ByteBuffer.js

Alternatively, Ditto's JavaScript transformation may be loaded with the [above mentioned](#helper-libraries) libraries, 
e.g. "bytebuffer.js".<br />
With `ByteBuffer`, the content of an `ArrayBuffer` can be accessed in a buffered way:

```javascript
let byteBuf = Ditto.asByteBuffer(bytePayload);
let numberFromBytes = parseInt(byteBuf.toHex(), 16);

let base64encoded = byteBuf.toBase64();
let buf = dcodeIO.ByteBuffer.fromBase64(base64encoded);

buf.readInt(); // read a 32bit signed integer + advances the offset in the buffer
buf.readUTF8String(4); // read 4 characters of UTF-8 encoded string + advances the offset in the buffer
buf.remaining(); // gets the number of remaining readable bytes in the buffer
```

Check the [ByteBuffer API documentation](https://github.com/dcodeIO/bytebuffer.js/wiki/API) to find out what is possible 
with that helper.


## JavaScript Examples

### Text payload example

Let's assume your device sends telemetry data via [Eclipse Hono's](https://www.eclipse.org/hono/) MQTT adapter 
into the cloud. And, that an example payload of your device is:

```json
{
  "temp": "23.42 °C",
  "hum": 78,
  "pres": {
    "value": 760,
    "unit": "mmHg"
  }
}
```

We want to map a single message of this device containing updates for all 3 values to a Thing in the following structure:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
  "features": {
    "temperature": {
       "properties": {
         "value": 23.42
       }
     },
    "pressure": {
       "properties": {
         "value": 760
       }
     },
    "humidity": {
       "properties": {
         "value": 78
       }
     }
  }
}
```

Therefore, we define following `incoming` mapping function:

```javascript
function mapToDittoProtocolMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
) {
    
    if (contentType !== 'application/json') {
        return null; // only handle messages with content-type application/json
    }
    
    let jsonData = JSON.parse(textPayload);
    
    let value = {
        temperature: {
            properties: {
                value: jsonData.temp.split(" ")[0] // omit the unit
            }
        },
        pressure: {
            properties: {
                value: jsonData.pres.value
            }
        },
        humidity: {
            properties: {
                value: jsonData.hum
            }
        }
    };

    return Ditto.buildDittoProtocolMsg(
        'org.eclipse.ditto', // in this example always the same
        headers['device_id'], // Eclipse Hono sets the authenticated device_id as AMQP 1.0 header
        'things', // we deal with a Thing
        'twin', // we want to update the twin
        'commands', // we want to create a command to update a twin
        'modify', // modify the twin
        '/features', // modify all features at once
        headers, // pass through the headers from AMQP 1.0
        value
    );
}
```

When your device now sends its payload via the MQTT adapter of Eclipse Hono:

```bash
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": "23.42 °C","hum": 78,"pres": {"value": 760,"unit": "mmHg"}}'
```

Your digital twin is updated by applying the specified script and extracting the relevant values from the passed `textPayload`.


### Bytes payload example

For this example, let's assume your device sends telemetry data via [Eclipse Hono's](https://www.eclipse.org/hono/) 
HTTP adapter into the cloud. An example payload of your device - displayed as hexadecimal - is:

```
0x09EF03F72A
```

Let us now also assume that

* the first 2 bytes `09 EF` represent 
  * the temperature as 16bit signed integer (thus, may also be negative)
  * this is not a float in oder to save space (as float needs at least 32 bit)
* the second 2 bytes `03 F7` represent the pressure as 16bit signed integer
* the last byte `2A` represents the humidity as 8bit unsigned integer of our device.

We want to map a single message of this device containing updates for all 3 values to a Thing in the following structure:

```json
{
  "thingId": "the.namespace:the-thing-id",
  "policyId": "the.namespace:the-policy-id",
  "features": {
    "temperature": {
       "properties": {
         "value": 25.43
       }
     },
    "pressure": {
       "properties": {
         "value": 1015
       }
     },
    "humidity": {
       "properties": {
         "value": 42
       }
     }
  }
}
```

Therefore, we define following `incoming` mapping function:

```javascript
function mapToDittoProtocolMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
) {
    
    if (contentType !== 'application/octet-stream') {
        return null; // only handle messages with content-type application/octet-stream
    }
    
    let view = new DataView(bytePayload);
    
    let value = {
        temperature: {
            properties: {
                // interpret the first 2 bytes (16 bit) as signed int and divide through 100.0:
                value: view.getInt16(0) / 100.0
            }
        },
        pressure: {
            properties: {
                // interpret the next 2 bytes (16 bit) as signed int:
                value: view.getInt16(2)
            }
        },
        humidity: {
            properties: {
                // interpret the next 1 bytes (8 bit) as unsigned int:
                value: view.getUint8(4)
            }
        }
    };

    return Ditto.buildDittoProtocolMsg(
        'org.eclipse.ditto', // in this example always the same
        headers['device_id'], // Eclipse Hono sets the authenticated device_id as AMQP 1.0 header
        'things', // we deal with a Thing
        'twin', // we want to update the twin
        'commands', // we want to create a command to update a twin
        'modify', // modify the twin
        '/features', // modify all features at once
        headers, // pass through the headers from AMQP 1.0
        value
    );
}
```

When your device now sends its payload via the HTTP adapter of Eclipse Hono:

```bash
echo -e $((0x09EF03F72A)) | curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/octet-stream' --data-binary @- http://127.0.0.1:8080/telemetry
```

Your digital twin is updated by applying the specified script and extracting the relevant values from the passed `bytePayload`.


## Custom Java based implementation

Beside the JavaScript based mapping - which can be configured/changed at runtime without the need of restarting the
connectivity service - there is also the possibility to implement a custom Java based mapper.

The interface to be implemented is
[`MessageMapper`](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/mapping/MessageMapper.java))
and there is an abstract class [`AbstractMessageMapper`](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/mapping/AbstractMessageMapper.java)
which eases implementation of a custom mapper.

Simply extend from `AbstractMessageMapper` to provide a custom mapper:

```java
public final class FooMapper extends AbstractMessageMapper {

    private static final String MAPPER_ALIAS = "Foo";

    public FooMapper(ActorSystem actorSystem, Config config) {
        super(actorSystem, config);
    }

    private FooMapper(AbstractMessageMapper copyFromMapper) {
        super(copyFromMapper);
    }

    @Override
    public String getAlias() {
        return MAPPER_ALIAS;
    }

    @Override
    public boolean isConfigurationMandatory() {
        return false;
    }

    @Override
    public MessageMapper createNewMapperInstance() {
        return new FooMapper(this);
    }

    @Override
    public List<Adaptable> map(ExternalMessage externalMessage) {
        // TODO implement mapping inbound messages consumed via "sources" to DittoProtocol adaptables
        return null;
    }

    @Override
    public DittoHeaders getAdditionalInboundHeaders(ExternalMessage externalMessage) {
        return DittoHeaders.empty();
    }

    @Override
    public List<ExternalMessage> map(Adaptable adaptable) {
        // TODO implement mapping DittoProtocol adaptables to outbound messages published via "targets"
        return null;
    }

    @Override
    private void doConfigure(Connection connection, MappingConfig mappingConfig,
            MessageMapperConfiguration configuration) {
        // extract configuration if needed
    }
}
```

After instantiation of the custom `MessageMapper`, the `doConfigure` method is called with all the *options* which were 
provided to the mapper in the [configured connection](connectivity-manage-connections.html#create-connection). 
Use them in order to pass in configurations, thresholds, etc.

Then, simply implement both of the `map` methods:

* `List<Adaptable> map(ExternalMessage message)` maps from an incoming external message to
  * an empty list of `Adaptable`s if the incoming message should be dropped
  * a list of one or many [Ditto Protocol](protocol-overview.html) `Adaptable`s
* `List<ExternalMessage> map(Adaptable adaptable)` maps from an outgoing [Ditto Protocol](protocol-overview.html) `Adaptable` to
  * an empty list of `ExternalMessage`s if the outgoing message should be dropped
  * a list of one or many external messages

In order to use this custom Java based mapper implementation, the following steps are required:

* the alias has to be defined via the implemented `getAlias()` method - it must be unique and *should* start with an uppercase letter
* if the custom mapper requires mandatory options then implement `isConfigurationMandatory()` to return `true`
* the mapper class needs to be on the classpath of the [connectivity](architecture-services-connectivity.html) 
  microservice in order to be loaded.  
  Follow the instructions of 
  [how to extend Ditto](installation-extending.html#providing-additional-functionality-by-adding-jars-to-the-classpath)
  to achieve that.
* the mapper needs to be registered via configuration in the connectivity service, 
  [extend the configuration](installation-extending.html#adjusting-configuration-of-ditto) or add the mapper via 
  [system properties](installation-operating.html#ditto-configuration) configuration
* when creating a new connection you have to specify the alias of your mapper as the `mappingEngine` in the
  connection's `mappingDefinitions` and reference the ID of your mapper in a source or a target

{% include tip.html content="If your mapper does not require any options (`isConfigurationMandatory() = true`), you can
    directly reference the alias in a source or a target without first defining it inside `mappingDefinitions`." %} 

### Example for Custom Java based mapper

Please have a look at the following Ditto example project:
* [custom-ditto-java-payload-mapper](https://github.com/eclipse-ditto/ditto-examples/tree/master/custom-ditto-java-payload-mapper)

This shows how to implement, add and configure a custom, Protobuf based, Java payload mapper for Ditto to use in the
connectivity service for mapping a custom domain specific Protbuf encoded payload.
