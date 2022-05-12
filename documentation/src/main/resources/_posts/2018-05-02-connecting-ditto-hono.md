---
title: "Connecting Eclipse Ditto to Eclipse Hono"
published: true
permalink: 2018-05-02-connecting-ditto-hono.html
layout: post
author: thomas_jaeckle
tags: [blog, connectivity]
hide_sidebar: true
sidebar: false
toc: true
---

{% include warning.html content="This guide does no longer work with the latest (1.x) versions of Ditto + Hono. Please take a look and make use of the [Eclipse IoT Packages \"cloud2edge\" package](https://www.eclipse.org/packages/packages/cloud2edge/) in order to setup and automatically connect Ditto + Hono." %}

With the recently released Ditto milestone [0.3.0-M1](2018-04-26-milestone-announcement-030-M1.html) the `connectivity`
to AMQP 1.0 endpoints can now be established in a durable and stable way (including failovers, etc.).

That means Ditto now is ready to be connected to [Eclipse Hono's](https://www.eclipse.org/hono/) "northbound" API which
is provided via AMQP 1.0.<br />
By doing so it is for example possible to receive Hono telemetry 
messages (see heading "Northbound Operations") which a device `demo-device` connected to the "southbound" of Hono sends 
via HTTP or MQTT (the currently available protocol adapters of Hono) in Ditto.<br />
When received, the payload can be translated into a format Ditto understands in order to update the 
[digital twin](intro-digitaltwins.html) of the `demo-device` device and provide API access to the twin, e.g. via `HTTP`
or `WebSocket`.

This blog post walks through the steps required to connect Ditto and Hono by adding a connection between the Hono and 
Ditto sandboxes at

* [hono.eclipse.org](http://hono.eclipse.org) 
* [ditto.eclipseprojects.io](https://ditto.eclipseprojects.io)


## Scenario

The following graphic illustrates the scenario:

{% include image.html file="blog/2018-05-02-ditto-hono-digital-twin.png" alt="Ditto-Hono digital twin" caption="Scenario for providing a digital twin in Ditto of a device connected via Hono" max-width=469 %}


Let's assume for this tutorial that we have a device (e.g. containing a sensor) `demo-device` which is capable of 
measuring temperature and humidity. 

This device sends the sensor telemetry data every 5 minutes via MQTT into the cloud in either of the following formats:

```json
{
  "temp": 23.42,
  "hum": 44.42
}
```

```json
{
  "temp": 23.42
}
```

```json
{
  "hum": 44.42
}
```

We want to create a digital twin for this device in order to access the device's sensor data as API via Eclipse Ditto.


## Steps in Hono

The steps in order to get started with Eclipse Hono are documented in the 
[Hono getting started](https://www.eclipse.org/hono/getting-started/) and in a new 
[Blog post about using multi-tenancy in Eclipse Hono](https://blog.bosch-si.com/developer/using-multi-tenancy-in-eclipse-hono/). 
We show them very briefly here as well but in order to comprehend what and why we are doing what we do please consult 
the Hono documentation.


### Create a tenant

First of all, create a new Hono tenant (we chose the tenant name `org.eclipse.ditto`):

```bash
$ curl -X POST -i -H 'Content-Type: application/json' -d '{"tenant-id": "org.eclipse.ditto"}' http://hono.eclipse.org:28080/tenant
```

### Register a device

Register a new device in Hono (we chose the device-id `demo-device`):

```bash
$ curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "demo-device"}' http://hono.eclipse.org:28080/registration/org.eclipse.ditto
```

### Add a device credential

In order for the device being able to send telemetry it needs to authenticate. For that we will need to add a credential
for that device in Hono.

We choose the `hashed-password` type:

```bash
$ PWD_HASH=$(echo -n 'demo-device-password' | openssl dgst -binary -sha512 | base64 -w 0)
$ curl -X POST -i -H 'Content-Type: application/json' -d '{
  "device-id": "demo-device",
  "type": "hashed-password",
  "auth-id": "demo-device-auth",
  "secrets": [{
      "hash-function" : "sha-512",
      "pwd-hash": "'$PWD_HASH'"
  }]
}' http://hono.eclipse.org:28080/credentials/org.eclipse.ditto
```

### Publish data

You are now able to publish `telemetry` (or also `event`) data via the Hono HTTP adapter:

```bash
$ curl -X POST -i -u demo-device-auth@org.eclipse.ditto:demo-device-password -H 'Content-Type: application/json' -d '{"temp": 23.07}' http://hono.eclipse.org:8080/telemetry
$ curl -X POST -i -u demo-device-auth@org.eclipse.ditto:demo-device-password -H 'Content-Type: application/json' -d '{"hum": 45.85}'  http://hono.eclipse.org:8080/telemetry
```

However as there is not yet a `consumer` listening for the messages, the Hono HTTP adapter will for example return an
error code `503 - Service unavailable` when publishing a `telemetry` message.
 
Alternatively you can also publish telemetry data via MQTT:

```bash
$ mosquitto_pub -u 'demo-device-auth@org.eclipse.ditto' -P demo-device-password -t telemetry -m '{"temp": 23.07}'
$ mosquitto_pub -u 'demo-device-auth@org.eclipse.ditto' -P demo-device-password -t telemetry -m '{"hum": 45.85}'
```

In the following steps we will register the missing `consumer` in Ditto by creating a connection to the Hono tenant 
in Ditto's connectivity.


## Steps in Ditto

We want to create a digital twin of the device connected to Eclipse Hono in order to access its latest reported state 
via the Ditto [HTTP API](httpapi-overview.html), in order to be able to find it in a population of digital twins or
in order to be notified about changed via an API optimized for the web.

### Create a digital twin

The first step is to create a skeleton for the digital twin by creating a Ditto `Thing`.<br />
Notice that we authenticate with the sandbox user `demo5` - a default [Policy](basic-policy.html) is implicitly 
created so that only that user may read+write the created `Thing`.

```bash
$ curl -X PUT -i -u demo5:demo -H 'Content-Type: application/json' -d '{
    "attributes": {
        "location": "Germany"
    },
    "features": {
        "temperature": {
            "properties": {
                "value": null
            }
        },
        "humidity": {
            "properties": {
                "value": null
            }
        }
    }
}' https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:demo-device
```

Make sure the digital twin was created:

```bash
$ curl -i -u demo5:demo https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:demo-device
```

### Create a connection to Hono

Ditto recently added support for [managing connections](connectivity-manage-connections.html) to foreign endpoints 
(currently to AMQP 1.0 or to AMQP 0.9.1). As Hono provides an AMQP 1.0 endpoint, a connection can be added in Ditto 
which connects to Hono and acts as a "northbound" `consumer`.

The following configuration for the connection has to be applied:

* AMQP 1.0 hostname: `hono.eclipse.org`
* AMQP 1.0 port: `15672`
* username: `consumer@HONO`
* password: `verysecret`
* sources:
    * `telemetry/org.eclipse.ditto`
    * `event/org.eclipse.ditto`

#### Test the connection

Send the following "test connection" command via HTTP in order to test if the Ditto sandbox can connect to the Hono one.

```bash
$ curl -X POST -i -u devops:devopsPw1! -H 'Content-Type: application/json' -d '{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:testConnection",
        "connection": {
            "id": "hono-sandbox-connection-1",
            "connectionType": "amqp-10",
            "connectionStatus": "open",
            "uri": "amqp://consumer%40HONO:verysecret@hono.eclipse.org:15672",
            "failoverEnabled": true,
            "sources": [{
                "addresses": [
                    "telemetry/org.eclipse.ditto",
                    "event/org.eclipse.ditto"
                ],
                "authorizationContext": ["nginx:demo5"]
            }]
        }
    }
}' https://ditto.eclipseprojects.io/devops/piggyback/connectivity?timeout=8s
```

The result should be:

```json
{
    "type": "connectivity.responses:testConnection",
    "status": 200,
    "connectionId": "hono-sandbox-connection-1",
    "testResult": "ditto-cluster=Success(successfully connected + initialized mapper)"
}
```

Great, it looks like with the provided credentials we can connect to the Hono sandbox.

#### Define a payload mapping

In the [scenario](#scenario) we described the payloads our device sends via MQTT. As those JSON payloads are missing
some information required for Ditto to map it to a [Ditto Protocol](protocol-overview.html) message Ditto uses for 
updating the digital twin, we have to configure a [payload mapping](connectivity-mapping.html) in order to add the 
missing information.

Whenever one of the 3 following messages arrives at Ditto's `consumer`, a payload mapping should be performed:

```json
{
  "temp": 23.42,
  "hum": 44.42
}
```

```json
{
  "temp": 23.42
}
```

```json
{
  "hum": 44.42
}
```

A JavaScript based mapping which exactly does this could look like this:

```javascript
function mapToDittoProtocolMsg(
    headers,
    textPayload,
    bytePayload,
    contentType
) {

    if (contentType !== "application/json") {
        return null; // only handle messages with content-type application/json
    }

    var jsonData = JSON.parse(textPayload);
    var temperature = jsonData.temp;
    var humidity = jsonData.hum;
    
    var path;
    var value;
    if (temperature != null && humidity != null) {
        path = "/features";
        value = {
            temperature: {
                properties: {
                    value: temperature
                }
            },
            humidity: {
                properties: {
                    value: humidity
                }
            }
        };
    } else if (temperature != null) {
        path = "/features/temperature/properties/value";
        value = temperature;
    } else if (humidity != null) {
        path = "/features/humidity/properties/value";
        value = humidity;
    }
    
    if (!path || !value) {
        return null;
    }

    return Ditto.buildDittoProtocolMsg(
        "org.eclipse.ditto",     // the namespace we use
        headers["device_id"],    // Hono sets the authenticated device-id in this header
        "things",                // it is a Thing entity we want to update
        "twin",                  // we want to update the twin
        "commands",
        "modify",                // command = modify
        path,
        headers,                 // copy all headers as Ditto headers
        value
    );
}
```

In order to add this script to the connection we want to create, the newlines have to be replaced by `\n` so that
the script fits in a single line JSON string and the `"` characters have to be replaced with `\"`: 

```json
"function mapToDittoProtocolMsg(\n    headers,\n    textPayload,\n    bytePayload,\n    contentType\n) {\n\n    if (contentType !== \"application/json\") {\n        return null;\n    }\n\n    var jsonData = JSON.parse(textPayload);\n    var temperature = jsonData.temp;\n    var humidity = jsonData.hum;\n    \n    var path;\n    var value;\n    if (temperature != null && humidity != null) {\n        path = \"/features\";\n        value = {\n                temperature: {\n                    properties: {\n                        value: temperature\n                    }\n                },\n                humidity: {\n                    properties: {\n                        value: humidity\n                    }\n                }\n            };\n    } else if (temperature != null) {\n        path = \"/features/temperature/properties/value\";\n        value = temperature;\n    } else if (humidity != null) {\n        path = \"/features/humidity/properties/value\";\n        value = humidity;\n    }\n    \n    if (!path || !value) {\n        return null;\n    }\n\n    return Ditto.buildDittoProtocolMsg(\n        \"org.eclipse.ditto\",\n        headers[\"device_id\"],\n        \"things\",\n        \"twin\",\n        \"commands\",\n        \"modify\",\n        path,\n        headers,\n        value\n    );\n}"
```

#### Create the connection

We use the payload of the previous "test connection" command and add the JavaScript mapping script from above in order
to specify the "create connection" command, which we will use to create the connection between Eclipse Hono and Ditto:

```bash
$ curl -X POST -i -u devops:devopsPw1! -H 'Content-Type: application/json' -d '{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:createConnection",
        "connection": {
            "id": "hono-sandbox-connection-1",
            "connectionType": "amqp-10",
            "connectionStatus": "open",
            "uri": "amqp://consumer%40HONO:verysecret@hono.eclipse.org:15672",
            "failoverEnabled": true,
            "sources": [{
                "addresses": [
                    "telemetry/org.eclipse.ditto",
                    "event/org.eclipse.ditto"
                ],
                "authorizationContext": ["nginx:demo5"]
            }],
            "mappingContext": {
                "mappingEngine": "JavaScript",
                "options": {
                    "incomingScript": "function mapToDittoProtocolMsg(\n    headers,\n    textPayload,\n    bytePayload,\n    contentType\n) {\n\n    if (contentType !== \"application/json\") {\n        return null;\n    }\n\n    var jsonData = JSON.parse(textPayload);\n    var temperature = jsonData.temp;\n    var humidity = jsonData.hum;\n    \n    var path;\n    var value;\n    if (temperature != null && humidity != null) {\n        path = \"/features\";\n        value = {\n                temperature: {\n                    properties: {\n                        value: temperature\n                    }\n                },\n                humidity: {\n                    properties: {\n                        value: humidity\n                    }\n                }\n            };\n    } else if (temperature != null) {\n        path = \"/features/temperature/properties/value\";\n        value = temperature;\n    } else if (humidity != null) {\n        path = \"/features/humidity/properties/value\";\n        value = humidity;\n    }\n    \n    if (!path || !value) {\n        return null;\n    }\n\n    return Ditto.buildDittoProtocolMsg(\n        \"org.eclipse.ditto\",\n        headers[\"device_id\"],\n        \"things\",\n        \"twin\",\n        \"commands\",\n        \"modify\",\n        path,\n        headers,\n        value\n    );\n}"
                }
            }
        }
    }
}' https://ditto.eclipseprojects.io/devops/piggyback/connectivity?timeout=8s
```

When establishing the connection + parsing the JavaScript worked, we get a success result as HTTP response again, 
otherwise an error message would be returned.


#### Retrieve connection metrics

After the connection was created, we can use the following command in order to retrieve the current connection status
and some metrics about how many messages were consumed:


```bash
$ curl -X POST -i -u devops:devopsPw1! -H 'Content-Type: application/json' -d '{
    "targetActorSelection": "/system/sharding/connection",
    "headers": {
        "aggregate": false
    },
    "piggybackCommand": {
        "type": "connectivity.commands:retrieveConnectionMetrics",
        "connectionId": "hono-sandbox-connection-1"
    }
}' https://ditto.eclipseprojects.io/devops/piggyback/connectivity?timeout=8s
```

The result looks like this:

```json
{
  "type": "connectivity.responses:retrieveConnectionMetrics",
  "status": 200,
  "connectionId": "hono-sandbox-connection-1",
  "containsFailures": false,
  "connectionMetrics": {
    "inbound": {
      "consumed": {
        "success": {
          "PT1M": 2,
          "PT1H": 2,
          "PT24H": 2,
          "lastMessageAt": "2019-02-06T09:37:28.416Z"
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "mapped": {
        "success": {
          "PT1M": 2,
          "PT1H": 2,
          "PT24H": 2,
          "lastMessageAt": "2019-02-06T09:37:28.422Z"
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "dropped": {
        "success": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "enforced": {
        "success": {
          "PT1M": 2,
          "PT1H": 2,
          "PT24H": 2,
          "lastMessageAt": "2019-02-06T09:37:28.422Z"
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      }
    },
    "outbound": {
      "dispatched": {
        "success": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        },
        "failure": {
          "PT1M": 2,
          "PT1H": 2,
          "PT24H": 2,
          "lastMessageAt": "2019-02-06T09:37:28.439Z"
        }
      },
      "filtered": {
        "success": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "mapped": {
        "success": {
          "PT1M": 2,
          "PT1H": 2,
          "PT24H": 2,
          "lastMessageAt": "2019-02-06T09:37:28.443Z"
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "dropped": {
        "success": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      },
      "published": {
        "success": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        },
        "failure": {
          "PT1M": 0,
          "PT1H": 0,
          "PT24H": 0,
          "lastMessageAt": null
        }
      }
    }
  },
  "sourceMetrics": {
    "addressMetrics": {
      "event/org.eclipse.ditto": {
        "consumed": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "mapped": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "dropped": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "enforced": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        }
      },
      "telemetry/org.eclipse.ditto": {
        "consumed": {
          "success": {
            "PT1M": 2,
            "PT1H": 2,
            "PT24H": 2,
            "lastMessageAt": "2019-02-06T09:37:28.416Z"
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "mapped": {
          "success": {
            "PT1M": 2,
            "PT1H": 2,
            "PT24H": 2,
            "lastMessageAt": "2019-02-06T09:37:28.422Z"
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "dropped": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "enforced": {
          "success": {
            "PT1M": 2,
            "PT1H": 2,
            "PT24H": 2,
            "lastMessageAt": "2019-02-06T09:37:28.422Z"
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        }
      }
    }
  },
  "targetMetrics": {
    "addressMetrics": {
      "_responses": {
        "dispatched": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 2,
            "PT1H": 2,
            "PT24H": 2,
            "lastMessageAt": "2019-02-06T09:37:28.439Z"
          }
        },
        "filtered": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "mapped": {
          "success": {
            "PT1M": 2,
            "PT1H": 2,
            "PT24H": 2,
            "lastMessageAt": "2019-02-06T09:37:28.443Z"
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "dropped": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        },
        "published": {
          "success": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          },
          "failure": {
            "PT1M": 0,
            "PT1H": 0,
            "PT24H": 0,
            "lastMessageAt": null
          }
        }
      }
    }
  }
}
```

## Test the integration

Whenever the device now sends telemetry in its own JSON format

* the message count of the [connection metrics in Ditto](#retrieve-connection-metrics) should be increased by one
* the digital twin with the `Thing` ID `org.eclipse.ditto:demo-device` should receive the updated value which is also
  reflected at the twin's HTTP endpoint 
  [https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:demo-device](https://demo5:demo@ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:demo-device)

Verify that by simulate sending telemetry using the Hono HTTP adapter:

```bash
$ curl -X POST -i -u demo-device-auth@org.eclipse.ditto:demo-device-password -H 'Content-Type: application/json' -d '{"temp": 14.51}' http://hono.eclipse.org:8080/telemetry
$ curl -X POST -i -u demo-device-auth@org.eclipse.ditto:demo-device-password -H 'Content-Type: application/json' -d '{"hum": 52.17}'  http://hono.eclipse.org:8080/telemetry

$ curl -X POST -i -u demo-device-auth@org.eclipse.ditto:demo-device-password -H 'Content-Type: application/json' -d '{"temp": 23.07, "hum": 45.85}'  http://hono.eclipse.org:8080/telemetry
```

<br/>
<br/>

Try it out for yourself and give us (the Ditto and the Hono teams) feedback what you like or what could be improved.


<br/>
<br/>
{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
