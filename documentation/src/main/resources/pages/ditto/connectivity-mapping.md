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
| `fields` | all | Comma-separated list of [field selectors](httpapi-concepts.html#with-field-selector) |
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

For `deleted` events, a `_deleted` field contains the deletion timestamp in ISO-8601 format.

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

### ImplicitThingCreation mapper

Automatically creates a thing when an incoming message arrives. The thing structure is defined
in the `thing` option as a JSON template with placeholder support.

**Options:**

| Option | Required | Description |
|--------|----------|-------------|
| `thing` | Yes | Thing JSON template (supports `{%raw%}{{ header:* }}{%endraw%}`, `{%raw%}{{ request:subjectId }}{%endraw%}`, `{%raw%}{{ time:now }}{%endraw%}` placeholders) |
| `commandHeaders` | No | Headers for the create command (default: `{"If-None-Match": "*"}`) |
| `allowPolicyLockout` | No | Allow creating policies without `WRITE` permission (default: `true`) |

The `thing` template can include:
* `_policy` -- inline [Policy JSON](basic-policy.html#model-specification)
* `_copyPolicyFrom` -- copy policy from another thing or policy ID

```json
{
  "thing": {
    "thingId": "{%raw%}{{ header:device_id }}{%endraw%}",
    "attributes": {
      "CreatedBy": "ImplicitThingCreation"
    }
  },
  "commandHeaders": {
    "If-None-Match": "*"
  }
}
```

### UpdateTwinWithLiveResponse mapper

Creates a [merge Thing command](protocol-specification-things-merge.html) from a
[live retrieve response](protocol-twinlive.html#live), patching the live data into the twin.

**Options:**

| Option | Default | Description |
|--------|---------|-------------|
| `dittoHeadersForMerge` | `{"response-required": false, "if-match": "*"}` | Headers for the merge command (supports placeholders) |

### CloudEvents mapper

Maps incoming [CloudEvents](https://github.com/cloudevents/spec/blob/v1.0.2/cloudevents/spec.md) to
Ditto Protocol. Supports both Binary and Structured CloudEvents.

* Binary CloudEvents: `content-type: application/vnd.eclipse.ditto+json` with `ce-*` headers
* Structured CloudEvents: `content-type: application/cloudevents+json` with Ditto Protocol in `data`

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
// Build a Ditto Protocol message
Ditto.buildDittoProtocolMsg(namespace, name, group, channel,
    criterion, action, path, dittoHeaders, value, status, extra)

// Build an external message
Ditto.buildExternalMsg(headers, textPayload, bytePayload, contentType)

// Convert ArrayBuffer to String
Ditto.arrayBufferToString(arrayBuffer)

// Convert String to ArrayBuffer
Ditto.stringToArrayBuffer(string)

// Convert ArrayBuffer to ByteBuffer (requires bytebuffer.js)
Ditto.asByteBuffer(arrayBuffer)
```

### Mapping incoming messages

Implement `mapToDittoProtocolMsg` to convert external payloads to Ditto Protocol:

```javascript
function mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType) {
  if (contentType !== 'application/json') {
    return null; // drop unsupported content types
  }

  let jsonData = JSON.parse(textPayload);
  let value = {
    temperature: { properties: { value: parseFloat(jsonData.temp) } }
  };

  return Ditto.buildDittoProtocolMsg(
    'org.eclipse.ditto',
    headers['device_id'],
    'things', 'twin', 'commands', 'modify',
    '/features', headers, value
  );
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
  // Parse namespace, name, group, channel, criterion, action from topic
  // Then delegate to mapFromDittoProtocolMsg or handle directly
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
let temp = view.getInt16(0) / 100.0;  // 16-bit signed int at offset 0
let pressure = view.getInt16(2);       // 16-bit signed int at offset 2
let humidity = view.getUint8(4);       // 8-bit unsigned int at offset 4
```

Or use `ByteBuffer.js` (load with `"loadBytebufferJS": "true"`):

```javascript
let byteBuf = Ditto.asByteBuffer(bytePayload);
let numberFromBytes = parseInt(byteBuf.toHex(), 16);
```

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

### Binary payload example

Device sends 5 bytes: 2 bytes temperature (int16), 2 bytes pressure (int16), 1 byte humidity (uint8):

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

## Custom Java mapper

For advanced use cases, implement a custom Java-based mapper by extending
[`AbstractMessageMapper`](https://github.com/eclipse-ditto/ditto/blob/master/connectivity/service/src/main/java/org/eclipse/ditto/connectivity/service/mapping/AbstractMessageMapper.java).

Key methods to implement:

* `List<Adaptable> map(ExternalMessage message)` -- inbound mapping
* `List<ExternalMessage> map(Adaptable adaptable)` -- outbound mapping
* `String getAlias()` -- unique mapper alias

To deploy:

1. Add the mapper JAR to the connectivity service classpath
   ([extending Ditto](installation-extending.html#providing-additional-functionality-by-adding-jars-to-the-classpath))
2. Register the alias in [connectivity configuration](installation-extending.html#adjusting-configuration-of-ditto)
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
