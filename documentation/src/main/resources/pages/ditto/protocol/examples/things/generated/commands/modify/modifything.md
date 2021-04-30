## ModifyThing

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
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
          "x": 4,
          "y": 3,
          "z": 5,
          "unit": "g"
        }
      }
    }
  }
}
```
