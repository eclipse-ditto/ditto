## things:thing.notdeletable

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 403,
    "error": "things:thing.notdeletable",
    "message": "The Thing with ID 'org.eclipse.ditto:fancy-thing' could not be deleted as the requester had insufficient permissions ( WRITE on root resource is required).",
    "description": "Check if the ID of your requested Thing was correct and you have sufficient permissions."
  },
  "status": 403
}
```
