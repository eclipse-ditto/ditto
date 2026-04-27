---
title: Payload Mapping
keywords: mapping, transformation, payload, javascript, mapper, protobuf
tags: [connectivity]
permalink: connectivity-mapping.html
---

You use payload mapping to transform messages between your device's native format and [Ditto Protocol](protocol-overview.html) JSON.

{% include callout.html content="**TL;DR**: Payload mapping transforms arbitrary payloads consumed via connections
    to [Ditto Protocol](protocol-overview.html) messages and vice versa. Use built-in mappers or write custom JavaScript to handle any format." type="primary" %}

## Overview

Devices rarely send data in Ditto Protocol format. A device might send:

```json
{"val": "23.42 °C", "ts": 1523946112727}
```

Or even binary:

```
0x08BD
```

Payload mapping bridges this gap by converting between device-native formats and the structured
[Ditto Protocol](protocol-specification.html) that Ditto requires.

## Built-in mappers

| Mapper | Alias | Inbound | Outbound | Description |
|--------|-------|:---:|:---:|-------------|
| [Ditto](#ditto-mapper) | `Ditto` | &#10004; | &#10004; | Messages already in Ditto Protocol format |
| [JavaScript](#javascript-mapper) | `JavaScript` | &#10004; | &#10004; | Custom JS scripts for arbitrary formats |
| [Normalized](#normalized-mapper) | `Normalized` | | &#10004; | Transforms events to a normalized JSON view |
| [ConnectionStatus](#connectionstatus-mapper) | `ConnectionStatus` | &#10004; | | Updates a feature based on `ttd`/`creation-time` headers |
| [RawMessage](#rawmessage-mapper) | `RawMessage` | &#10004; | &#10004; | Maps message command payloads directly |
| [ImplicitThingCreation](#implicitthingcreation-mapper) | `ImplicitThingCreation` | &#10004; | | Auto-creates things from incoming messages |
| [UpdateTwinWithLiveResponse](#updatetwinwithliveresponse-mapper) | `UpdateTwinWithLiveResponse` | &#10004; | | Patches twin data from live responses |
| [CloudEvents](#cloudevents-mapper) | `CloudEvents` | &#10004; | &#10004; | Maps CloudEvent format to Ditto Protocol |

### Ditto mapper

The default mapper. Assumes messages are in [Ditto Protocol JSON](protocol-specification.html).
No configuration required -- use the alias `Ditto` directly.

### JavaScript mapper

Transforms arbitrary payloads using custom JavaScript scripts executed in a sandboxed
[Rhino](https://github.com/mozilla/rhino) engine. See the
[JavaScript mapping engine](#javascript-mapping-engine) section for details.

**Options:**

| Option | Required | Description |
|--------|----------|-------------|
| `incomingScript` | Yes | Script for inbound messages |
| `outgoingScript` | Yes | Script for outbound messages |
| `loadBytebufferJS` | No | Load ByteBufferJS library (default: `false`) |
| `loadLongJS` | No | Load LongJS library (default: `false`) |

### Normalized mapper

Transforms `created`, `modified`, and `deleted` events to a normalized JSON structure.
Other message types are dropped.

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `fields` | all | Comma-separated list of [field selectors](httpapi-concepts.html#field-selectors) |
| `includeDeletedFields` | `false` | Track partial deletions in `_deletedFields` |

Example input:
```json
{
  "topic": "thing/id/things/twin/events/modified",
  "path": "/features/sensors/properties/temperature/value",
  "value": 42
}
```

Normalized output:
```json
{
  "thingId": "thing:id",
  "features": {
    "sensors": {
      "properties": {
        "temperature": { "value": 42 }
      }
    }
  },
  "_context": {
    "topic": "thing/id/things/twin/events/modified",
    "path": "/features/sensors/properties/temperature/value",
    "value": 42
  }
}
```

For `deleted` events, the mapper includes a `_deleted` field with the deletion timestamp:

```json
{
  "topic": "thing/id/things/twin/events/deleted",
  "headers": { "content-type": "application/json" },
  "path": "/",
  "value": null
}
```

Normalized output:

```json
{
  "thingId": "thing:id",
  "_deleted": "2023-12-01T10:30:00Z",
  "_context": {
    "topic": "thing/id/things/twin/events/deleted",
    "path": "/",
    "value": null,
    "headers": {
      "content-type": "application/json"
    }
  }
}
```

The `_deleted` field contains the ISO-8601 timestamp when the thing was deleted. This field is only added for complete thing deletions.

When `includeDeletedFields` is enabled, partial deletions and merge-patch deletions are tracked in `_deletedFields`. The `_deletedFields` object mirrors the JSON structure of the deleted paths and stores ISO-8601 timestamps at the leaf nodes:

```json
{
  "thingId": "thing:id",
  "_deletedFields": {
    "attributes": {
      "location": {
        "mountedOn": "2025-01-27T10:00:00Z"
      }
    },
    "features": {
      "myFeature": "2025-01-27T11:00:00Z"
    }
  }
}
```

### ConnectionStatus mapper

Transforms `ttd` and `creation-time` headers (from
[Eclipse Hono device notifications](https://www.eclipse.org/hono/docs/concepts/device-notifications/))
into a ModifyFeature command that updates a `ConnectionStatus` feature.

Typically used alongside another mapper:
`"payloadMapping": ["Ditto", "connectionStatus"]`

**Options:**

| Option | Required | Description |
|--------|----------|-------------|
| `thingId` | Yes | Thing ID (supports placeholders like `{%raw%}{{ header:device_id }}{%endraw%}`) |
| `featureId` | No | Feature ID (default: `ConnectionStatus`) |

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

Use the ConnectionStatus mapper alongside another mapper in a source configuration:

```json
{
  "addresses": ["<source>"],
  "authorizationContext": ["ditto:inbound"],
  "payloadMapping": ["Ditto", "connectionStatus"]
}
```

### RawMessage mapper

Maps message command/response payloads directly to/from the external message format. The encoding
is determined by the content type.

For incoming messages, the mapper wraps the payload in a message command envelope.
For outgoing messages, the mapper extracts the `"value"` field for publishing.

Messages with `content-type: application/vnd.eclipse.ditto+json` fall through to the Ditto mapper.

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `outgoingContentType` | `text/plain; charset=UTF-8` | Fallback content type for outgoing messages |
| `incomingMessageHeaders` | (see below) | Headers for constructing the message envelope |

Key incoming headers (all support placeholders):

| Header | Purpose | Default |
|--------|---------|---------|
| `content-type` | Encoding of the payload | `{%raw%}{{ header:content-type \| fn:default('application/octet-stream') }}{%endraw%}` |
| `ditto-message-subject` | Message subject (required for MQTT 3) | `{%raw%}{{ header:ditto-message-subject }}{%endraw%}` |
| `ditto-message-thing-id` | Target thing ID (required for MQTT 3) | `{%raw%}{{ header:ditto-message-thing-id }}{%endraw%}` |
| `ditto-message-direction` | `TO` (inbox) or `FROM` (outbox) | `TO` |
| `ditto-message-feature-id` | Feature ID (omit for thing-level messages) | `{%raw%}{{ header:ditto-message-feature-id }}{%endraw%}` |
| `status` | Include for responses, omit for commands | `{%raw%}{{ header:status }}{%endraw%}` |

The mapper maps between a feature message command response like:

```json
{
  "topic": "org.eclipse.ditto/smartcoffee/things/live/messages/heatUp",
  "headers": { "content-type": "application/octet-stream" },
  "path": "/features/water-tank/inbox/messages/heatUp",
  "value": "AQIDBAUG",
  "status": 200
}
```

and an AMQP, MQTT 5, or Kafka message with payload of 6 bytes:

```
0x01 02 03 04 05 06
```

with headers:

```
content-type: application/octet-stream
status: 200
ditto-message-subject: heatUp
ditto-message-direction: TO
ditto-message-thing-id: org.eclipse.ditto:smartcoffee
ditto-message-feature-id: water-tank
```

Headers are lost for connection protocols without application headers such as MQTT 3.

Example configuration:

```json
{
  "outgoingContentType": "application/octet-stream",
  "incomingMessageHeaders": {
    "content-type": "{%raw%}{{ header:content-type | fn:default('application/octet-stream') }}{%endraw%}",
    "status": "{%raw%}{{ header:status }}{%endraw%}",
    "ditto-message-subject": "{%raw%}{{ header:ditto-message-subject | fn:default('fallback-subject') }}{%endraw%}",
    "ditto-message-direction": "TO",
    "ditto-message-thing-id": "{%raw%}{{ header:ditto-message-thing-id | fn:default('ns:fallback-thing') }}{%endraw%}",
    "ditto-message-feature-id": "{%raw%}{{ header:ditto-message-feature-id }}{%endraw%}"
  }
}
```

### ImplicitThingCreation mapper

Automatically creates a thing when an incoming message arrives. The thing structure is defined
in the `thing` option as a JSON template with placeholder support.

**Options:**

* `thing` (required): The values of the thing that is created implicitly. It can contain fixed values
  or header placeholders (e.g. `{%raw%}{{ header:device_id }}{%endraw%}`).
    * The following placeholders may be used inside the `"thing"` JSON:

      | Placeholder | Description |
      |-------------|-------------|
      | `{%raw%}{{ header:<header-name> }}{%endraw%}` | Header value from the external message, e.g. from protocol headers |
      | `{%raw%}{{ request:subjectId }}{%endraw%}` | The first authenticated subjectId which did the request |
      | `{%raw%}{{ time:now }}{%endraw%}` | The current timestamp in ISO-8601 format as string in UTC timezone |
      | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | The current timestamp in "milliseconds since epoch" formatted as string |

    * The `"thing"` JSON may also include:
      * an inline policy: `"_policy"` containing the [Policy JSON](basic-policy.html#model-specification)
      * a "copy policy from" statement: `"_copyPolicyFrom"` - see also [create Thing alternatives](protocol-examples-creatething.html#alternative-creatething-commands)
          * either including a policyId to copy from
          * or containing the link to a thing to copy the policy from in the form: `{%raw%}{{ ref:things/<theThingId>/policyId }}{%endraw%}`

* `commandHeaders` (optional, default: `{"If-None-Match": "*"}`): The Ditto headers to use for constructing the "create thing" command.
    * The following placeholders may be used:

      | Placeholder | Description |
      |-------------|-------------|
      | `{%raw%}{{ header:<header-name> }}{%endraw%}` | Header value from the external message, e.g. from protocol headers |
      | `{%raw%}{{ request:subjectId }}{%endraw%}` | The first authenticated subjectId which did the request |
      | `{%raw%}{{ time:now }}{%endraw%}` | The current timestamp in ISO-8601 format as string in UTC timezone |
      | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | The current timestamp in "milliseconds since epoch" formatted as string |

* `allowPolicyLockout` (optional, default: `true`): Whether it should be allowed to create policies without having `WRITE`
  permissions in the created policy for the subject which creates the policy.

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

Creates a [merge Thing command](protocol-specification-things-merge.html) from a
[live retrieve response](protocol-twinlive.html#live-channel), patching the live data into the twin.

**Options:**

* `dittoHeadersForMerge` (optional): The Ditto headers to use for constructing the "merge thing"
  command, may for example add a condition to apply in order to update the twin
  (default: `"response-required": false`, `"if-match": "*"`).
   * The following placeholders may be used:

       | Placeholder | Description |
       |-------------|-------------|
       | `{%raw%}{{ header:<header-name> }}{%endraw%}` | Header value from the external message, e.g. from protocol headers |
       | `{%raw%}{{ request:subjectId }}{%endraw%}` | The first authenticated subjectId which did the request |
       | `{%raw%}{{ time:now }}{%endraw%}` | The current timestamp in ISO-8601 format as string in UTC timezone |
       | `{%raw%}{{ time:now_epoch_millis }}{%endraw%}` | The current timestamp in "milliseconds since epoch" formatted as string |

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

### CloudEvents mapper

Maps incoming [CloudEvents](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md) to
Ditto Protocol. Supports both Binary and Structured CloudEvents.

**Note**: The mapper supports incoming Structured CloudEvents messages with `content-type:application/cloudevents+json` and Binary CloudEvents messages with `content-type:application/vnd.eclipse.ditto+json`.

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

## Using multiple mappers

Reference multiple mappers in a source's `payloadMapping` array. Define custom mappers in
`mappingDefinitions`:

```json
{
  "name": "exampleConnection",
  "sources": [{
    "addresses": ["<source>"],
    "authorizationContext": ["ditto:inbound"],
    "payloadMapping": ["Ditto", "status"]
  }],
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

{% include note.html content="Start aliases with an uppercase character and IDs with a lowercase character to distinguish them clearly. This convention is not enforced. "%}

## Mapping conditions

You can add `incomingConditions` and `outgoingConditions` to control when a mapper executes.
All conditions must evaluate to true for the mapping to run:

```json
{
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

Ditto uses the [Rhino](https://github.com/mozilla/rhino) JavaScript engine (version `1.7.14`,
ES6 flag enabled) with strict sandboxing for security.

### Sandboxing constraints

* No access to Java packages or classes
* No file access, network calls, or `exit`/`quit`/`print`
* Endless loops and deep recursion are terminated
* Script file size is limited
* No foreign JS library loading (unless included inline)

Check [Rhino compatibility](https://mozilla.github.io/rhino/compat/engines.html) for supported
ES6 features.

### Helper libraries

You can load these libraries via `specificConfig` options:

* [bytebuffer.js](https://github.com/dcodeIO/bytebuffer.js) -- `ArrayBuffer` manipulation
* [long.js](https://github.com/dcodeIO/long.js) -- 64-bit integer support

### Adding CommonJS modules

Configure `CONNECTIVITY_MESSAGE_MAPPING_JS_COMMON_JS_MODULE_PATH` to point to a directory
containing CommonJS modules (for example, via a Docker volume mount):

```
CONNECTIVITY_MESSAGE_MAPPING_JS_COMMON_JS_MODULE_PATH=/opt/commonjs-modules/
```

Then use `require()` in your scripts:

```javascript
var Pbf = require('pbf');
```

### Helper functions

Ditto provides these functions under the `Ditto` scope:

```javascript
// Builds a Ditto Protocol message from the passed parameters.
function buildDittoProtocolMsg(namespace, name, group, channel,
    criterion, action, path, dittoHeaders, value, status, extra) {
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

// Builds a Ditto Protocol topic string.
function buildTopic(namespace, name, group, channel, criterion, action) {
    const topicChannel = 'none' === channel ? '' : '/' + channel;

    return namespace + "/" + name + "/" + group + topicChannel + "/" + criterion + "/" + action;
}

// Builds an external message from the passed parameters.
function buildExternalMsg(headers, textPayload, bytePayload, contentType) {

  return {
    headers: headers,
    textPayload: textPayload,
    bytePayload: bytePayload,
    contentType: contentType,
  };
}

// Transforms an ArrayBuffer to a String (unsigned 8-bit integers).
function arrayBufferToString(arrayBuffer) {

  return String.fromCharCode.apply(null, new Uint8Array(arrayBuffer));
}

// Transforms a String to an ArrayBuffer (unsigned 8-bit integers).
function stringToArrayBuffer(string) {

  let buf = new ArrayBuffer(string.length);
  let bufView = new Uint8Array(buf);
  for (let i=0, strLen=string.length; i<strLen; i++) {
    bufView[i] = string.charCodeAt(i);
  }
  return buf;
}

// Transforms an ArrayBuffer to a ByteBuffer (requires bytebuffer.js).
function asByteBuffer(arrayBuffer) {

  let byteBuffer = new ArrayBuffer(arrayBuffer.byteLength);
  new Uint8Array(byteBuffer).set(new Uint8Array(arrayBuffer));
  return dcodeIO.ByteBuffer.wrap(byteBuffer);
}
```

### Mapping incoming messages

Implement `mapToDittoProtocolMsg` to convert external payloads to Ditto Protocol:

```javascript
function mapToDittoProtocolMsg(
  headers,
  textPayload,
  bytePayload,
  contentType
) {

  // Insert/adapt your mapping logic here.
  // Use Ditto.buildDittoProtocolMsg to build Ditto Protocol messages from incoming payload.
  if (contentType === 'application/vnd.eclipse.ditto+json') {
    // Message is already in Ditto Protocol format -- use directly
    return JSON.parse(textPayload);
  } else if (contentType === 'application/octet-stream') {
    // Binary payload -- assume Ditto Protocol message (JSON)
    try {
      return JSON.parse(Ditto.arrayBufferToString(bytePayload));
    } catch (e) {
      // parsing failed (no JSON document); drop the message
      return null;
    }
  } else if (contentType === 'application/json') {
    let parsedJson = JSON.parse(textPayload);
    value = parsedJson.number1 + parsedJson['sub-field']; // access JSON keys with dashes using bracket notation
    // determine these variables from parsedJson and headers:
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
  // no mapping logic matched; drop the message
  return null;
}
```

Return a single Ditto Protocol message, an array of messages, or `null` to drop the message.

For full access to the external message object, implement `mapToDittoProtocolMsgWrapper` instead:

```javascript
function mapToDittoProtocolMsgWrapper(externalMsg) {
  let headers = externalMsg.headers;
  let textPayload = externalMsg.textPayload;
  let bytePayload = externalMsg.bytePayload;
  let contentType = externalMsg.contentType;
  return mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType);
}
```

### Mapping outgoing messages

Implement `mapFromDittoProtocolMsg` to convert Ditto Protocol messages to external format:

```javascript
function mapFromDittoProtocolMsg(namespace, name, group, channel,
    criterion, action, path, dittoHeaders, value, status, extra) {
  let headers = dittoHeaders;
  let textPayload = JSON.stringify(
    Ditto.buildDittoProtocolMsg(namespace, name, group, channel,
      criterion, action, path, dittoHeaders, value, status, extra)
  );
  return Ditto.buildExternalMsg(headers, textPayload, null,
    'application/vnd.eclipse.ditto+json');
}
```

For access to the full Ditto Protocol message (including `revision`), implement
`mapFromDittoProtocolMsgWrapper`:

```javascript
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

### Working with byte payloads

Use [TypedArrays](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray)
or [DataViews](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/DataView)
to process binary data:

```javascript
// TypedArray approach
let bytes = new Uint8Array(bytePayload);

// DataView approach (mixed types)
let view = new DataView(bytePayload);
view.getInt8(0);                       // 8-bit signed integer (byte) at offset 0
view.getUint16(1);                     // 16-bit unsigned integer (unsigned short) at offset 1
let temp = view.getInt16(0) / 100.0;   // 16-bit signed int at offset 0
let pressure = view.getInt16(2);       // 16-bit signed int at offset 2
let humidity = view.getUint8(4);       // 8-bit unsigned int at offset 4
```

Or use `ByteBuffer.js` (load with `"loadBytebufferJS": "true"`):

```javascript
let byteBuf = Ditto.asByteBuffer(bytePayload);
let numberFromBytes = parseInt(byteBuf.toHex(), 16);

let base64encoded = byteBuf.toBase64();
let buf = dcodeIO.ByteBuffer.fromBase64(base64encoded);

buf.readInt(); // read a 32bit signed integer + advances the offset in the buffer
buf.readUTF8String(4); // read 4 characters of UTF-8 encoded string + advances the offset in the buffer
buf.remaining(); // gets the number of remaining readable bytes in the buffer
```

Check the [ByteBuffer API documentation](https://github.com/dcodeIO/bytebuffer.js/wiki/API) for the full list of operations.

## JavaScript examples

### Text payload example

Device sends JSON telemetry:

```json
{"temp": "23.42 °C", "hum": 78, "pres": {"value": 760, "unit": "mmHg"}}
```

Mapping to update thing features:

```javascript
function mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType) {
  if (contentType !== 'application/json') return null;

  let jsonData = JSON.parse(textPayload);
  let value = {
    temperature: { properties: { value: parseFloat(jsonData.temp.split(" ")[0]) } },
    pressure: { properties: { value: jsonData.pres.value } },
    humidity: { properties: { value: jsonData.hum } }
  };

  return Ditto.buildDittoProtocolMsg(
    'org.eclipse.ditto', headers['device_id'],
    'things', 'twin', 'commands', 'modify',
    '/features', headers, value
  );
}
```

Send this payload via Eclipse Hono's MQTT adapter:

```bash
mosquitto_pub -u 'sensor1@DEFAULT_TENANT' -P hono-secret -t telemetry -m '{"temp": "23.42 °C","hum": 78,"pres": {"value": 760,"unit": "mmHg"}}'
```

The digital twin is updated by applying the script and extracting the relevant values from the `textPayload`.

### Binary payload example

Device sends 5 bytes as hexadecimal `0x09EF03F72A`:

* the first 2 bytes `09 EF` represent the temperature as 16-bit signed integer (not a float, to save space)
* the next 2 bytes `03 F7` represent the pressure as 16-bit signed integer
* the last byte `2A` represents the humidity as 8-bit unsigned integer

```javascript
function mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType) {
  if (contentType !== 'application/octet-stream') return null;

  let view = new DataView(bytePayload);
  let value = {
    temperature: { properties: { value: view.getInt16(0) / 100.0 } },
    pressure: { properties: { value: view.getInt16(2) } },
    humidity: { properties: { value: view.getUint8(4) } }
  };

  return Ditto.buildDittoProtocolMsg(
    'org.eclipse.ditto', headers['device_id'],
    'things', 'twin', 'commands', 'modify',
    '/features', headers, value
  );
}
```

Send this payload via Eclipse Hono's HTTP adapter:

```bash
echo -e $((0x09EF03F72A)) | curl -i -X POST -u sensor1@DEFAULT_TENANT:hono-secret -H 'Content-Type: application/octet-stream' --data-binary @- http://127.0.0.1:8080/telemetry
```

The digital twin is updated by applying the script and extracting the relevant values from the `bytePayload`.

## Custom Java mapper

For advanced use cases, implement a custom Java-based mapper by extending
[`AbstractMessageMapper`](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/mapping/AbstractMessageMapper.java).

Extend `AbstractMessageMapper` to provide a custom mapper:

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

Key methods to implement:

* `List<Adaptable> map(ExternalMessage message)` -- inbound mapping (return empty list to drop)
* `List<ExternalMessage> map(Adaptable adaptable)` -- outbound mapping (return empty list to drop)
* `String getAlias()` -- unique mapper alias (must start with uppercase)

To deploy:

1. Add the mapper JAR to the connectivity service classpath
   ([extending Ditto](installation-extending.html#adding-jars-to-the-classpath))
2. Register the alias in [connectivity configuration](installation-extending.html#adjusting-service-configuration)
3. Reference the alias in your connection's `mappingDefinitions`

{% include tip.html content="If your mapper does not require any options (`isConfigurationMandatory() = true`), you can
    directly reference the alias in a source or a target without first defining it inside `mappingDefinitions`." %}

For a complete example, see the
[custom-ditto-java-payload-mapper](https://github.com/eclipse-ditto/ditto-examples/tree/master/custom-ditto-java-payload-mapper)
project.

## Further reading

* [Connections overview](basic-connections.html) -- connection model and configuration
* [Header mapping](connectivity-header-mapping.html) -- map external headers
* [Ditto Protocol](protocol-overview.html) -- message format specification
