---
title: "Using Eclipse Hono's Command&Control with Eclipse Ditto"
published: true
permalink: 2018-12-05-example-command-and-control.html
layout: post
author: dominik_guggemos
tags: [blog]
hide_sidebar: true
sidebar: false
toc: false
---

With [version 0.8.0](release_notes_080.html) Eclipse Ditto can now interact with Eclipse Hono using the 
"Command & Control" feature. It is possible to send a 
Thing (or Feature) message at the Ditto Message API, which is then forwarded to Hono as a 
command message. Hono routes the message to the device, which in turn can send a response to the command including a 
status, telling if the command was successfully processed or not. 
This response is routed back via Hono to the Ditto Message API.

In this example we connect the [Ditto sandbox](https://ditto.eclipseprojects.io/) and the 
[Hono sandbox](https://www.eclipse.org/hono/sandbox/) to send a message (3) to a simulated device via the 
Ditto Messages API. The device receives the command from the Hono HTTP Adapter and responds with a message (4) 
that is routed back to the caller at the Ditto Message API (5). For the sake of simplicity we use simple curl 
commands both for the Ditto and Hono HTTP APIs.

The following steps are covered in this example:

1. Setup a connection between Eclipse Ditto and Hono sandboxes
1. Signal availability of the device
1. Send a Ditto message
1. Device receives command and sends command response
1. Caller receives response at Ditto Message API

![Command and Control](images/blog/2018-12-14-command-and-control.png)

### Prerequisites: A Ditto digital twin and a Hono device 

The creation of a Hono device and Ditto digital twin has already been covered in the blog post 
[Connecting Eclipse Ditto to Eclipse Hono](2018-05-02-connecting-ditto-hono.html). For brevity we will just list the 
required commands to create a twin/device here. For a detailed explanation of the steps please refer to the previous 
post. 

#### Create Hono device
```bash
# setup a tenant
$ curl -X POST -i -H 'Content-Type: application/json' -d '{"tenant-id": "org.eclipse.ditto"}' http://hono.eclipse.org:28080/tenant
# create a device
$ curl -X POST -i -H 'Content-Type: application/json' -d '{"device-id": "org.eclipse.ditto:teapot"}' http://hono.eclipse.org:28080/registration/org.eclipse.ditto
# add device credentials
$ PWD_HASH=$(echo -n 'teapot' | openssl dgst -binary -sha512 | base64 -w 0)
$ curl -X POST -i -H 'Content-Type: application/json' -d '{
  "device-id": "org.eclipse.ditto:teapot",
  "type": "hashed-password",
  "auth-id": "teapot",
  "secrets": [{
      "hash-function" : "sha-512",
      "pwd-hash": "'$PWD_HASH'"
  }]
}' http://hono.eclipse.org:28080/credentials/org.eclipse.ditto
```

#### Create Ditto policy for digital twin
```bash
# create policy in Ditto
$ curl -X PUT -i -u demo5:demo -H 'Content-Type: application/json' -d '{
   "policyId": "org.eclipse.ditto:teapot-policy",
   "entries": {
       "DEMO": {
           "subjects": {
              "nginx:demo5": {
                  "type": "basic auth user and user used in connection's authorizationContext"
              }
           },
           "resources": {
               "thing:/": {
                   "grant": ["READ", "WRITE"],
                   "revoke": []
               },
               "policy:/": {
                   "grant": ["READ", "WRITE"],
                   "revoke": []
               },
               "message:/": {
                   "grant": ["READ", "WRITE"],
                   "revoke": []
               }
           }
       }
   }
}' https://ditto.eclipseprojects.io/api/2/policies/org.eclipse.ditto:teapot-policy 
```

#### Create Ditto digital twin
```bash
# create thing in Ditto
$ curl -X PUT -i -u demo5:demo -H 'Content-Type: application/json' -d '{
    "policyId": "org.eclipse.ditto:teapot-policy",
    "features": {
      "water": {
        "properties": {
          "temperature": 20
        }
      }
    }
}' https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:teapot 
```

### Setup a connection for Command & Control
In order to forward Ditto Messages to the device as a Hono command we first need to setup and configure a 
[connection](connectivity-manage-connections.html) between Eclipse Ditto and Eclipse Hono that is prepared for 
_Command & Control_ messages. According to the 
[Hono documentation](https://www.eclipse.org/hono/) 
the connection must contain a target with the address `control/<tenant-id>/<device-id>` 
and a source with the address `control/<tenant-id>/<reply-identifier>`. The `reply-identifier` can be 
chosen arbitrarily, but must be set as the `reply-to` header of a command exactly as defined in the connection: 

```bash
curl -X POST -i -u devops:devopsPw1! \
     -H 'Content-Type: application/json' \
     -d '{
           "targetActorSelection": "/system/sharding/connection",
           "headers": {
             "aggregate": false
           },
           "piggybackCommand": {
             "type": "connectivity.commands:createConnection",
             "connection": {
               "id": "command-and-control-connection",
               "connectionType": "amqp-10",
               "connectionStatus": "open",
               "uri": "amqp://consumer%40HONO:verysecret@hono.eclipse.org:15672",
               "failoverEnabled": true,
               "sources": [{
                   "addresses": [
                     "control/org.eclipse.ditto/replies"
                   ],
                   "authorizationContext": [
                     "nginx:demo5"
                   ],
                   "headerMapping": {
                     "correlation-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
                     "status": "{%raw%}{{ header:status }}{%endraw%}",
                     "content-type": "{%raw%}{{ header:content-type }}{%endraw%}"
                   }
                 }
               ],
               "targets": [{
                   "address": "control/org.eclipse.ditto/{%raw%}{{ thing:name }}{%endraw%}",
                   "authorizationContext": [
                     "nginx:demo5"
                   ],
                   "topics":["_/_/things/live/messages"],
                   "headerMapping": {
                     "message-id": "{%raw%}{{ header:correlation-id }}{%endraw%}",
                     "subject": "{%raw%}{{ topic:subject }}{%endraw%}",
                     "content-type": "application/vnd.eclipse.ditto+json",
                     "reply-to": "control/org.eclipse.ditto/replies"
                   }
                 }
               ]
             }
           }
         }' \
    https://ditto.eclipseprojects.io/devops/piggyback/connectivity?timeout=10s
``` 

As described in the 
[Hono API description](https://www.eclipse.org/hono/) 
a command message has three mandatory properties: `correlation-id`, `subject` and `reply-to`, these are defined in the 
target header mapping of the connection. 
The source header mapping defines a mapping for `correlation-id` and `status` to internal headers, they are required 
to properly map the Hono command response to a Ditto message response.   

### Signal availability of device

As we are using the Hono HTTP Adapter to connect our device, send telemetry and receive commands, the designated way 
is therefor to signal readiness to receive a command by specifying the `hono-ttd` parameter on an arbitrary event (for 
detailed description please consult the 
[Hono HTTP Adapter] (https://www.eclipse.org/hono/) 
guide).

```bash
curl -X POST -i -u teapot@org.eclipse.ditto:teapot -H 'hono-ttd: 60' -H 'Content-Type: application/json' \
     -d '{
           "topic": "org.eclipse.ditto/teapot/things/twin/commands/modify",
           "path": "/features/water/properties/temperature",
           "value": 23
         }' \
     http://hono.eclipse.org:8080/telemetry
```

The request is now open to receive a command for 60 seconds before it is terminated.

### Send a Ditto message

Now we can use the [Ditto Messages API](protocol-specification-things-messages.html#using-the-messages-api) to send a 
message to the device waiting for a command: 
```bash
curl -i -X POST 'https://ditto.eclipseprojects.io/api/2/things/org.eclipse.ditto:teapot/inbox/messages/brew?timeout=60' \
     -u demo5:demo \
     -H 'x-correlation-id: command-and-control' \
     -d '{"targetTemperature":85}'
```

### Device receives the command

The message is forwarded to Hono as configured in the connection and finally terminates the pending request we opened 
before with a status code of `200 OK`:
```
HTTP/1.1 200 OK
hono-command: brew
hono-cmd-req-id: 013command-and-controlreplies
Content-Type: application/octet-stream
Content-Length: 17
Connection: Keep-Alive
``` 

Hono adds two headers besides the standard HTTP headers: `hono-command` and `hono-cmd-req-id`. 
`hono-command` contains the subject of the message and `hono-cmd-req-id` identifies the messages and is used to 
correlate the request and the response we are now going to send.

### Device sends a command response

We use the header value of `hono-cmd-req-id` to construct the response address: 
`http://hono.eclipse.org:8080/control/res/013command-and-controlreplies` 

Another curl command completes the roundtrip with a response from the simulated device:

```bash
curl -i -X POST -u teapot@org.eclipse.ditto:teapot \
     -H 'Content-Type: application/json' \
     -H 'hono-cmd-status: 200' \
     -d '{
           "topic": "org.eclipse.ditto/teapot/things/live/messages/brew",
           "headers": {
             "content-type": "application/json",
             "correlation-id": "command-and-control"
           },
           "path": "/inbox/messages/brew",
           "value": { "eta": 56},
           "status": 200
         }' \
     http://hono.eclipse.org:8080/control/res/013command-and-controlreplies
```

### Message response is received at Ditto Message API
And finally we receive the command response at the Ditto Message API where we sent the original message:

```
HTTP/1.1 200 OK
correlation-id: command-and-control
message-id: command-and-control
status: 200
Content-Type: application/json
Content-Length: 10

{"eta":56}
```

### Alternative: Receive command and send response via MQTT

Alternatively we can also receive the command by subscribing to the MQTT topic `control/+/+/req/#` at the 
[Hono MQTT Adapter](https://www.eclipse.org/hono/):
```bash
$ mosquitto_sub -d -h hono.eclipse.org -p 8883 -v -u 'teapot@org.eclipse.ditto' -P teapot -t 'control/+/+/req/#'
```

And also publish the command response on the MQTT topic `control///res/013command-and-controlreplies/200`:
```bash
mosquitto_pub -d -h hono.eclipse.org -p 8883 -u 'teapot@org.eclipse.ditto' -P teapot \
              -t control///res/013command-and-controlreplies/200 \
              -m '{
                    "topic": "org.eclipse.ditto/teapot/things/live/messages/brew",
                    "headers": {
                      "content-type": "application/json",
                      "correlation-id": "command-and-control"
                    },
                    "path": "/inbox/messages/brew",
                    "value": {
                      "eta": 58
                    },
                    "status": 200
                  }'
```

If you have any wishes, improvements, are missing something
or just want to get in touch with us, you can use one of
our [feedback channels](feedback.html).


{% include image.html file="ditto.svg" alt="Ditto" max-width=500 %}
--<br/>
The Eclipse Ditto team
