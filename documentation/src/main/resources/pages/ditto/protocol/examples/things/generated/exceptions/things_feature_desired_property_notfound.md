## things:feature.desiredProperty.notfound

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 404,
    "error": "things:feature.desiredProperty.notfound",
    "message": "The desired property with JSON Pointer '/x' of the Feature with ID 'accelerometer' on the Thing with ID 'org.eclipse.ditto:fancy-thing' does not exist or the requester had insufficient permissions to access it.",
    "description": "Check if the ID of the Thing, the Feature ID and the key of your requested desired property was correct and you have sufficient permissions."
  },
  "status": 404
}
```