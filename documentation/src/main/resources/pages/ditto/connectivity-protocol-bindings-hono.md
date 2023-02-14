---
title: Eclipse Hono binding
keywords: binding, protocol, hono, kafka, kafka2
tags: [connectivity]
permalink: connectivity-protocol-bindings-hono.html
---

Consume messages from Eclipse Hono through Apache Kafka brokers and send messages to 
Eclipse Hono the same manner as [Kafka connection](connectivity-protocol-bindings-kafka2.html) does.

This connection type is implemented just for convenience - to avoid the need the user to be aware of the specific 
header mappings, address formats and Kafka specificConfig, which are required to connect to Eclipse Hono. 
These specifics are applied automatically at runtime for the connections of type Hono.

Hono connection is based on Kafka connection and uses it behind the scenes, so most of the 
[Kafka connection documentation](connectivity-protocol-bindings-kafka2.html) is valid for Hono connection too, 
but with some exceptions, described bellow.

#### Important note
During the creation of hono connection, the connection ID must be provided to be the same as `Hono-tenantId`. This is needed to match the Kafka topics (aka connection addresses) to the topics on which Hono will send and listen to. See bellow sections [Source addresses](#source-addresses), [Source reply target](#source-reply-target) and [Target Address](#target-address)

## Specific Hono connection configuration

### Connection URI
In Hono connection definition, property `uri` should not be specified (any specified value will be ignored). 
The connection URI and credentials are common for all Hono connections and are derived from the configuration of the connectivity service.
`uri` will be automatically generated, based on values of 3 configuration properties of connectivity service - 
`ditto.connectivity.hono.base-uri`, `ditto.connectivity.hono.username` and `ditto.connectivity.hono.password`.
Property `base-uri` must specify protocol, host and port number 
(see the [example below](#configuration-example)). 
In order to connect to Kafka brokers, at runtime `username` and `password` values will be inserted between 
protocol identifier and the host name of `base-uri` to form the connection URI like this `tcp://username:password@host.name:port`

Note: If any of these parameters has to be changed, the service must be restarted to apply the new values.

### Source format
#### Source addresses
For a Hono connection source "addresses" are specified as aliases, which are resolved at runtime to Kafka topics to subscribe to. 
Valid source addresses (aliases) are `event`, `telemetry` and `command_response`. 
Runtime, these are resolved as following:
* `event` -> `{%raw%}hono.event.{{connection:id}}{%endraw%}`
* `telemetry` -> `{%raw%}hono.telemetry.{{connection:id}}{%endraw%}`
* `command_response` -> `{%raw%}hono.command_response.{{connection:id}}{%endraw%}`

Note: The `{%raw%}{{connection:id}}{%endraw%}` will be replaced by the value of connectionId

#### Source reply target
Similar to source addresses, the reply target `address` is an alias as well. The single valid value for it is `command`. 
It is resolved to Kafka topic/key like this:
* `command` -> `{%raw%}hono.command.{{connection:id}}/<thingId>{%endraw%}` (&lt;thingId> is substituted by thing ID value).

Note: The `{%raw%}{{connection:id}}{%endraw%}` will be replaced by the value of connectionId

The needed header mappings for the `replyTarget` are also populated automatically at runtime and there is 
no need to specify them in the connection definition. Any of the following specified value will be substituted (i.e. ignored).
Actually the `headerMapping` subsection is not required and could be omitted at all (in the context of `replyTarget`).

For addresses `telemetry` and `event`, the following header mappings will be automatically applied:
* `device_id`: `{%raw%}{{ thing:id }}{%endraw%}`
* `subject`: `{%raw%}{{ header:subject \| fn:default(topic:action-subject) \| fn:default(topic:criterion) }}{%endraw%}-response`
* `correlation-id`: `{%raw%}{{ header:correlation-id }}{%endraw%}`

For address `command_response`, the following header mappings will be automatically applied:
* `correlation-id`: `{%raw%}{{ header:correlation-id }}{%endraw%}`
* `status`: `{%raw%}{{ header:status }}{%endraw%}`

Note: Any other header mappings defined manually will be merged with the auto-generated ones.

The following example shows a valid Hono-connection source:
```json
{
  "addresses": ["event"],
  "consumerCount": 1,
  "qos": 1,
  "authorizationContext": ["ditto:inbound-auth-subject"],
  "enforcement": {
    "input": "{%raw%}{{ header:device_id }}{%endraw%}",
    "filters": ["{%raw%}{{ entity:id }}{%endraw%}"]
  },
  "headerMapping": {},
  "payloadMapping": ["Ditto"],
  "replyTarget": {
    "enabled": true,
    "address": "command",
    "expectedResponseTypes": ["response", "error", "nack"]
  },
  "acknowledgementRequests": {
    "includes": []
  },
  "declaredAcks": []
}
```
#### Source header mapping

Hono connection does not need any header mapping for sources. Anyway, the header mappings documented for 
[Kafka connection](connectivity-protocol-bindings-kafka2.html) are still available.
See [Source header mapping](connectivity-protocol-bindings-kafka2.html#source-header-mapping) in Kafka protocol bindings 
and [Header mapping for connections](connectivity-header-mapping.html).

### Target format
#### Target address 
The target `address` is specified as an alias and the only valid alias is `command`. 
It is automatically resolved at runtime to the following Kafka topic/key:
* `command` -> `{%raw%}hono.command.{{connection:id}}/<thingId>{%endraw%}` (&lt;thingId> is substituted by thing ID value).

Note: The `{%raw%}{{connection:id}}{%endraw%}` will be replaced by the value of connectionId

#### Target header mapping
The target `headerMapping` section is also populated automatically at runtime and there is
no need to specify it the connection definitionm i.e. could be omitted.   
If any of the following keys are specified in the connection will be ignored and automatically substituted as follows:
* `device_id`: `{%raw%}{{ thing:id }}{%endraw%}`
* `subject`: `{%raw%}{{ header:subject \| fn:default(topic:action-subject) }}{%endraw%}`
* `response-required`: `{%raw%}{{ header:response-required }}{%endraw%}`
* `correlation-id`: `{%raw%}{{ header:correlation-id }}{%endraw%}`

Note: Any other header mappings defined manually will be merged with the auto-generated ones.

The following example shows a valid Hono-connection target:
```json
{
  "address": "command",
  "topics": [
    "_/_/things/twin/events",
    "_/_/things/live/messages"
  ],
  "authorizationContext": ["ditto:outbound-auth-subject"]
}
```

### Specific configuration properties

The properties needed by Kafka server in section `specificConfig` with the following keys will be automatically added at runtime to the connection. 
Any manually specified definition of `bootstrapServers` and `saslMechanism` will be ignored, but `groupId` will not.
* `bootstrapServers` The value will be taken from configuration property `ditto.connectivity.hono.bootstrap-servers` of connectivity service. 
It must contain a comma separated list of Kafka bootstrap servers to use for connecting to (in addition to automatically added connection uri).
* `saslMechanism` The value will be taken from configuration property `ditto.connectivity.hono.sasl-mechanism`. 
The value must be one of `SaslMechanism` enum values to select the SASL mechanisms to use for authentication at Kafka:
    * `PLAIN`
    * `SCRAM-SHA-256`
    * `SCRAM-SHA-512`
* `groupId`: could be specified by the user, but not required. If omitted, the value of the connection ID will be automatically used.

Hono connection still allows to manually specify additional properties (like `debugEnabled`), which will be merged with auto-generated ones.
If no additional properties are needed, the whole section `specificConfig` could be omitted.

### Certificate validation
The connection property `validateCertificates` is also set automatically. The value is taken from `ditto.connectivity.hono.validate-certificates` property.
For more details see [Connection configuration](connectivity-tls-certificates.html).

## Examples
### Example of Hono connection
```json
{
  "connection": {
    "id": "hono-example-connection-123",
    "connectionType": "hono",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "sources": [
      {
        "addresses": ["event"],
        "consumerCount": 1,
        "qos": 1,
        "authorizationContext": ["ditto:inbound-auth-subject"],
        "enforcement": {
          "input": "{%raw%}{{ header:device_id }}{%endraw%}",
          "filters": ["{%raw%}{{ entity:id }}{%endraw%}"]
        },
        "headerMapping": {},
        "payloadMapping": ["Ditto"],
        "replyTarget": {
          "enabled": true,
          "address": "command",
          "expectedResponseTypes": ["response", "error", "nack"]
        },
        "acknowledgementRequests": {
          "includes": []
        },
        "declaredAcks": []
      }
    ],
    "targets": [
      {
        "address": "command",
        "topics": [
          "_/_/things/twin/events",
          "_/_/things/live/messages"
        ],
        "authorizationContext": ["ditto:outbound-auth-subject"]
      }
    ]
  }
}
```
### Configuration example
Here is an example with all the configurations of connectivity, service which are needed by Hono connections:
```
ditto {
  connection {
    hono {
      base-uri = "tcp://localhost:9092"
      username = "honoUsername"
      password = "honoPassword"
      sasl-mechanism = "PLAIN"
      bootstrap-servers = "localhost:9092"
      validate-certificates = false
    }
  }
}
```
## Troubleshooting Hono connection configuration
To help the troubleshooting, a separate Piggyback command `retrieveHonoConnection` is implemented. 
It is valid only for Hono connections. It returns the "real" Hono connection after all its properties being resolved or auto-generated.
The returned value could be used for inspection, but not for example to create a new Hono connection using it.
