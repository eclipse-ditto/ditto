## things:id.notsettable

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/errors",
  "headers": {
    "correlation-id": "<preserved-command-correlation-id>"
  },
  "path": "/",
  "value": {
    "status": 400,
    "error": "things:id.notsettable",
    "message": "The Thing ID in the request body is not equal to the Thing ID in the request URL.",
    "description": "Either delete the Thing ID from the request body or use the same Thing ID as in the request URL."
  },
  "status": 400
}
```
