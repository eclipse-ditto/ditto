## things:feature.definition.identifier.invalid

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:feature.definition.identifier.invalid",
    "message": "Feature Definition Identifier <foo:bar> is invalid!",
    "description": "An Identifier string is expected to have the structure 'namespace:name:version'. Each segment must contain at least one char of [_a-zA-Z0-9\\-.]"
  },
  "status": 400
}
```
