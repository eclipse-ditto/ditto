## RetrieveThingsResponse

```json
{
  "topic": "org.eclipse.ditto/_/things/twin/commands/retrieve",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": [{
      "thingId": "org.eclipse.ditto:fancy-thing_53",
      "policyId": "org.eclipse.ditto:the_policy_id",
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
    }],
  "status": 200
}
```
