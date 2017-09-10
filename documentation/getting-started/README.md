## Eclipse Ditto :: Documentation :: Getting Started

After starting Ditto, we have an HTTP and WebSocket API for you Digital Twins at our hands.

### Example

Assume we want to create a Digital Twin for a car which contains both static "metadata" and dynamic "state data" which 
changes as its real world counterpart constantly does.

Those two variations are mapped in the Eclipse Ditto model to "attributes" (metadata) and "features" (state).
A JSON representation of some metadata and state data could for example look like this:
```json
{
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

Background: Eclipse Ditto only knows about "attributes" and "features".

Inside "attributes" (the metadata) we can add as much JSON keys as we like with any JSON value we need.

Insode "features" (the state data) we can add as much features as we like - but each feature needs to have a "properties" JSON object.
Inside that JSON object we can add as much JSON keys as we like with any JSON value we need. 

### Creating your first Thing

We create a Thing for the example from above by using cURL (uses the user "ditto" which is created by default in the started nginx: see "docker" folder):

```bash
curl -u ditto:ditto -X PUT -d '{
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
 }' 'http://localhost:8080/api/1/things/org.eclipse.ditto:fancy-car'
```

We just created a Digital Twin for a Thing with the ID `org.eclipse.ditto:fancy-car` (an ID must always contain a 
namespace before the `:`). That way Things are easier organizable.

### Querying an existing Thing

By creating the Digital Twin as a Thing with the specified JSON format, Eclipse Ditto implicitly provides an API for
our Thing.

For Things we know the ID, we can simply do an HTTP query with that ID:
```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/1/things/org.eclipse.ditto:fancy-car'

# if you have python installed, that's how to get a prettier response:
curl -u ditto:ditto -X GET 'http://localhost:8080/api/1/things/org.eclipse.ditto:fancy-car' | python -m json.tool
```

### Querying one specific state value

The created API for our Thing also provides HTTP endpoints for each attribute and feature property.

That way we can for example just retrieve the `cur_speed` of our fancy car:

```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/1/things/org.eclipse.ditto:fancy-car/features/transmission/properties/cur_speed'
```

### Updating one specific state value

We can just as easy use the HTTP API in order to update one attribute or feature property, e.g. update the `cur_speed` to `77`:
```bash
curl -u ditto:ditto -X PUT -d '77' 'http://localhost:8080/api/1/things/org.eclipse.ditto:fancy-car/features/transmission/properties/cur_speed'
```

### Searching for all Things

When we lost the overview which Things we already created, we can use the `search` HTTP endpoint,
e.g. searching all Things with the same `manufacturer` named `"ACME"`:
```bash
curl -u ditto:ditto -X GET 'http://localhost:8080/api/1/search/things?filter=eq(attributes/manufacturer,"ACME")'
```
