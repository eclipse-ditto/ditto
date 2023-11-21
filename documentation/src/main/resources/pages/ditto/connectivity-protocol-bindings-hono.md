---
title: Eclipse Hono binding
keywords: binding, protocol, hono, kafka, kafka2
tags: [connectivity]
permalink: connectivity-protocol-bindings-hono.html
---

Consume messages from Eclipse Hono through Apache Kafka brokers and send messages to 
Eclipse Hono in the same manner as the [Kafka connection](connectivity-protocol-bindings-kafka2.html) does.

The Hono connection type is implemented just for convenience - to avoid the need for the user to be aware of the specific 
header mappings, address formats and Kafka `specificConfig` settings, which are required to connect to Eclipse Hono. 
These specifics are applied automatically at runtime for the connections of type Hono.

The Hono connection is based on the Kafka connection and uses it behind the scenes, so most of the 
[Kafka connection documentation](connectivity-protocol-bindings-kafka2.html) is valid for the Hono connection too, 
but with some exceptions, as described below.


{% include note.html
   content="A Hono connection is associated with _one_ Hono tenant. That means for each Hono tenant a separate Hono connection
            needs to be created. The tenant ID is used in the source and target connection addresses, representing the Kafka
            topics used by Hono for sending and receiving messages for this tenant. See below sections [Source addresses](#source-addresses),
            [Source reply target](#source-reply-target) and [Target Address](#target-address). The Hono tenant ID for the connection is defined in the
            [specific config](#specific-configuration-properties) `honoTenantId` property."
%}

## Specific Hono connection configuration

### Connection URI
In the Hono connection definition, the `uri` property should not be specified (any specified value will be ignored). 
The connection URI and credentials are common for all Hono connections and are derived from the [configuration](installation-operating.html#ditto-configuration) of the connectivity service.
`uri` will be automatically generated, based on the values of 3 configuration properties of the connectivity service - 
`ditto.connectivity.hono.base-uri`, `ditto.connectivity.hono.username` and `ditto.connectivity.hono.password`.
The connectivity service property `base-uri` must specify protocol, host and port number (see the [example below](#connectivity-configuration-example)). 
In order to connect to Kafka brokers, `username` and `password` values will be inserted at runtime between the 
protocol identifier and the host name parts of `base-uri`, resulting in a connection URI of the form `tcp://username:password@host.name:port`.

Note: If any of these parameters has to be changed, the service must be restarted to apply the new values.

### Source format
#### Source addresses
For a Hono connection, source "addresses" are specified as aliases, which are resolved at runtime to Kafka topics to subscribe to. 
Valid source addresses (aliases) are `event`, `telemetry` and `command_response`. 
At runtime, these are resolved as follows:
* `event` -> `{%raw%}hono.event.<honoTenantId>{%endraw%}`
* `telemetry` -> `{%raw%}hono.telemetry.<honoTenantId>{%endraw%}`
* `command_response` -> `{%raw%}hono.command_response.<honoTenantId>{%endraw%}`

`{%raw%}<honoTenantId>{%endraw%}` will be replaced by the value of `specificConfig.honoTenantId` or, if not set,
by the connection id.

#### Source reply target
Similar to source addresses, the reply target `address` is an alias as well. The single valid value for it is `command`. 
It is resolved to Kafka topic/key like this:
* `command` -> `{%raw%}hono.command.<honoTenantId>/<thingId>{%endraw%}`

`{%raw%}<honoTenantId>{%endraw%}` will be replaced by the value of `specificConfig.honoTenantId` or, if not set,
by the connection id. `{%raw%}<thingId>{%endraw%}` is substituted by the thing ID value.

The needed header mappings for the `replyTarget` are also populated automatically at runtime and there is 
no need to specify them in the connection definition. Any of the following specified value will be substituted (i.e. ignored).
Actually the `headerMapping` subsection is not required and could be omitted completely (in the context of `replyTarget`).

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

The Hono connection does not need any header mapping for sources. Nevertheless, the header mappings documented for 
[Kafka connection](connectivity-protocol-bindings-kafka2.html) are still available.
See [Source header mapping](connectivity-protocol-bindings-kafka2.html#source-header-mapping) in Kafka protocol bindings 
and [Header mapping for connections](connectivity-header-mapping.html).

### Target format
#### Target address 
The target `address` is specified as an alias and the only valid alias is `command`. 
It is automatically resolved at runtime to the following Kafka topic/key:
* `command` -> `{%raw%}hono.command.<honoTenantId>/<thingId>{%endraw%}`

`{%raw%}<honoTenantId>{%endraw%}` will be replaced by the value of `specificConfig.honoTenantId` or, if not set,
by the connection id. `{%raw%}<thingId>{%endraw%}` is substituted by the thing ID value.

#### Target header mapping
The target `headerMapping` section is also populated automatically at runtime and there is
no need to specify it the connection definition i.e. could be omitted.   
If any of the following keys are specified in the connection, they will be ignored and automatically substituted as follows:
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

In the `specificConfig` section, the Hono tenant of the connection is specified in the `honoTenantId` property.
If that property is not set, the connection ID will be taken as Hono tenant ID.

The following Kafka connection related properties in the `specificConfig` section will be automatically added at runtime
to the connection. Any manually specified definition of `bootstrapServers` and `saslMechanism` will be ignored, but `groupId` will not.
* `bootstrapServers` The value will be taken from the configuration property `ditto.connectivity.hono.bootstrap-servers` of the connectivity service. 
It must contain a comma separated list of Kafka bootstrap servers to use for connecting to (in addition to the automatically added connection uri).
* `saslMechanism` The value will be taken from configuration property `ditto.connectivity.hono.sasl-mechanism`. 
The value must be one of `SaslMechanism` enum values to select the SASL mechanisms to use for authentication at Kafka:
    * `PLAIN`
    * `SCRAM-SHA-256`
    * `SCRAM-SHA-512`
* `groupId`: could be specified by the user, but is not required. If omitted, the value of the connection ID will be automatically used.

Hono connection still allows to manually specify additional properties (like `debugEnabled`), which will be merged with the auto-generated ones.

### Certificate validation
The connection property `validateCertificates` is also set automatically. The value is taken from the `ditto.connectivity.hono.validate-certificates` property.
For more details see [Connection configuration](connectivity-tls-certificates.html).

## Examples
### Example of Hono connection
```json
{
  "connection": {
    "id": "connection-for-hono-example-tenant",
    "connectionType": "hono",
    "connectionStatus": "open",
    "failoverEnabled": true,
    "specificConfig": {
      "honoTenantId": "example-tenant"
    },
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
### Connectivity configuration example
Here is an example with all the configuration options of the connectivity service that are needed by Hono connections:
```
ditto {
  connection {
    hono {
      base-uri = "tcp://localhost:9092"
      username = "honoUsername"
      password = "honoPassword"
      sasl-mechanism = "PLAIN"
      bootstrap-servers = "localhost:9092"
      validateCertificates = true,
      ca = "-----BEGIN CERTIFICATE-----\n<trusted certificate>\n-----END CERTIFICATE-----"
    }
  }
}
```
## Troubleshooting Hono connection configuration
To help the troubleshooting, a separate Piggyback command `retrieveHonoConnection` is implemented. 
It is valid only for Hono connections. It returns the "real" Hono connection after all its properties being resolved or auto-generated.
The returned value could be used for inspection, but not for example to create a new Hono connection using it.
