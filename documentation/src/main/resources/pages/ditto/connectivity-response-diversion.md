---
title: Response diversion
keywords: response, diversion, redirect, routing, multi-protocol
tags: [ connectivity ]
permalink: connectivity-response-diversion.html
---

## Response diversion

Response diversion is a powerful feature in Ditto's connectivity service that allows responses from one connection to be
diverted (redirected) to another connection instead of being sent to the originally configured reply target. This
enables sophisticated multi-protocol workflows.

## Overview

When a connection source receives a command, the response to that command would normally be sent back through the same
connection's reply target. With response diversion, you can configure the response to be sent through a different
connection entirely or also (duplicated).

## Configuration

Response diversion is configured at the connection source and target level
using header mapping and specific config.
The following special headers control the diversion behavior:

### Source connection configuration

#### Header mapping keys

#### divert-response-to-connection

Specifies the target connection ID where responses should be diverted.

- **Static configuration**: Set to a specific connection ID (e.g., `"target-connection-123"`)
- **Dynamic configuration**: Don't set as header mapping but in a payload mapper.

#### divert-expected-response-types

Specify which response types should be diverted. Value should be a comma-separated list of response types.

Supported response types:

- `response`: Normal command responses
- `error`: Error responses
- `nack`: Negative acknowledgements

**Default behavior**: If not specified, (`response,error`) response types will be diverted.

#### Specific config keys

##### is-diversion-source

Set this to `"true"` in the specific config of the source connection to enable response diversion.
If not set, response diversion will be disabled regardless of header mapping configuration.

##### preserve-normal-response-via-source

Set this to `"true"` in the specific config of the source connection to preserve sending responses via the original
source connection in addition to diverting them.
If not set or set to `"false"`, responses will only be sent via the diversion target connection.

### Target connection configuration

The target connection must be configured to accept diverted responses.


1. The targets in the target connection should be in the same order as the sources are defined in the source connection.
   This means that the first target in the target connection will receive responses for commands coming from the first
   source in the source connection,
   the second target will receive responses for commands from the second source, and so on.
2. The target connection is registered as diversion target, which is done by setting the "is-diversion-target" = "true"
   property in the specific config.
3. The target connection must authorize the source connection as a valid source of diverted responses. This is done by
   setting the "authorized-connections-as-sources" property in the specific config of the target connection to a
   comma-separated list of source connection IDs that are allowed to divert responses to this target connection.
   If not set, no source connections are authorized. Diverted responses will be dropped and there will be a WARNING log.
4. Leave the topics empty in the target connection. This is to ensure no other events are pushed to the target connection.

## Configuration examples

### Static diversion configuration

Configure the source connection to always divert responses to a specific target connection:

```json
{
    "name": "MQTT Source",
    "connectionType": "mqtt",
    "connectionStatus": "open",
    "sources": [
        {
            "addresses": [
                "commands/device1"
            ],
            "authorizationContext": [
                "ditto:inbound-auth-subject"
            ],
            "headerMapping": {
                "divert-response-to-connection": "http-webhook-connection",
                "divert-expected-response-types": "response,error"
            }
        }
    ],
    "specificConfig": {
        "is-diversion-source": "true",
        "preserve-normal-response-via-source": "false"
    }
}
```

Configure the target connection to accept diverted responses:

```json
{
    "id": "http-webhook-connection",
    "connectionType": "http-push",
    "connectionStatus": "open",
    "uri": "https://webhook.example.com",
    "sources": [],
    "targets": [
        {
            "address": "POST:/api/v1/device-responses/{{ thing:id }}",
            "topics": [],
            "authorizationContext": [
                "ditto:response-publisher"
            ]
        }
    ],
    "specificConfig": {
        "is-diversion-target": "true",
        "authorized-connections-as-sources": "mqtt-source-connection"
    }
}
```

In this example:

- Commands received on `commands/device1` will have their responses diverted
- Responses and errors will be sent to the connection with ID `http-webhook-connection`

### Dynamic diversion configuration

For dynamic diversion where the target connection is determined at runtime by a JavaScript mapper,
you can omit the static `divert-response-to-connection` header mapping but 

### Example JS mapper for dynamic diversion

```javascript
function mapToDittoProtocolMsg(headers, textPayload, bytePayload, contentType) {
    let parsedPayload = JSON.parse(textPayload);

    // Determine target connection based on device type or other criteria
    let targetConnection;
    if (parsedPayload.deviceType === "sensor") {
        targetConnection = "sensor-analytics-connection";
    } else if (parsedPayload.deviceType === "actuator") {
        targetConnection = "actuator-control-connection";
    } else {
        targetConnection = "default-processing-connection";
    }

    let dittoHeaders = {
        "correlation-id": headers["correlation-id"],
        "divert-response-to-connection": targetConnection,
        "divert-expected-response-types": "response,error"
    };

    return Ditto.buildDittoProtocolMsg(
            parsedPayload.namespace,
            parsedPayload.name,
            "things",
            "twin",
            "commands",
            "modify",
            "/attributes",
            dittoHeaders,
            parsedPayload.value
    );
}
```