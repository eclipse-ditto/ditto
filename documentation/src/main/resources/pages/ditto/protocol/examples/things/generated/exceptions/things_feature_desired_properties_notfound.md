## things:feature.desiredProperties.notfound

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 404,
    "error": "things:feature.desiredProperties.notfound",
    "message": "The desired properties of the Feature with ID 'accelerometer' on the Thing with ID 'org.eclipse.ditto:fancy-thing' do not exist or the requester had insufficient permissions to access it.",
    "description": "Check if the ID of the Thing and the Feature ID was correct and you have sufficient permissions."
  },
  "status": 404
}
```