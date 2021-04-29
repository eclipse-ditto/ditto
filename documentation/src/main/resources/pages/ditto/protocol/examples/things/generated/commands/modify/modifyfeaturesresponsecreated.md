## ModifyFeaturesResponse

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/features",
  "value": {
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
  },
  "status": 201
}
```
