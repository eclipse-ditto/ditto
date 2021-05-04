## things:policy.notallowed

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:policy.notallowed",
    "message": "The Thing with ID 'org.eclipse.ditto:fancy-thing' could not be modified as it contained an inline Policy",
    "description": "Once a Thing with inline Policy is created it can't be modified with another Policy. Use the Policy resources to modify the existing Policy."
  },
  "status": 400
}
```
