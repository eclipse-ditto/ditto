---
title: Hello world
tags: [getting_started]
permalink: intro-hello-world.html
---

After [starting Ditto](installation-running.html), we have a HTTP and WebSocket API for your
[digital twins](intro-digitaltwins.html) at our hands.

## Example

Assume we want to create a digital twin for a car. The twin should hold static metadata and dynamic state data. 
The state data should change as often as its real world counterpart does.

Those static and dynamic types of data are mapped in the Ditto model to "attributes" (for static metadata), "features" 
(for dynamic state data) and "definition" (to link a model the thing follows, 
e.g. an [Eclipse Vorto](https://www.eclipse.org/vorto/) "information model").
A JSON representation of some metadata and state data could for example look like this:

```json
{
  "definition": "digitaltwin:DigitaltwinExample:1.0.0",
  "attributes": {
    "manufacturer": "ACME",
    "VIN": "0815666337"
  },
  "features": {
    "transmission": {
      "properties": {
        "automatic": true,
        "mode": "eco",
        "cur_speed": 90,
        "gear": 5
      }
    },
    "environment-scanner": {
      "properties": {
        "temperature": 20.8,
        "humidity": 73,
        "barometricPressure": 970.7,
        "location": {
          "longitude": 47.682170,
          "latitude": 9.386372
        },
        "altitude": 399
      }
    }
  }
}
```

Background: Ditto only knows about "attributes", "features" and the "definition".

Inside "attributes" (the metadata) we can add as much JSON keys as we like with any JSON value we need.

Inside "features" (the state data) we can add as much features as we like - but each feature needs to have 
a "properties" JSON object. Inside that JSON object we can add as much JSON keys as we like with any JSON value we need. 

Inside "definition" we can add one JSON string value. 

## Creating your first Thing

We create a Thing for the example from above by using [cURL](https://github.com/curl/curl). Basic authentication will use the credentials of a user "ditto". 
Those credentials have been created by default in the [nginx](https://github.com/nginx/nginx) started via "docker". 
(See [ditto/deployment/docker/README.md](https://github.com/eclipse-ditto/ditto/blob/master/deployment/docker/README.md))

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d '{
   "definition": "digitaltwin:DigitaltwinExample:1.0.0",
   "attributes": {
     "manufacturer": "ACME",
     "VIN": "0815666337"
   },
   "features": {
     "transmission": {
       "properties": {
         "automatic": true, 
         "mode": "eco",
         "cur_speed": 90, 
         "gear": 5
       }
     },
     "environment-scanner": {
       "properties": {
         "temperature": 20.8,
         "humidity": 73,
         "barometricPressure": 970.7,
         "location": {
           "longitude": 47.682170,
           "latitude": 9.386372
         },
         "altitude": 399
       }
     }
   }
 }' 'http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-car'
```

The result is a digital twin in Thing notation. The Thing ID is `org.eclipse.ditto:fancy-car`. 
An ID must always contain a namespace before the `:`. That way Things are easier to organize.

## Querying an existing Thing

By creating the digital twin as a Thing with the specified JSON format, Ditto implicitly provides an API for
our Thing.

For Things we know the ID of, we can simply query them by their ID:

```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-car'

# if you have python installed, that's how to get a prettier response:
curl -u ditto:ditto -X GET 'http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-car' | python -m json.tool
```

## Querying one specific state value

The created API for our Thing also provides HTTP endpoints for each attribute and feature property.

That way we can for example just retrieve the `cur_speed` of our fancy car:

```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-car/features/transmission/properties/cur_speed'
```

## Updating one specific state value

We can just as easy use the HTTP API to update one attribute or feature property, e.g. update the `cur_speed` to `77`:

```bash
curl -u ditto:ditto -X PUT -H 'Content-Type: application/json' -d '77' 'http://localhost:8080/api/2/things/org.eclipse.ditto:fancy-car/features/transmission/properties/cur_speed'
```

## Searching for all Things

When we lost the overview which Things we have already created, we can use the `search` HTTP endpoint,
e.g. searching all Things with the same `manufacturer` named `"ACME"`:

```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/2/search/things?filter=eq(attributes/manufacturer,"ACME")'
```
