## things:feature.definition.empty

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:feature.definition.empty",
    "message": "Feature Definition must not be empty!",
    "description": "A Feature Definition must contain at least one element. It can however also be set to null or deleted completely."
  },
  "status": 400
}
```
