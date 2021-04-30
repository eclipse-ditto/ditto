## things:thing.notcreatable

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:thing.notcreatable",
    "message": "The Thing with ID 'org.eclipse.ditto:fancy-thing' could not be created as the Policy with ID 'org.eclipse.ditto:the_policy_id' is not existing.",
    "description": "Check if the ID of the Policy you created the Thing with is correct and that the Policy is existing."
  },
  "status": 400
}
```
