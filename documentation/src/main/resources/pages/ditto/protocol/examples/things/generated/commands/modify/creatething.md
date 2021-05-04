## CreateThing

Creates a new Thing with ID ``org.eclipse.ditto:fancy-thing_53`` that uses an existing Policy with ID ``org.eclipse.ditto:the_policy_id``.

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/create",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/",
  "value": {
    "thingId": "org.eclipse.ditto:fancy-thing_53",
    "policyId": "org.eclipse.ditto:the_policy_id",
    "definition": "org.eclipse.ditto:SomeModel:1.0.0",
    "attributes": {
      "location": {
        "latitude": 44.673856,
        "longitude": 8.261719
      }
    },
    "features": {
      "accelerometer": {
        "properties": {
          "x": 3.141,
          "y": 2.718,
          "z": 1,
          "unit": "g"
        },
        "desiredProperties": {
          "x": 4
        }
      }
    }
  }
}
```
