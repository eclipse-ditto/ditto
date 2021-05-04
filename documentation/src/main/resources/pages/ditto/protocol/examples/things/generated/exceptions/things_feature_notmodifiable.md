## things:feature.notmodifiable

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 403,
    "error": "things:feature.notmodifiable",
    "message": "The Feature with ID 'accelerometer' on the Thing with ID 'org.eclipse.ditto:fancy-thing' could not be modified as the requester had insufficient permissions to modify it (WRITE is required).",
    "description": "Check if the ID of the Thing and the ID of your requested Feature was correct and you have sufficient permissions."
  },
  "status": 403
}
```
