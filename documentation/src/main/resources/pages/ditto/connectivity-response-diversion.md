---
title: Response diversion
keywords: response, diversion, redirect, routing, multi-protocol
tags: [connectivity]
permalink: connectivity-response-diversion.html
---

## Response diversion

Response diversion is a powerful feature in Ditto's connectivity service that allows responses from one connection to be diverted (redirected) to another connection instead of being sent to the originally configured reply target. This enables sophisticated multi-protocol workflows.

## Overview

When a connection source receives a command, the response to that command would normally be sent back through the same connection's reply target. With response diversion, you can configure the response to be sent through a different connection entirely.

## Configuration

Response diversion is configured at the connection source level
using header mapping and in the target connection in the specific config.
The following special headers control the diversion behavior:

### Header mapping keys

#### divert-response-to-connection
Specifies the target connection ID where responses should be diverted.

- **Static configuration**: Set to a specific connection ID (e.g., `"target-connection-123"`)
- **Dynamic configuration**: Set to `"*"` to enable dynamic diversion where the target connection is determined at runtime through signal headers
- **Disabled**: Omit this header to disable response diversion for the source

#### divert-expected-response-types
Specifies which response types should be diverted. Value should be a comma-separated list of response types.

Supported response types:
- `response`: Normal command responses
- `error`: Error responses
- `nack`: Negative acknowledgements

**Default behavior**: If not specified, all response types (`response,error,nack`) will be diverted.

#### diverted-response-from
This header is automatically added by Ditto to track the source connection ID of diverted responses. It should not be manually configured.

### Target connection configuration
The target connection must be configured to accept diverted responses.
There are few prerequisites for this:
1. The targets in the target connection should be in the same order that the sources are defined in the source connection.
This means that the first target in the target connection will receive responses for commands coming from the first source in the source connection,
the second target will receive responses for commands from the second source, and so on.
2. The target connection is registered as diversion target, which is done by setting the "is-diversion-target"
= "true" property in the specific config.
3. Leave the topics empty in the target connection. They are not used for diverted responses and removing them will ensure no other events are pushed to the target connection.

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
    ]
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
        "is-diversion-target": "true"
    }
}
```

In this example:
- Commands received on `commands/device1` will have their responses diverted
- Responses and errors will be sent to the connection with ID `http-webhook-connection`

### Dynamic diversion configuration

For dynamic diversion where the target connection is determined at runtime by a JavaScript mapper,
you only need to enable the target connection to accept diverted responses:

```json
{
  "addresses": ["commands/+"],
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "headerMapping": {},
  "payloadMapping": ["dynamic-router"],
  "replyTarget": {
    "enabled": true,
    "address": "responses/default"
  }
}
```

With a corresponding JavaScript mapper `dynamic-router`:

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

## Technical behavior

### Circular diversion prevention

Ditto automatically prevents circular diversion chains by:
- Tracking the source connection in the `diverted-response-from` header
- Preventing responses from being diverted back to their originating connection by validating the configuration
- Logging warnings when circular diversion attempts are detected at runtime

## Monitoring and troubleshooting

### Connection metrics
Monitor the following metrics to track diversion performance:
- Response diversion success rate
- Diversion target distribution
- Response latency impact

### Common issues

#### Responses not being diverted
- Verify that `divert-response-to-connection` is correctly configured in the source header mapping
- Check that the target connection ID exists and is active
- Ensure response types match the `divert-expected-response-types` configuration

#### Target connection not receiving responses
- Verify the target connection has appropriate target configured
- Check authorization context permissions
